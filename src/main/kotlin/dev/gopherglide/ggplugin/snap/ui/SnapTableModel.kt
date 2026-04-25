package dev.gopherglide.ggplugin.snap.ui

import dev.gopherglide.ggplugin.snap.SnapModel
import javax.swing.table.AbstractTableModel

class SnapTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("ID / Tag", "Date", "Total Requests", "Peak RPS")
    private var snaps: List<SnapModel> = emptyList()

    fun setSnaps(newSnaps: List<SnapModel>) {
        this.snaps = newSnaps
        fireTableDataChanged()
    }

    fun getSnapAt(row: Int): SnapModel? {
        if (row in snaps.indices) {
            return snaps[row]
        }
        return null
    }

    override fun getRowCount(): Int = snaps.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val snap = snaps[rowIndex]
        val meta = snap.meta
        val tag = meta?.tag ?: ""
        return when (columnIndex) {
            0 -> if (tag.isNotBlank()) "${snap.internalIndex} - $tag" else "${snap.internalIndex} - (untagged)"
            1 -> meta?.startTime ?: ""
            2 -> meta?.totalRequests ?: 0
            3 -> meta?.peakRps ?: 0.0
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}
