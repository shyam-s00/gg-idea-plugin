package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.snap.ManualChangeRow
import java.awt.Component
import java.awt.Font
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/** The recorded marks and bias changes, one row per event in time order. */
class ManualChangesTable(events: List<RunAnnotation>) : JBTable(Model(events.map(ManualChangeRow::from))) {
    private class Model(val rows: List<ManualChangeRow>) : AbstractTableModel() {
        override fun getRowCount() = rows.size
        override fun getColumnCount() = COLUMNS.size
        override fun getColumnName(column: Int) = COLUMNS[column]
        override fun isCellEditable(row: Int, column: Int) = false
        override fun getValueAt(row: Int, column: Int): Any = rows[row].let {
            when (column) {
                TIME -> it.time
                KIND -> it.kind
                DETAIL -> it.detail
                else -> it.total
            }
        }
    }

    init {
        tableHeader = null
        setShowGrid(false)
        intercellSpacing = java.awt.Dimension(0, 0)
        rowHeight = JBUI.scale(ROW_HEIGHT)
        autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setDefaultRenderer(Any::class.java, TextRenderer())
        columnModel.getColumn(TIME).apply { preferredWidth = JBUI.scale(52); maxWidth = JBUI.scale(64) }
        columnModel.getColumn(KIND).apply { preferredWidth = JBUI.scale(66); maxWidth = JBUI.scale(76); cellRenderer = ChipRenderer() }
        columnModel.getColumn(DETAIL).preferredWidth = JBUI.scale(260)
        columnModel.getColumn(TOTAL).apply { preferredWidth = JBUI.scale(90); maxWidth = JBUI.scale(120) }
    }

    override fun getAccessibleContext() = super.getAccessibleContext()?.also { it.accessibleName = "Marks and bias changes" }

    private class TextRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(table: JTable, value: Any?, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
            super.getTableCellRendererComponent(table, value, selected, false, row, column)
            border = JBUI.Borders.empty(0, 4)
            font = when (column) {
                TIME -> Font(Font.MONOSPACED, Font.PLAIN, table.font.size)
                TOTAL -> table.font.deriveFont(table.font.size2D - 1f)
                else -> table.font
            }
            if (!selected) foreground = if (column == TIME || column == TOTAL) JBColor.GRAY else table.foreground
            return this
        }
    }

    /** Paints the type as a small bordered tag, drawn directly so the cell needs no layout. */
    private class ChipRenderer : JComponent(), TableCellRenderer {
        private var chip = BIAS_CHIP
        private var rowBackground: Color = Color.WHITE

        override fun getTableCellRendererComponent(table: JTable, value: Any?, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
            chip = if (value == ManualChangeRow.Kind.MARK) MARK_CHIP else BIAS_CHIP
            rowBackground = if (selected) table.selectionBackground else table.background
            font = table.font.deriveFont(Font.BOLD, table.font.size2D - 2f)
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.color = rowBackground
            g2.fillRect(0, 0, width, height)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = font
            val fm = g2.fontMetrics
            val pad = JBUI.scale(5)
            val boxWidth = fm.stringWidth(chip.text) + 2 * pad
            val boxHeight = fm.height + JBUI.scale(2)
            val x = JBUI.scale(4)
            val y = (height - boxHeight) / 2
            g2.color = chip.bg
            g2.fillRoundRect(x, y, boxWidth, boxHeight, 6, 6)
            g2.color = chip.line
            g2.drawRoundRect(x, y, boxWidth - 1, boxHeight - 1, 6, 6)
            g2.color = chip.fg
            g2.drawString(chip.text, x + pad, y + (boxHeight - fm.height) / 2 + fm.ascent)
        }
    }

    private data class Chip(val text: String, val fg: JBColor, val bg: JBColor, val line: JBColor)

    private companion object {
        const val ROW_HEIGHT = 22
        const val TIME = 0
        const val KIND = 1
        const val DETAIL = 2
        const val TOTAL = 3
        val COLUMNS = listOf("Time", "Type", "Event", "Bias")
        val MARK_CHIP = Chip("MARK", JBColor(0x7A5A06, 0xF0CB74), JBColor(0xFFF3D6, 0x3A3426), JBColor(0xE8B341, 0x8A6E2F))
        val BIAS_CHIP = Chip("BIAS", JBColor(0x1F5FA8, 0x9ACBFA), JBColor(0xE3F0FF, 0x263646), JBColor(0x8DBBE8, 0x3F6C96))
    }
}
