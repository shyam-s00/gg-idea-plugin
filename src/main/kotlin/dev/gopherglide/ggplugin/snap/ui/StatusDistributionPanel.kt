package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val CONNECTION_ERRORS_LABEL = "Connection errors"
private const val CONNECTION_ERRORS_SHORT_LABEL = "ERR"

/**
 * Status-code distribution as stacked, colored bars sorted by share (fixed-height, top-anchored).
 * [errorRate] can exceed the 4xx/5xx buckets when requests fail without a status, so the gap is
 * shown as a synthetic "Connection errors" bucket.
 */
class StatusDistributionPanel(statusDist: Map<String, Double>, errorRate: Double = 0.0) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4, 0)

        val httpErrorFraction = statusDist.entries
            .filter { (it.key.toIntOrNull() ?: 0) >= 400 }
            .sumOf { it.value }
        val connectionErrorFraction = (errorRate - httpErrorFraction).coerceAtLeast(0.0)

        val entries = statusDist.entries.map { it.key to it.value } +
            if (connectionErrorFraction > 0.001) listOf(CONNECTION_ERRORS_LABEL to connectionErrorFraction) else emptyList()

        if (entries.isEmpty()) {
            add(JBLabel("No status codes recorded.").apply { foreground = JBColor.GRAY })
        } else {
            entries.sortedByDescending { it.second }.forEach { (status, fraction) ->
                add(row(status, fraction))
                add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
            }
        }

        if (connectionErrorFraction > 0.001) {
            add(JBLabel("ERR — connection failures (timeouts, refused, DNS) with no HTTP status code").apply {
                font = font.deriveFont(font.size2D - 1f)
                foreground = JBColor.GRAY
                border = JBUI.Borders.emptyTop(4)
            })
        }

        add(javax.swing.Box.createVerticalGlue())
    }

    private fun row(status: String, fraction: Double): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(8), 0))
        val rowHeight = JBUI.scale(20)
        row.maximumSize = Dimension(Int.MAX_VALUE, rowHeight)
        row.preferredSize = Dimension(row.preferredSize.width, rowHeight)
        val displayLabel = if (status == CONNECTION_ERRORS_LABEL) CONNECTION_ERRORS_SHORT_LABEL else status
        val statusLabel = JBLabel(displayLabel).apply {
            preferredSize = Dimension(JBUI.scale(40), preferredSize.height)
            if (status == CONNECTION_ERRORS_LABEL) toolTipText = CONNECTION_ERRORS_LABEL
        }
        val pctLabel = JBLabel("%.1f%%".format(fraction * 100), SwingConstants.RIGHT).apply {
            preferredSize = Dimension(JBUI.scale(52), preferredSize.height)
        }
        row.add(statusLabel, BorderLayout.WEST)
        row.add(Bar(fraction, colorFor(status)), BorderLayout.CENTER)
        row.add(pctLabel, BorderLayout.EAST)
        return row
    }

    private class Bar(private val fraction: Double, private val color: JBColor) : JPanel() {
        init {
            preferredSize = Dimension(JBUI.scale(120), JBUI.scale(16))
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor(0xE0E0E0, 0x3C3F41)
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(4), JBUI.scale(4))
            val filledWidth = (width * fraction.coerceIn(0.0, 1.0)).toInt()
            if (filledWidth > 0) {
                g2.color = color
                g2.fillRoundRect(0, 0, filledWidth, height, JBUI.scale(4), JBUI.scale(4))
            }
        }
    }

    companion object {
        fun colorFor(status: String): JBColor = when {
            status == CONNECTION_ERRORS_LABEL -> JBColor(0xC62828, 0xE57373)
            status.firstOrNull() == '2' -> JBColor(0x2E7D32, 0x66BB6A)
            status.firstOrNull() == '3' -> JBColor(0x1565C0, 0x64B5F6)
            status.firstOrNull() == '4' -> JBColor(0xB8860B, 0xFFB74D)
            status.firstOrNull() == '5' -> JBColor(0xC62828, 0xE57373)
            else -> JBColor.GRAY
        }
    }
}
