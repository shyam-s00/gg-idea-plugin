package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel
import javax.swing.SwingConstants

data class Metric(val caption: String, val value: String, val tooltip: String = value)

/**
 * Header strip of stacked "caption above value" metric cells with hairline
 * separators (no card backgrounds — see the flat-row layout chosen over
 * hybrid cards for maintenance/resize).
 *
 * Uses GridBagLayout, not GridLayout: GridLayout forces every cell to the
 * width of the single widest cell in the whole grid, so one long value (e.g.
 * a full timestamp range) drags every other column out with it and pins the
 * dialog's minimum width huge. GridBagLayout sizes each column from only the
 * cells in that column, and `weightx = 1.0` still lets columns share extra
 * width evenly when the dialog is wide.
 */
class MetricHeaderPanel(metrics: List<Metric>, columns: Int = 3) : JPanel(BorderLayout()) {

    init {
        val grid = JPanel(GridBagLayout())
        metrics.forEachIndexed { index, metric ->
            val row = index / columns
            val col = index % columns
            val constraints = GridBagConstraints().apply {
                gridx = col
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(0, 0, 0, 0)
            }
            grid.add(cell(metric, leftHairline = col != 0), constraints)
        }
        add(grid, BorderLayout.CENTER)
        border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    }

    private fun cell(metric: Metric, leftHairline: Boolean): JPanel {
        val cell = JPanel(BorderLayout(0, JBUI.scale(3)))
        cell.border = if (leftHairline) {
            JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0),
                JBUI.Borders.empty(8, 12)
            )
        } else {
            JBUI.Borders.empty(8, 12)
        }

        val captionLabel = JBLabel(metric.caption.uppercase(), SwingConstants.LEFT).apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 2f)
            foreground = JBColor.GRAY
        }
        val valueLabel = JBLabel(metric.value, SwingConstants.LEFT).apply {
            font = font.deriveFont(Font.BOLD)
            toolTipText = metric.tooltip
        }

        cell.add(captionLabel, BorderLayout.NORTH)
        cell.add(valueLabel, BorderLayout.CENTER)
        return cell
    }
}
