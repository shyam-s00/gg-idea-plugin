package dev.gopherglide.ggplugin.execution.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkLabelsTest {
    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("cache-warm", MarkLabels.normalize("  cache-warm \t"))
    }

    @Test
    fun blankLabelsAreRejected() {
        assertNull(MarkLabels.normalize(""))
        assertNull(MarkLabels.normalize("   \n\t "))
    }

    @Test
    fun newlinesAndRunsOfWhitespaceCollapseToOneSpace() {
        assertEquals("deploy v2 canary", MarkLabels.normalize("deploy\nv2\t\t canary"))
    }

    @Test
    fun quotesAndUnicodeSurvive() {
        assertEquals("say \"hi\" ✓ 日本", MarkLabels.normalize("say \"hi\" ✓ 日本"))
    }

    @Test
    fun aLabelOfExactlyTheLimitIsKept() {
        val label = "a".repeat(MarkLabels.MAX_LENGTH)
        assertEquals(label, MarkLabels.normalize(label))
    }

    @Test
    fun aLongerLabelIsCutToTheLimit() {
        assertEquals(MarkLabels.MAX_LENGTH, MarkLabels.normalize("a".repeat(500))!!.length)
    }

    @Test
    fun cuttingNeverSplitsASurrogatePair() {
        val cut = MarkLabels.normalize("😀".repeat(300))!!
        assertEquals(MarkLabels.MAX_LENGTH, cut.codePointCount(0, cut.length))
        assertTrue(cut.none { Character.isSurrogate(it) } || cut.length == 2 * MarkLabels.MAX_LENGTH)
        assertEquals("😀".repeat(MarkLabels.MAX_LENGTH), cut)
    }
}
