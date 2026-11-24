package dev.gopherglide.ggplugin.execution.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import java.awt.Font
import java.awt.image.BufferedImage
import java.awt.event.MouseEvent

class RpsChartAnnotationsTest : BasePlatformTestCase() {
    private fun chart(vararg annotations: RunAnnotation): RpsChartComponent {
        val chart = RpsChartComponent()
        chart.font = Font(Font.DIALOG, Font.PLAIN, 12)
        chart.setSize(642, 210)
        chart.setKnownPeakRps(100)
        chart.setTotalDuration(60.0)
        chart.addDataPoint(10.0, 20.0, 20)
        chart.addDataPoint(40.0, 60.0, 60)
        chart.setAnnotations(annotations.toList())
        return chart
    }

    private fun tooltipAt(chart: RpsChartComponent, elapsed: Double, rps: Double): String? {
        val x = 36 + ((642 - 42) * (elapsed / 60.0)).toInt()
        val y = 6 + ((210 - 10) * (1 - rps / 100.0)).toInt()
        return chart.getToolTipText(MouseEvent(chart, MouseEvent.MOUSE_MOVED, 0L, 0, x, y, 0, false))
    }

    fun testHoldTickExplainsItselfOnHover() {
        val chart = chart(RunAnnotation.HoldChange(15, 30.0))
        assertEquals("Hold 15 RPS at 0:30", tooltipAt(chart, 30.0, 15.0))
    }

    fun testReleaseTickSitsOnTheTargetLine() {
        val chart = chart(RunAnnotation.HoldChange(null, 30.0))
        assertEquals("Hold released at 0:30", tooltipAt(chart, 30.0, 60.0))
    }

    fun testAutopilotAndAgentSourcesAreNamedInTheTooltip() {
        val auto = chart(RunAnnotation.HoldChange(28, 30.0, AnnotationSource.AUTOPILOT))
        assertEquals("Hold 28 RPS at 0:30 (⚡ autopilot)", tooltipAt(auto, 30.0, 28.0))
        val agent = chart(RunAnnotation.Mark("canary", 30.0, AnnotationSource.AGENT))
        assertEquals("canary — at 0:30 (🤖 agent)", tooltipAt(agent, 30.0, 1.0))
    }

    fun testAUserMarkTooltipStaysPlain() {
        val chart = chart(RunAnnotation.Mark("cache-warm", 30.0))
        assertEquals("cache-warm — at 0:30", tooltipAt(chart, 30.0, 1.0))
    }

    fun testBiasTooltipNamesANonUserSource() {
        val chart = chart(RunAnnotation.BiasChange(5, 5, 30.0, AnnotationSource.AGENT))
        assertTrue(tooltipAt(chart, 30.0, 60.0)!!.endsWith("(🤖 agent)"))
    }

    fun testEveryKindOfAnnotationPaintsWithoutError() {
        val chart = chart(
            RunAnnotation.Mark("a", 5.0),
            RunAnnotation.Mark("b", 20.0, AnnotationSource.AUTOPILOT),
            RunAnnotation.BiasChange(10, 10, 25.0),
            RunAnnotation.HoldChange(40, 35.0),
            RunAnnotation.HoldChange(null, 50.0, AnnotationSource.AGENT)
        )
        val image = BufferedImage(642, 210, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        chart.paint(g)
        g.dispose()
    }

    fun testHoldTicksAreNotDrawnOnAScrollingAxis() {
        val chart = RpsChartComponent()
        chart.font = Font(Font.DIALOG, Font.PLAIN, 12)
        chart.setSize(642, 210)
        chart.setKnownPeakRps(100)
        chart.addDataPoint(10.0, 20.0, 20)
        chart.addDataPoint(40.0, 60.0, 60)
        chart.setAnnotations(listOf(RunAnnotation.HoldChange(15, 30.0)))
        assertNull(tooltipAt(chart, 30.0, 15.0))
    }
}
