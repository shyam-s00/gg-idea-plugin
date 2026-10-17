package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.JBColor
import dev.gopherglide.ggplugin.execution.annotations.AnnotationLayout
import dev.gopherglide.ggplugin.execution.annotations.LabelSpan
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.abs
import kotlin.math.ceil

/**
 * RPS-over-time chart: solid actual line, dotted target line. The x-axis is fixed to the run's total
 * duration when known (matching the stage timeline below it), else it scrolls over recent points,
 * e.g. against a pre-v1.1.0 `gg` binary. Run annotations are drawn only on the fixed axis.
 */
class RpsChartComponent : JComponent() {
    internal data class Point(val elapsedSec: Double, val actualRps: Double, val targetRps: Int)

    private class Frame(
        val leftPad: Int, val topPad: Int, val chartWidth: Int, val chartHeight: Int,
        val yMax: Double, val fixedDuration: Double
    ) {
        fun y(value: Double) = topPad + (chartHeight * (1 - (value / yMax).coerceIn(0.0, 1.0))).toInt()
        fun xAt(elapsedSec: Double) = leftPad + (chartWidth * (elapsedSec / fixedDuration)).toInt().coerceIn(0, chartWidth)
    }

    private class BiasTick(val x: Int, val y: Int, val change: RunAnnotation.BiasChange)

    private class MarkDraw(val x: Int, val box: Rectangle?, val text: String, val mark: RunAnnotation.Mark)

    private val points = mutableListOf<Point>()
    private var annotations: List<RunAnnotation> = emptyList()
    private var yAxisMax = 0.0
    private var totalDurationSec = 0.0
    private val maxFallbackPoints = 60

