package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import dev.gopherglide.ggplugin.execution.annotations.AnnotationLayout
import dev.gopherglide.ggplugin.execution.annotations.LabelSpan
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import dev.gopherglide.ggplugin.snap.RunTimeline
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.abs

/** Marks as flags and bias as a step line along a run's length, read from a snapshot. */
class RunTimelineStrip(private val timeline: RunTimeline) : JComponent() {
    private class MarkDraw(val x: Int, val box: Rectangle?, val text: String, val mark: RunAnnotation.Mark)

    private class StepPoint(val x: Int, val y: Int, val change: RunAnnotation.BiasChange)

    private class Geometry(
        val left: Int, val right: Int, val flagsTop: Int, val biasTop: Int, val biasBottom: Int, val axisY: Int,
        val low: Int, val high: Int
    ) {
        fun x(seconds: Double, duration: Double) = left + ((right - left) * (seconds / duration).coerceIn(0.0, 1.0)).toInt()

        fun yBias(value: Int): Int {
            val span = (high - low).coerceAtLeast(1)
            return biasBottom - ((biasBottom - biasTop) * (value - low).toDouble() / span).toInt()
        }
    }

    init {
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    private val hasMarks = timeline.marks.isNotEmpty()
    private val hasBias = timeline.biasChanges.isNotEmpty()

    override fun getPreferredSize(): Dimension = Dimension(400, geometry(400, getFontMetrics(font)).axisY + getFontMetrics(font).height + 6)

    override fun getMinimumSize(): Dimension = Dimension(120, preferredSize.height)

    private fun geometry(width: Int, fm: FontMetrics): Geometry {
        val flagsTop = 2
        val flagsHeight = if (hasMarks) FLAG_ROWS * (FLAG_HEIGHT + 2) else 0
        val biasTop = flagsTop + flagsHeight + if (hasBias) 14 else 0
        val biasBottom = if (hasBias) biasTop + BIAS_HEIGHT else biasTop
        val levels = listOf(0) + timeline.biasChanges.map { it.cumulative }
        return Geometry(
            left = LEFT_PAD, right = (width - RIGHT_PAD).coerceAtLeast(LEFT_PAD + 1), flagsTop = flagsTop,
            biasTop = biasTop, biasBottom = biasBottom, axisY = biasBottom + 6,
            low = levels.min(), high = levels.max()
        )
    }

    private fun markDraws(g: Geometry, fm: FontMetrics): List<MarkDraw> {
        val marks = timeline.marks
        val xs = marks.map { g.x(it.elapsedSeconds, timeline.durationSeconds) }
        val texts = marks.map { ellipsize("${it.label} · ${formatElapsed(it.elapsedSeconds)}", fm) }
        val widths = texts.map { fm.stringWidth(it) + 8 }
        val spans = marks.indices.map { LabelSpan(AnnotationLayout.labelLeft(xs[it], widths[it], g.left, g.right), widths[it]) }
        val rowOf = AnnotationLayout.assignRows(spans, FLAG_ROWS)
        return marks.indices.map { i ->
            val box = rowOf[i]?.let { Rectangle(spans[i].left, g.flagsTop + it * (FLAG_HEIGHT + 2), widths[i], FLAG_HEIGHT) }
            MarkDraw(xs[i], box, texts[i], marks[i])
        }
    }

    private fun stepPoints(g: Geometry): List<StepPoint> =
        timeline.biasChanges.map { StepPoint(g.x(it.elapsedSeconds, timeline.durationSeconds), g.yBias(it.cumulative), it) }

    private fun ellipsize(text: String, fm: FontMetrics): String {
        if (fm.stringWidth(text) <= MAX_FLAG_TEXT) return text
        var end = text.length
        while (end > 1 && fm.stringWidth(text.substring(0, end) + "…") > MAX_FLAG_TEXT) end--
        return text.substring(0, end) + "…"
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val fm = getFontMetrics(font)
        val g = geometry(width, fm)
        stepPoints(g).lastOrNull { abs(event.x - it.x) <= 6 && abs(event.y - it.y) <= 6 }?.let {
            val c = it.change
            return "Bias ${formatSigned(c.amount)} → cumulative ${formatSigned(c.cumulative)} at ${formatElapsed(c.elapsedSeconds)}"
        }
        val draw = markDraws(g, fm).lastOrNull { abs(event.x - it.x) <= 4 || it.box?.contains(event.point) == true } ?: return null
        return "${draw.mark.label} — at ${formatElapsed(draw.mark.elapsedSeconds)}"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g2 = graphics as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val fm = g2.fontMetrics
        val g = geometry(width, fm)
        paintAxis(g2, g, fm)
        val marks = markDraws(g, fm)
        paintMarkLines(g2, g, marks)
        if (hasBias) paintBias(g2, g, fm)
        paintFlags(g2, fm, marks)
    }

    private fun paintAxis(g2: Graphics2D, g: Geometry, fm: FontMetrics) {
        g2.stroke = BasicStroke(1f)
        g2.color = AXIS
        g2.drawLine(g.left, g.axisY, g.right, g.axisY)
        for (t in AnnotationLayout.axisTicks(timeline.durationSeconds)) {
            val x = g.x(t, timeline.durationSeconds)
            val label = formatElapsed(t)
            g2.drawLine(x, g.axisY, x, g.axisY + 3)
            g2.drawString(label, (x - fm.stringWidth(label) / 2).coerceIn(0, (width - fm.stringWidth(label)).coerceAtLeast(0)), g.axisY + 4 + fm.ascent)
        }
    }

    private fun paintMarkLines(g2: Graphics2D, g: Geometry, marks: List<MarkDraw>) {
        g2.color = MARK_COLOR
        g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(4f, 3f), 0f)
        marks.forEach { g2.drawLine(it.x, g.flagsTop, it.x, g.axisY) }
    }

