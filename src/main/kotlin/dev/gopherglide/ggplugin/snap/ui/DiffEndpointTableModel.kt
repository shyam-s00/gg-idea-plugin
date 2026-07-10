package dev.gopherglide.ggplugin.snap.ui

import dev.gopherglide.ggplugin.snap.EndpointSnap
import dev.gopherglide.ggplugin.snap.SnapModel
import javax.swing.table.AbstractTableModel
import kotlin.math.abs

private const val LATENCY_REGRESSION_THRESHOLD = 0.20
private const val ERROR_RATE_DELTA_THRESHOLD = 0.05
private const val PAYLOAD_SIZE_DELTA_THRESHOLD = 0.50

class DiffEndpointTableModel(snapA: SnapModel, snapB: SnapModel) : AbstractTableModel() {
    private val columns = arrayOf("Endpoint", "Requests", "Error Rate", "P50", "P95", "P99", "Payload Avg", "Payload Max")
    private val rows: List<Row> = buildRows(snapA, snapB)

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    override fun getColumnClass(columnIndex: Int): Class<*> = DiffCell::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.endpointCell
            1 -> row.requestsCell
            2 -> row.errorRateCell
            3 -> row.p50Cell
            4 -> row.p95Cell
            5 -> row.p99Cell
            6 -> row.payloadAvgCell
            7 -> row.payloadMaxCell
            else -> ""
        }
    }

    /** Raw endpoints backing the row at [rowIndex], for driving a detail pane on selection. */
    fun getEndpointsAt(rowIndex: Int): Pair<EndpointSnap?, EndpointSnap?>? =
        rows.getOrNull(rowIndex)?.let { it.endpointA to it.endpointB }

    private data class Row(
        val endpointCell: DiffCell,
        val requestsCell: DiffCell,
        val errorRateCell: DiffCell,
        val p50Cell: DiffCell,
        val p95Cell: DiffCell,
        val p99Cell: DiffCell,
        val payloadAvgCell: DiffCell,
        val payloadMaxCell: DiffCell,
        val endpointA: EndpointSnap?,
        val endpointB: EndpointSnap?
    )

    companion object {
        private fun buildRows(snapA: SnapModel, snapB: SnapModel): List<Row> {
            val byIdA = snapA.endpoints.associateBy { it.id }
            val byIdB = snapB.endpoints.associateBy { it.id }
            val ids = (byIdA.keys + byIdB.keys).toSortedSet()

            return ids.map { id ->
                val a = byIdA[id]
                val b = byIdB[id]
                val endpointCell = when {
                    a == null -> DiffCell("$id  NEW", DiffSeverity.ADDED)
                    b == null -> DiffCell("$id  GONE", DiffSeverity.REMOVED)
                    else -> DiffCell(id)
                }
                Row(
                    endpointCell = endpointCell,
                    requestsCell = requestsCell(a?.requestCount, b?.requestCount),
                    errorRateCell = errorRateCell(a?.errorRate, b?.errorRate),
                    p50Cell = latencyCell(a?.latency?.p50, b?.latency?.p50),
                    p95Cell = latencyCell(a?.latency?.p95, b?.latency?.p95),
                    p99Cell = latencyCell(a?.latency?.p99, b?.latency?.p99),
                    payloadAvgCell = payloadCell(a?.payloadSize?.avg, b?.payloadSize?.avg),
                    payloadMaxCell = payloadCell(a?.payloadSize?.max, b?.payloadSize?.max),
                    endpointA = a,
                    endpointB = b
                )
            }
        }

        private fun requestsCell(before: Long?, after: Long?): DiffCell = when {
            before == null && after != null -> DiffCell("%,d  NEW".format(after), DiffSeverity.ADDED)
            after == null && before != null -> DiffCell("%,d  GONE".format(before), DiffSeverity.REMOVED)
            before != null && after != null -> {
                val diff = after - before
                val suffix = if (diff == 0L) "" else " (%+d)".format(diff)
                DiffCell("%,d".format(after) + suffix, DiffSeverity.NEUTRAL)
            }
            else -> DiffCell("—")
        }

        private fun latencyCell(before: Double?, after: Double?): DiffCell = when {
            before == null && after != null -> DiffCell("%.1fms  NEW".format(after), DiffSeverity.ADDED)
            after == null && before != null -> DiffCell("%.1fms  GONE".format(before), DiffSeverity.REMOVED)
            before != null && after != null -> {
                val delta = DiffFormat.pctDelta(before, after)
                val severity = when {
                    delta >= LATENCY_REGRESSION_THRESHOLD -> DiffSeverity.REGRESSION
                    delta <= -LATENCY_REGRESSION_THRESHOLD -> DiffSeverity.IMPROVEMENT
                    else -> DiffSeverity.NEUTRAL
                }
                DiffCell("%.1fms".format(after) + DiffFormat.deltaSuffix(delta, "%"), severity)
            }
            else -> DiffCell("—")
        }

        private fun errorRateCell(before: Double?, after: Double?): DiffCell = when {
            before == null && after != null -> DiffCell("%.2f%%  NEW".format(after * 100), DiffSeverity.ADDED)
            after == null && before != null -> DiffCell("%.2f%%  GONE".format(before * 100), DiffSeverity.REMOVED)
            before != null && after != null -> {
                val delta = after - before
                val severity = when {
                    delta >= ERROR_RATE_DELTA_THRESHOLD -> DiffSeverity.REGRESSION
                    delta <= -ERROR_RATE_DELTA_THRESHOLD -> DiffSeverity.IMPROVEMENT
                    else -> DiffSeverity.NEUTRAL
                }
                DiffCell("%.2f%%".format(after * 100) + DiffFormat.deltaSuffix(delta, "pp"), severity)
            }
            else -> DiffCell("—")
        }

        private fun payloadCell(before: Double?, after: Double?): DiffCell = when {
            before == null && after != null -> DiffCell("${EndpointTableModel.formatBytes(after)}  NEW", DiffSeverity.ADDED)
            after == null && before != null -> DiffCell("${EndpointTableModel.formatBytes(before)}  GONE", DiffSeverity.REMOVED)
            before != null && after != null -> {
                val delta = DiffFormat.pctDelta(before, after)
                val severity = if (delta.isFinite() && abs(delta) >= PAYLOAD_SIZE_DELTA_THRESHOLD) {
                    DiffSeverity.WARNING
                } else {
                    DiffSeverity.NEUTRAL
                }
                DiffCell(EndpointTableModel.formatBytes(after) + DiffFormat.deltaSuffix(delta, "%"), severity)
            }
            else -> DiffCell("—")
        }
    }
}