    init {
        preferredSize = Dimension(300, 120)
        minimumSize = Dimension(150, 80)
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    /**
     * Call once, from the "started" heartbeat, with the known peak target RPS across all
     * stages (e.g. `stages?.maxOfOrNull { it.targetRps }`). Null/0 means "unknown" — the y-axis
     * then grows to fit the highest RPS observed so far instead, via [addDataPoint].
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
        yAxisMax = grownAxisMax(yAxisMax, maxOf(actualRps, targetRps.toDouble()))
        repaint()
    }

    fun setAnnotations(annotations: List<RunAnnotation>) {
        this.annotations = annotations.toList()
        repaint()
    }

    fun clear() {
        points.clear()
        annotations = emptyList()
        yAxisMax = 0.0
        totalDurationSec = 0.0
        repaint()
    }

    private fun frame(): Frame {
        val leftPad = 36
        val topPad = 6
        return Frame(
            leftPad, topPad,
            (width - leftPad - 6).coerceAtLeast(1), (height - topPad - 4).coerceAtLeast(1),
            yAxisMax.takeIf { it > 0.0 } ?: 1.0, totalDurationSec
        )
    }

    private fun biasTicks(frame: Frame): List<BiasTick> {
        if (frame.fixedDuration <= 0.0 || points.isEmpty()) return emptyList()
        return annotations.filterIsInstance<RunAnnotation.BiasChange>().mapNotNull { change ->
            val level = targetLevelAt(points, change.elapsedSeconds) ?: return@mapNotNull null
            BiasTick(frame.xAt(change.elapsedSeconds), frame.y(level.toDouble()), change)
        }
    }

    private fun markDraws(frame: Frame, fm: FontMetrics): List<MarkDraw> {
        if (frame.fixedDuration <= 0.0) return emptyList()
        val marks = annotations.filterIsInstance<RunAnnotation.Mark>().sortedBy { it.elapsedSeconds }
        val rows = when {
            frame.chartHeight >= 110 -> 3
            frame.chartHeight >= 70 -> 2
            else -> 0
        }
        val xs = marks.map { frame.xAt(it.elapsedSeconds) }
        val texts = marks.map { ellipsize("${it.label} · ${formatElapsed(it.elapsedSeconds)}", fm, MAX_FLAG_TEXT) }
        val widths = texts.map { fm.stringWidth(it) + 8 }
        val spans = marks.indices.map {
            LabelSpan(AnnotationLayout.labelLeft(xs[it], widths[it], frame.leftPad, frame.leftPad + frame.chartWidth), widths[it])
        }
        val rowOf = AnnotationLayout.assignRows(spans, rows)
        return marks.indices.map { i ->
            val box = rowOf[i]?.let { Rectangle(spans[i].left, frame.topPad + 2 + it * (FLAG_HEIGHT + 2), widths[i], FLAG_HEIGHT) }
            MarkDraw(xs[i], box, texts[i], marks[i])
        }
    }

    private fun ellipsize(text: String, fm: FontMetrics, maxWidth: Int): String {
        if (fm.stringWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && fm.stringWidth(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val frame = frame()
        biasTicks(frame).lastOrNull { abs(event.x - it.x) <= TICK_RADIUS + 2 && abs(event.y - it.y) <= TICK_RADIUS + 2 }?.let {
            val change = it.change
            return "Bias ${formatSigned(change.amount)} → cumulative ${formatSigned(change.cumulative)} at ${formatElapsed(change.elapsedSeconds)}"
        }
        val draw = markDraws(frame, getFontMetrics(font))
            .lastOrNull { abs(event.x - it.x) <= 4 || it.box?.contains(event.point) == true } ?: return null
        return "${draw.mark.label} — at ${formatElapsed(draw.mark.elapsedSeconds)}"
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val gridColor = JBColor(0xE0E0E0, 0x3C3F41)
        val axisColor = JBColor(0xA0A0A0, 0x787878)
        val actualColor = JBColor(0x2E7D32, 0x66BB6A)
        val targetColor = JBColor(0x9E9E9E, 0x9E9E9E)

        val frame = frame()
        val fm = g2.fontMetrics

        for (fraction in listOf(0.0, 0.5, 1.0)) {
            val y = frame.topPad + (frame.chartHeight * (1 - fraction)).toInt()
            g2.color = gridColor
            g2.drawLine(frame.leftPad, y, frame.leftPad + frame.chartWidth, y)
            val label = (frame.yMax * fraction).toInt().toString()
            g2.color = axisColor
            g2.drawString(label, frame.leftPad - fm.stringWidth(label) - 4, (y + fm.ascent / 2).coerceIn(fm.ascent, height))
        }

        if (points.size < 2) return

        fun xFor(point: Point, index: Int) = if (frame.fixedDuration > 0.0) {
            frame.xAt(point.elapsedSec)
        } else {
            frame.leftPad + (frame.chartWidth * index / (points.size - 1).coerceAtLeast(1))
        }

        val marks = markDraws(frame, fm)
        paintMarkLines(g2, frame, marks)

        g2.color = targetColor
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(4f, 4f), 0f)
        for (i in 1 until points.size) {
            g2.drawLine(
                xFor(points[i - 1], i - 1), frame.y(points[i - 1].targetRps.toDouble()),
                xFor(points[i], i), frame.y(points[i].targetRps.toDouble())
            )
        }

        g2.color = actualColor
        g2.stroke = BasicStroke(2f)
        for (i in 1 until points.size) {
            g2.drawLine(
                xFor(points[i - 1], i - 1), frame.y(points[i - 1].actualRps),
                xFor(points[i], i), frame.y(points[i].actualRps)
            )
        }

        paintMarkFlags(g2, fm, marks)
        paintBiasTicks(g2, frame)
    }

    private fun paintMarkLines(g2: Graphics2D, frame: Frame, marks: List<MarkDraw>) {
        g2.color = MARK_COLOR
        g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(4f, 3f), 0f)
        marks.forEach { g2.drawLine(it.x, frame.topPad, it.x, frame.topPad + frame.chartHeight) }
    }

    private fun paintMarkFlags(g2: Graphics2D, fm: FontMetrics, marks: List<MarkDraw>) {
        g2.stroke = BasicStroke(1f)
        for (draw in marks) {
            val box = draw.box ?: continue
            g2.color = MARK_FILL
            g2.fillRoundRect(box.x, box.y, box.width, box.height, 6, 6)
            g2.color = MARK_COLOR
            g2.drawRoundRect(box.x, box.y, box.width, box.height, 6, 6)
            g2.color = MARK_TEXT
            g2.drawString(draw.text, box.x + 4, box.y + (box.height + fm.ascent) / 2 - 2)
        }
    }

    private fun paintBiasTicks(g2: Graphics2D, frame: Frame) {
        val tickColor = JBColor(0x1565C0, 0x6AB7FF)
        val fm = g2.fontMetrics
        g2.stroke = BasicStroke(1f)
        for (tick in biasTicks(frame)) {
            g2.color = tickColor
            g2.fillPolygon(
                Polygon(
                    intArrayOf(tick.x, tick.x + TICK_RADIUS, tick.x, tick.x - TICK_RADIUS),
                    intArrayOf(tick.y - TICK_RADIUS, tick.y, tick.y + TICK_RADIUS, tick.y),
                    4
                )
            )
            val label = formatSigned(tick.change.amount)
            val labelY = if (tick.change.amount > 0) tick.y - TICK_RADIUS - 3 else tick.y + TICK_RADIUS + fm.ascent
            g2.drawString(label, tick.x - fm.stringWidth(label) / 2, labelY.coerceIn(fm.ascent, height - 2))
        }
    }

    internal companion object {
        private const val TICK_RADIUS = 5
        private const val FLAG_HEIGHT = 14
        private const val MAX_FLAG_TEXT = 190
        private val MARK_COLOR = JBColor(0xB8860B, 0xE8B341)
        private val MARK_FILL = JBColor(Color(232, 179, 65, 50), Color(232, 179, 65, 50))
        private val MARK_TEXT = JBColor(0x7A5A06, 0xF0CB74)

        /** Returns [current] widened to fit [value] with a little headroom; never shrinks. */
        fun grownAxisMax(current: Double, value: Double): Double {
            if (value <= current) return current
            val step = when {
                value <= 20.0 -> 5.0
                value <= 100.0 -> 10.0
                else -> 50.0
            }
            return ceil(value * 1.05 / step) * step
        }

        /** The target RPS at the first sample at or after [elapsedSec], else at the last sample. */
        fun targetLevelAt(points: List<Point>, elapsedSec: Double): Int? =
            (points.firstOrNull { it.elapsedSec >= elapsedSec } ?: points.lastOrNull())?.targetRps
    }
}
