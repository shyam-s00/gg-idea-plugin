package dev.gopherglide.ggplugin.execution.annotations

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationLayoutTest {
    @Test
    fun labelSitsJustRightOfItsAnchorWhenItFits() {
        assertEquals(103, AnnotationLayout.labelLeft(x = 100, width = 50, minX = 36, maxX = 900))
    }

    @Test
    fun labelFlipsLeftOfItsAnchorNearTheRightEdge() {
        assertEquals(827, AnnotationLayout.labelLeft(x = 880, width = 50, minX = 36, maxX = 900))
    }

    @Test
    fun labelNeverStartsBeforeTheLeftEdge() {
        assertEquals(36, AnnotationLayout.labelLeft(x = 40, width = 100, minX = 36, maxX = 60))
    }

    @Test
    fun anchorAtTheLeftEdgeStillGetsItsLabel() {
        assertEquals(39, AnnotationLayout.labelLeft(x = 36, width = 80, minX = 36, maxX = 900))
    }

    @Test
    fun farApartLabelsShareTheFirstRow() {
        val rows = AnnotationLayout.assignRows(listOf(LabelSpan(0, 50), LabelSpan(100, 50)), rows = 3)
        assertEquals(listOf(0, 0), rows)
    }

    @Test
    fun overlappingLabelsStaggerOntoLaterRows() {
        val rows = AnnotationLayout.assignRows(listOf(LabelSpan(0, 100), LabelSpan(20, 100), LabelSpan(40, 100)), rows = 3)
        assertEquals(listOf(0, 1, 2), rows)
    }

    @Test
    fun aLabelWithNoFreeRowGetsNone() {
        val spans = List(4) { LabelSpan(it * 10, 100) }
        assertEquals(listOf(0, 1, 2, null), AnnotationLayout.assignRows(spans, rows = 3))
    }

    @Test
    fun labelsCloserThanTheGapCountAsOverlapping() {
        assertEquals(listOf(0, 1), AnnotationLayout.assignRows(listOf(LabelSpan(0, 50), LabelSpan(52, 48)), rows = 2, gap = 4))
        assertEquals(listOf(0, 0), AnnotationLayout.assignRows(listOf(LabelSpan(0, 50), LabelSpan(55, 45)), rows = 2, gap = 4))
    }

    @Test
    fun aLaterLabelCanReuseARowFreedByDistance() {
        val spans = listOf(LabelSpan(0, 100), LabelSpan(10, 100), LabelSpan(300, 50))
        assertEquals(listOf(0, 1, 0), AnnotationLayout.assignRows(spans, rows = 2))
    }

    @Test
    fun noRowsMeansNoLabels() {
        assertEquals(listOf(null, null), AnnotationLayout.assignRows(listOf(LabelSpan(0, 10), LabelSpan(50, 10)), rows = 0))
    }

    @Test
    fun axisTicksAreRoundAndStartAtZero() {
        assertEquals(listOf(0.0, 30.0, 60.0, 90.0, 120.0, 150.0), AnnotationLayout.axisTicks(150.0))
        assertEquals(listOf(0.0, 5.0, 10.0), AnnotationLayout.axisTicks(14.04))
    }

    @Test
    fun axisTicksStayWithinTheLimitForLongRuns() {
        assertEquals(true, AnnotationLayout.axisTicks(7200.0).size <= 8)
        assertEquals(true, AnnotationLayout.axisTicks(3.0, maxTicks = 3).size <= 3)
    }

    @Test
    fun thinLabelsKeepsTheFirstAndDropsThoseTooClose() {
        assertEquals(listOf(true, false, true, false, true), AnnotationLayout.thinLabels(listOf(0, 10, 30, 40, 70), minGap = 26))
    }

    @Test
    fun thinLabelsKeepsEverythingThatIsFarEnoughApart() {
        assertEquals(listOf(true, true, true), AnnotationLayout.thinLabels(listOf(0, 50, 100), minGap = 26))
    }

    @Test
    fun manyCrowdedLabelsLeaveOnlyAWellSpacedSubset() {
        val kept = AnnotationLayout.thinLabels((0 until 60).map { it * 3 }, minGap = 26).count { it }
        assertEquals(true, kept in 5..8)
    }
}
