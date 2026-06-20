package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * RPS-over-time chart: solid line for actual RPS, dotted line for target RPS, with a labeled
 * y-axis. When the run's total duration is known (the `stages` array resolved on the "started"
 * heartbeat), the x-axis is fixed to that duration and each point is drawn at its real
 * elapsed-time position — the same time model the stage timeline below it uses, so the two stay
 * in sync as the run progresses. Falls back to a scrolling window of recent points (the original
 * behavior) when the duration isn't known, e.g. against a pre-v1.1.0 `gg` binary.
 */
class RpsChartComponent : JComponent() {
    private data class Point(val elapsedSec: Double, val actualRps: Double, val targetRps: Int)

    private val points = mutableListOf<Point>()
    private var yAxisMax = 0.0
    private var totalDurationSec = 0.0
    private val maxFallbackPoints = 60

    init {
        preferredSize = Dimension(300, 120)
        minimumSize = Dimension(150, 80)
    }

    /**
     * Call once, from the "started" heartbeat, with the known peak target RPS across all
     * stages (e.g. `stages?.maxOfOrNull { it.targetRps }`). Null/0 means "unknown" — the y-axis
     * then auto-scales to the highest RPS observed so far instead, via [addDataPoint].
     */
    fun setKnownPeakRps(peakRps: Int?) {
        yAxisMax = (peakRps ?: 0).toDouble()
        repaint()
    }

    /**
     * Call once, from the "started" heartbeat, with the total resolved stage duration
     * (e.g. `stages?.sumOf { it.durationSeconds }`). Null/0 means "unknown" — the chart then
     * falls back to a scrolling window of the most recent points instead of a fixed axis.
     */
    fun setTotalDuration(totalSec: Double?) {
        totalDurationSec = totalSec?.takeIf { it > 0.0 } ?: 0.0
        repaint()
    }

    fun addDataPoint(elapsedSec: Double, actualRps: Double, targetRps: Int) {
        points.add(Point(elapsedSec, actualRps, targetRps))
        if (totalDurationSec <= 0.0 && points.size > maxFallbackPoints) points.removeAt(0)
        if (yAxisMax <= 0.0) {
            yAxisMax = maxOf(yAxisMax, actualRps, targetRps.toDouble())
        }
        repaint()
    }

    fun clear() {
        points.clear()
        yAxisMax = 0.0
        totalDurationSec = 0.0
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val gridColor = JBColor(0xE0E0E0, 0x3C3F41)
        val axisColor = JBColor(0xA0A0A0, 0x787878)
        val actualColor = JBColor(0x2E7D32, 0x66BB6A)
        val targetColor = JBColor(0x9E9E9E, 0x9E9E9E)

        val leftPad = 36
        val bottomPad = 4
        val topPad = 6
        val rightPad = 6
        val chartWidth = (width - leftPad - rightPad).coerceAtLeast(1)
        val chartHeight = (height - topPad - bottomPad).coerceAtLeast(1)
        val yMax = yAxisMax.takeIf { it > 0.0 } ?: 1.0
        val fm = g2.fontMetrics

        for (fraction in listOf(0.0, 0.5, 1.0)) {
            val y = topPad + (chartHeight * (1 - fraction)).toInt()
            g2.color = gridColor
            g2.drawLine(leftPad, y, leftPad + chartWidth, y)
            val label = (yMax * fraction).toInt().toString()
            g2.color = axisColor
            g2.drawString(label, leftPad - fm.stringWidth(label) - 4, (y + fm.ascent / 2).coerceIn(fm.ascent, height))
        }

        if (points.size < 2) return

        val fixedDuration = totalDurationSec
        fun yFor(value: Double) = topPad + (chartHeight * (1 - (value / yMax).coerceIn(0.0, 1.0))).toInt()
        fun xFor(point: Point, index: Int) = if (fixedDuration > 0.0) {
            leftPad + (chartWidth * (point.elapsedSec / fixedDuration)).toInt().coerceIn(0, chartWidth)
        } else {
            leftPad + (chartWidth * index / (points.size - 1).coerceAtLeast(1))
        }

        g2.color = targetColor
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(4f, 4f), 0f)
        for (i in 1 until points.size) {
            g2.drawLine(
                xFor(points[i - 1], i - 1), yFor(points[i - 1].targetRps.toDouble()),
                xFor(points[i], i), yFor(points[i].targetRps.toDouble())
            )
        }

        g2.color = actualColor
        g2.stroke = BasicStroke(2f)
        for (i in 1 until points.size) {
            g2.drawLine(
                xFor(points[i - 1], i - 1), yFor(points[i - 1].actualRps),
                xFor(points[i], i), yFor(points[i].actualRps)
            )
        }
    }
}