    private fun paintBias(g2: Graphics2D, g: Geometry, fm: FontMetrics) {
        val zeroY = g.yBias(0)
        g2.color = GUIDE
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(3f, 3f), 0f)
        g2.drawLine(g.left, zeroY, g.right, zeroY)
        g2.color = AXIS
        listOf(g.high, 0, g.low).distinct().forEach {
            val label = formatSigned(it)
            g2.drawString(label, g.left - fm.stringWidth(label) - 4, g.yBias(it) + fm.ascent / 2 - 1)
        }

        val points = stepPoints(g)
        g2.color = BIAS_COLOR
        g2.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
        var prevX = g.left
        var prevY = zeroY
        for (p in points) {
            g2.drawLine(prevX, prevY, p.x, prevY)
            g2.drawLine(p.x, prevY, p.x, p.y)
            prevX = p.x
            prevY = p.y
        }
        g2.drawLine(prevX, prevY, g.right, prevY)

        val shown = AnnotationLayout.thinLabels(points.map { it.x }, LABEL_GAP)
        points.forEachIndexed { i, p ->
            g2.fillOval(p.x - 3, p.y - 3, 6, 6)
            if (!shown[i]) return@forEachIndexed
            val label = formatSigned(p.change.cumulative)
            val above = p.change.amount > 0
            val y = if (above) p.y - 6 else p.y + 6 + fm.ascent
            g2.drawString(label, p.x - fm.stringWidth(label) / 2, y.coerceIn(fm.ascent, height - 2))
        }
    }

    private fun paintFlags(g2: Graphics2D, fm: FontMetrics, marks: List<MarkDraw>) {
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

    private companion object {
        const val LEFT_PAD = 40
        const val RIGHT_PAD = 16
        const val FLAG_HEIGHT = 14
        const val FLAG_ROWS = 2
        const val BIAS_HEIGHT = 44
        const val MAX_FLAG_TEXT = 190
        const val LABEL_GAP = 26
        val AXIS = JBColor(0x808080, 0x9A9DA3)
        val GUIDE = JBColor(0xC0C0C0, 0x5A5D63)
        val BIAS_COLOR = JBColor(0x1565C0, 0x6AB7FF)
        val MARK_COLOR = JBColor(0xB8860B, 0xE8B341)
        val MARK_FILL = JBColor(Color(232, 179, 65, 50), Color(232, 179, 65, 50))
        val MARK_TEXT = JBColor(0x7A5A06, 0xF0CB74)
    }
}
