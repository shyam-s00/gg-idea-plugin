package dev.gopherglide.ggplugin.snap.ui

import dev.gopherglide.ggplugin.snap.SnapModel
import javax.swing.table.AbstractTableModel
import kotlin.math.abs

private const val LATENCY_REGRESSION_THRESHOLD = 0.20
private const val ERROR_RATE_DELTA_THRESHOLD = 0.05
private const val PAYLOAD_SIZE_DELTA_THRESHOLD = 0.50

class DiffEndpointTableModel(snapA: SnapModel, snapB: SnapModel) : AbstractTableModel() {
    private val columns = arrayOf("Endpoint", "Requests (A → B)", "Error Rate", "P50", "P95", "P99", "Payload Avg")
    private val rows: List<Row> = buildRows(snapA, snapB)

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 1) String::class.java else DiffCell::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.endpointCell
            1 -> row.requestsText
            2 -> row.errorRateCell
            3 -> row.p50Cell
            4 -> row.p95Cell
            5 -> row.p99Cell
            6 -> row.payloadAvgCell
            else -> ""
        }
    }

    private data class Row(
        val endpointCell: DiffCell,
        val requestsText: String,
        val errorRateCell: DiffCell,
        val p50Cell: DiffCell,
        val p95Cell: DiffCell,
        val p99Cell: DiffCell,
        val payloadAvgCell: DiffCell
    )

    companion object {
        private fun buildRows(snapA: SnapModel, snapB: SnapModel): List<Row> {
            val byIdA = snapA.endpoints.associateBy { it.id }
            val byIdB = snapB.endpoints.associateBy { it.id }
            val ids = (byIdA.keys + byIdB.keys).toSortedSet()

            return ids.map { id ->
                val a = byIdA[id]
                val b = byIdB[id]
                when {
                    a == null && b != null -> Row(
                        endpointCell = DiffCell("$id (added)", DiffSeverity.ADDED),
                        requestsText = "— → ${b.requestCount}",
                        errorRateCell = DiffCell("—", DiffSeverity.ADDED),
                        p50Cell = DiffCell("—", DiffSeverity.ADDED),
                        p95Cell = DiffCell("—", DiffSeverity.ADDED),
                        p99Cell = DiffCell("—", DiffSeverity.ADDED),
                        payloadAvgCell = DiffCell("—", DiffSeverity.ADDED)
                    )
                    b == null && a != null -> Row(
                        endpointCell = DiffCell("$id (removed)", DiffSeverity.REMOVED),
                        requestsText = "${a.requestCount} → —",
                        errorRateCell = DiffCell("—", DiffSeverity.REMOVED),
                        p50Cell = DiffCell("—", DiffSeverity.REMOVED),
                        p95Cell = DiffCell("—", DiffSeverity.REMOVED),
                        p99Cell = DiffCell("—", DiffSeverity.REMOVED),
                        payloadAvgCell = DiffCell("—", DiffSeverity.REMOVED)
                    )
                    a != null && b != null -> Row(
                        endpointCell = DiffCell(id),
                        requestsText = "${a.requestCount} → ${b.requestCount}",
                        errorRateCell = errorRateCell(a.errorRate, b.errorRate),
                        p50Cell = latencyCell(a.latency.p50, b.latency.p50),
                        p95Cell = latencyCell(a.latency.p95, b.latency.p95),
                        p99Cell = latencyCell(a.latency.p99, b.latency.p99),
                        payloadAvgCell = payloadCell(a.payloadSize.avg, b.payloadSize.avg)
                    )
                    else -> error("unreachable: endpoint id present in neither snapshot")
                }
            }
        }

        private fun pctDelta(before: Double, after: Double): Double =
            if (before == 0.0) (if (after == 0.0) 0.0 else Double.POSITIVE_INFINITY) else (after - before) / before

        private fun deltaText(delta: Double): String = if (delta.isFinite()) "%+.0f%%".format(delta * 100) else "+∞"

        private fun latencyCell(before: Double, after: Double): DiffCell {
            val delta = pctDelta(before, after)
            val severity = when {
                delta >= LATENCY_REGRESSION_THRESHOLD -> DiffSeverity.REGRESSION
                delta <= -LATENCY_REGRESSION_THRESHOLD -> DiffSeverity.IMPROVEMENT
                else -> DiffSeverity.NEUTRAL
            }
            return DiffCell("%.1f → %.1f ms (%s)".format(before, after, deltaText(delta)), severity)
        }

        private fun errorRateCell(before: Double, after: Double): DiffCell {
            val delta = after - before
            val severity = when {
                delta >= ERROR_RATE_DELTA_THRESHOLD -> DiffSeverity.REGRESSION
                delta <= -ERROR_RATE_DELTA_THRESHOLD -> DiffSeverity.IMPROVEMENT
                else -> DiffSeverity.NEUTRAL
            }
            return DiffCell("%.2f%% → %.2f%% (%+.2fpp)".format(before * 100, after * 100, delta * 100), severity)
        }

        private fun payloadCell(before: Double, after: Double): DiffCell {
            val delta = pctDelta(before, after)
            val severity = if (delta.isFinite() && abs(delta) >= PAYLOAD_SIZE_DELTA_THRESHOLD) {
                DiffSeverity.WARNING
            } else {
                DiffSeverity.NEUTRAL
            }
            return DiffCell(
                "${EndpointTableModel.formatBytes(before)} → ${EndpointTableModel.formatBytes(after)} (${deltaText(delta)})",
                severity
            )
        }
    }
}
