package dev.gopherglide.ggplugin.execution.ui

import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import dev.gopherglide.ggplugin.execution.ui.RpsChartComponent.Companion.grownAxisMax
import dev.gopherglide.ggplugin.execution.ui.RpsChartComponent.Companion.targetLevelAt
import dev.gopherglide.ggplugin.execution.ui.RpsChartComponent.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpsChartComponentTest {
    @Test
    fun axisGrowsPastTheStagePeakWithHeadroom() {
        assertEquals(80.0, grownAxisMax(50.0, 70.0), 0.0)
    }

    @Test
    fun axisNeverShrinksOrMovesWhenTheValueFits() {
        assertEquals(50.0, grownAxisMax(50.0, 50.0), 0.0)
        assertEquals(80.0, grownAxisMax(80.0, 10.0), 0.0)
    }

    @Test
    fun axisStartsFromZeroWhenThePeakIsUnknown() {
        assertEquals(15.0, grownAxisMax(0.0, 12.0), 0.0)
        assertEquals(60.0, grownAxisMax(0.0, 50.0), 0.0)
    }

    @Test
    fun axisStepsGrowWithTheMagnitude() {
        assertEquals(150.0, grownAxisMax(0.0, 120.0), 0.0)
        assertEquals(110.0, grownAxisMax(0.0, 100.0), 0.0)
    }

    private val samples = listOf(Point(5.0, 10.0, 20), Point(10.0, 30.0, 40), Point(15.0, 50.0, 60))

    @Test
    fun targetLevelIsTakenFromTheFirstSampleAtOrAfterTheTime() {
        assertEquals(20, targetLevelAt(samples, 3.0))
        assertEquals(40, targetLevelAt(samples, 10.0))
        assertEquals(60, targetLevelAt(samples, 11.0))
    }

    @Test
    fun targetLevelFallsBackToTheLastSampleForALaterTime() {
        assertEquals(60, targetLevelAt(samples, 99.0))
    }

    @Test
    fun targetLevelIsNullWithoutSamples() {
        assertNull(targetLevelAt(emptyList(), 5.0))
    }

    @Test
    fun signedValuesUseATrueMinusSign() {
        assertEquals("+10", formatSigned(10))
        assertEquals("−5", formatSigned(-5))
        assertEquals("0", formatSigned(0))
    }

    @Test
    fun elapsedTimeIsMinutesAndSeconds() {
        assertEquals("0:18", formatElapsed(18.4))
        assertEquals("1:05", formatElapsed(65.0))
        assertEquals("0:00", formatElapsed(-3.0))
    }
}
