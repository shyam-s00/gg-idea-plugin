package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualChangeRowsTest {
    @Test
    fun aMarkRowShowsItsLabelAndNoTotal() {
        assertEquals(
            ManualChangeRow("0:18", ManualChangeRow.Kind.MARK, "cache-warm", ""),
            ManualChangeRow.from(RunAnnotation.Mark("cache-warm", 18.4))
        )
    }

    @Test
    fun aBiasRowShowsTheSignedAmountAndTheRunningTotal() {
        assertEquals(
            ManualChangeRow("1:03", ManualChangeRow.Kind.BIAS, "−5 RPS", "total +15"),
            ManualChangeRow.from(RunAnnotation.BiasChange(-5, 15, 63.2))
        )
    }

    @Test
    fun tilesNameThePeakTheActiveTimeAndTheFirstChange() {
        val stats = ChangeStats(finalBias = 15, peakBias = 20, peakAtSeconds = 52.0, activeSeconds = 106.0, firstChangeSeconds = 44.0)
        assertEquals(
            listOf(
                ChangeTile("FINAL BIAS", "+15", "at the end of the run"),
                ChangeTile("PEAK BIAS", "+20", "reached at 0:52"),
                ChangeTile("BIAS ACTIVE", "1:46", "71% of the run"),
                ChangeTile("FIRST CHANGE", "0:44", "29% into the run")
            ),
            ChangeTiles.from(stats, 150.0)
        )
    }
}
