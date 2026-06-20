package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class DiffCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (!isSelected) {
            val severity = (value as? DiffCell)?.severity
            foreground = when (severity) {
                DiffSeverity.REGRESSION -> JBColor(0xC62828, 0xE57373)
                DiffSeverity.WARNING -> JBColor(0xB8860B, 0xFFB74D)
                DiffSeverity.IMPROVEMENT -> JBColor(0x2E7D32, 0x66BB6A)
                DiffSeverity.ADDED -> JBColor(0x1565C0, 0x64B5F6)
                DiffSeverity.REMOVED -> JBColor.GRAY
                else -> table?.foreground ?: foreground
            }
        }
        return component
    }
}
