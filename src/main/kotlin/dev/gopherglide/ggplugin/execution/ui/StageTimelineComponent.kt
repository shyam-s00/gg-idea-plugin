package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.JBColor
import dev.gopherglide.ggplugin.execution.StageInfo
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * Horizontal progress bar showing all load stages, highlighting the one currently running.
 * Segment widths are proportional to each stage's duration when known (`stages`, from gg's
 * "started" heartbeat); otherwise falls back to `totalStages` equal-width segments highlighting
 * just the current index, since there's no duration to size segments by.
 */
class StageTimelineComponent : JComponent() {
    private var stages: List<StageInfo> = emptyList()
    private var totalStages: Int = 0
    private var currentStage: Int = 1
    private var elapsedSec: Double = 0.0

    init {
        preferredSize = Dimension(200, 36)
        minimumSize = Dimension(120, 28)
    }

    /** Call once, from the "started" heartbeat. Null/empty renders the equal-width fallback. */
    fun setStages(stages: List<StageInfo>?) {
        this.stages = stages ?: emptyList()
        repaint()
    }

    /** Call on every heartbeat. [elapsedSec] is seconds since the run started. */
    fun update(stage: Int, totalStages: Int, elapsedSec: Double) {
        this.currentStage = stage
        this.totalStages = totalStages
        this.elapsedSec = elapsedSec
        repaint()
    }

    fun clear() {
        stages = emptyList()
        totalStages = 0
        currentStage = 1
        elapsedSec = 0.0
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val segmentCount = if (stages.isNotEmpty()) stages.size else totalStages
        if (segmentCount <= 0) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val barTop = 4
        val barHeight = (height - 16).coerceAtLeast(8)
        val gap = 2
        val usableWidth = (width - gap * (segmentCount - 1)).coerceAtLeast(segmentCount)

        val activeColor = JBColor(0x3592C4, 0x589DF6)
        val inactiveColor = JBColor(0xD3D3D3, 0x3C3F41)

        if (stages.isNotEmpty()) {
            val totalDuration = stages.sumOf { it.durationSeconds }.coerceAtLeast(0.001)
            val activeIndex = (currentStage - 1).coerceIn(0, stages.size - 1)

            var x = 0.0
            for ((index, stageInfo) in stages.withIndex()) {
                val segWidth = (stageInfo.durationSeconds / totalDuration) * usableWidth
                g2.color = if (index == activeIndex) activeColor else inactiveColor
                g2.fillRect(x.toInt(), barTop, segWidth.toInt().coerceAtLeast(1), barHeight)

                val fm = g2.fontMetrics
                val labelWidth = fm.stringWidth(stageInfo.name)
                if (labelWidth < segWidth - 4) {
                    g2.color = foreground
                    g2.drawString(stageInfo.name, (x + (segWidth - labelWidth) / 2).toInt(), barTop + barHeight + fm.ascent)
                }

                x += segWidth + gap
            }

            // Position marker: elapsed time as a fraction of the total resolved stage duration.
            val markerFraction = (elapsedSec / totalDuration).coerceIn(0.0, 1.0)
            val markerX = (markerFraction * usableWidth).toInt().coerceIn(0, (width - 2).coerceAtLeast(0))
            g2.color = foreground
            g2.fillRect(markerX, barTop - 3, 2, barHeight + 6)
        } else {
            val segWidth = usableWidth.toDouble() / segmentCount
            val activeIndex = (currentStage - 1).coerceIn(0, segmentCount - 1)

            var x = 0.0
            for (index in 0 until segmentCount) {
                g2.color = if (index == activeIndex) activeColor else inactiveColor
                g2.fillRect(x.toInt(), barTop, segWidth.toInt().coerceAtLeast(1), barHeight)
                x += segWidth + gap
            }
        }
    }
}
