package dev.gopherglide.ggplugin.snap.ui

import dev.gopherglide.ggplugin.snap.EndpointSnap
import javax.swing.table.AbstractTableModel

class EndpointTableModel(private val endpoints: List<EndpointSnap>) : AbstractTableModel() {
    private val columns = arrayOf("Endpoint", "Requests", "Error Rate", "P50", "P95", "P99", "Max", "Payload Avg", "Payload P95")

    override fun getRowCount(): Int = endpoints.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val endpoint = endpoints[rowIndex]
        return when (columnIndex) {
            0 -> endpoint.id
            1 -> endpoint.requestCount
            2 -> "%.2f%%".format(endpoint.errorRate * 100)
            3 -> "%.1f ms".format(endpoint.latency.p50)
            4 -> "%.1f ms".format(endpoint.latency.p95)
            5 -> "%.1f ms".format(endpoint.latency.p99)
            6 -> "%.1f ms".format(endpoint.latency.max)
            7 -> formatBytes(endpoint.payloadSize.avg)
            8 -> formatBytes(endpoint.payloadSize.p95)
            else -> ""
        }
    }

    fun getEndpointAt(rowIndex: Int): EndpointSnap? = endpoints.getOrNull(rowIndex)

    companion object {
        fun formatBytes(bytes: Double): String =
            if (bytes >= 1024) "%.1f KB".format(bytes / 1024) else "%.0f B".format(bytes)
    }
}
