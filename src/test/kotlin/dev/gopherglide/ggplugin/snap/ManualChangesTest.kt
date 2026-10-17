package dev.gopherglide.ggplugin.snap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualChangesTest {
    private fun meta(marks: Int, vararg cumulative: Int): SnapMeta = SnapMeta(
        marks = List(marks) { SnapMark("m$it", it.toDouble()) }.takeIf { it.isNotEmpty() },
        biasEvents = cumulative.mapIndexed { i, c -> SnapBiasEvent(5, c, i.toDouble()) }.takeIf { it.isNotEmpty() }
    )

    private val plain = SnapMeta()

    @Test
    fun noNoticeWhenNeitherSnapshotHasAnything() {
        assertNull(ManualChanges.describe(plain, plain))
        assertNull(ManualChanges.describe(null, null))
    }

    @Test
    fun bothSidesAnnotatedAreDescribedWithTheLikeForLikeCaveat() {
        assertEquals(
            ManualChanges.Notice(
                "baseline had 3 bias changes (final +15) and 2 marks; target had no bias changes and 1 mark.",
                "Peak RPS and latency may not be like for like."
            ),
            ManualChanges.describe(meta(2, 10, 20, 15), meta(1))
        )
    }

    @Test
    fun anUnannotatedSideSaysSo() {
        val caveat = "Peak RPS and latency may not be like for like."
        assertEquals(
            ManualChanges.Notice("baseline had no bias changes or marks; target had 1 bias change (final +5) and no marks.", caveat),
            ManualChanges.describe(plain, meta(0, 5))
        )
        assertEquals(
            ManualChanges.Notice("baseline had 1 bias change (final +5) and no marks; target had no bias changes or marks.", caveat),
            ManualChanges.describe(meta(0, 5), plain)
        )
    }

    @Test
    fun marksOnlyDoNotClaimTheNumbersAreUnlikeForLike() {
        assertEquals(
            ManualChanges.Notice("baseline had no bias changes and 2 marks; target had no bias changes or marks.", null),
            ManualChanges.describe(meta(2), plain)
        )
    }

    @Test
    fun singularAndPluralWordsAreRight() {
        val text = ManualChanges.describe(meta(1, 5), meta(3, 5, 10))!!.summary
        assertEquals(true, text.contains("1 bias change (final +5) and 1 mark;"))
        assertEquals(true, text.contains("2 bias changes (final +10) and 3 marks."))
    }

    @Test
    fun finalBiasUsesTheRecordedFieldAndKeepsItsSign() {
        val m = SnapMeta(finalBias = -10, biasEvents = listOf(SnapBiasEvent(-10, -10, 1.0)))
        assertEquals(true, ManualChanges.describe(m, plain)!!.summary.contains("(final −10)"))
    }
}
