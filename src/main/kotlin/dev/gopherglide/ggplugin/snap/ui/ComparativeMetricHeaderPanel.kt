package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

data class ComparativeMetric(val caption: String, val baseline: String, val target: String)

/**
 * Side-by-side Baseline/Target metadata as a real (small, non-scrolling)
 * [JBTable] rather than a hand-laid-out grid — resizable columns and a real
 * [javax.swing.table.JTableHeader] come for free, same lesson as the schema
 * tree/table in Milestone 2.
 */
class ComparativeMetricHeaderPanel(metrics: List<ComparativeMetric>) : JPanel(BorderLayout()) {

    init {
        val model = object : AbstractTableModel() {
            private val columns = arrayOf("", "Baseline", "Target")
            override fun getRowCount(): Int = metrics.size
            override fun getColumnCount(): Int = columns.size
            override fun getColumnName(column: Int): String = columns[column]
            override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
                0 -> metrics[rowIndex].caption
                1 -> metrics[rowIndex].baseline
                2 -> metrics[rowIndex].target
                else -> ""
            }
        }

        val table = JBTable(model)
        table.rowSelectionAllowed = false
        table.tableHeader.reorderingAllowed = false
        table.columnModel.getColumn(0).cellRenderer = CaptionCellRenderer()

        add(table.tableHeader, BorderLayout.NORTH)
        add(table, BorderLayout.CENTER)
        border = JBUI.Borders.emptyBottom(4)
    }

    private class CaptionCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            font = font.deriveFont(Font.BOLD)
            return component
        }
    }
}
