package dev.gopherglide.ggplugin.snap.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.snap.ManualChanges
import dev.gopherglide.ggplugin.snap.RunTimeline
import dev.gopherglide.ggplugin.snap.SnapBiasEvent
import dev.gopherglide.ggplugin.snap.SnapMark
import dev.gopherglide.ggplugin.snap.SnapMeta
import dev.gopherglide.ggplugin.snap.SnapModel

class ManualChangesSectionTest : BasePlatformTestCase() {
    private val timeline = RunTimeline(150.0, listOf(RunAnnotation.Mark("m", 10.0)), listOf(RunAnnotation.BiasChange(5, 5, 20.0)), 5)

    fun testStartsExpandedByDefault() {
        assertTrue(ManualChangesSection(timeline, { false }, { }).isExpanded)
    }

    fun testStartsCollapsedWhenThatWasRemembered() {
        assertFalse(ManualChangesSection(timeline, { true }, { }).isExpanded)
    }

    fun testToggleFlipsTheBodyAndRemembersTheChoice() {
        var stored = false
        val section = ManualChangesSection(timeline, { stored }, { stored = it })
        section.toggle()
        assertFalse(section.isExpanded)
        assertTrue(stored)
        section.toggle()
        assertTrue(section.isExpanded)
        assertFalse(stored)
    }

    fun testANewSectionPicksUpTheRememberedChoice() {
        var stored = false
        ManualChangesSection(timeline, { stored }, { stored = it }).toggle()
        assertFalse(ManualChangesSection(timeline, { stored }, { stored = it }).isExpanded)
    }

    private fun snap(meta: SnapMeta) = SnapModel(meta = meta)

    fun testNoticeBannerAppearsOnlyWhenEitherSnapshotHasAnnotations() {
        val annotated = snap(SnapMeta(marks = listOf(SnapMark("m", 1.0))))
        val plain = snap(SnapMeta())
        assertNull(ManualChangesBanner.forSnaps(plain, plain))
        assertNotNull(ManualChangesBanner.forSnaps(annotated, plain))
        assertNotNull(ManualChangesBanner.forSnaps(plain, annotated))
    }

    fun testNoticeBannerCarriesTheDescription() {
        val a = snap(SnapMeta(biasEvents = listOf(SnapBiasEvent(5, 5, 1.0))))
        val b = snap(SnapMeta())
        val notice = ManualChanges.describe(a.meta, b.meta)!!
        val labels = com.intellij.util.ui.UIUtil.findComponentsOfType(ManualChangesBanner.forSnaps(a, b)!!, com.intellij.ui.components.JBLabel::class.java)
        assertTrue(labels.any { it.text?.contains(notice.summary) == true && it.text!!.contains("Manual changes during the run:") && it.text!!.contains("<br>${notice.caveat}") })
    }

    fun testNoteShowsItsTextAsAGrayLine() {
        assertEquals("No marks or bias changes during this run", ManualChangesNote("No marks or bias changes during this run").label.text)
    }

    fun testListsEveryEventInTimeOrder() {
        val table = com.intellij.util.ui.UIUtil.findComponentsOfType(ManualChangesSection(timeline, { false }, { }), ManualChangesTable::class.java).single()
        assertEquals(2, table.rowCount)
        assertEquals(listOf("0:10", "0:20"), (0 until 2).map { table.getValueAt(it, 0) })
        assertEquals("m", table.getValueAt(0, 2))
        assertEquals("+5 RPS", table.getValueAt(1, 2))
        assertEquals("total +5", table.getValueAt(1, 3))
    }

    fun testFiguresAppearOnlyWhenThereAreBiasChanges() {
        fun tiles(t: RunTimeline) = com.intellij.util.ui.UIUtil.findComponentsOfType(ManualChangesSection(t, { false }, { }), ChangeTilesPanel::class.java)
        assertEquals(1, tiles(timeline).size)
        assertTrue(tiles(RunTimeline(60.0, listOf(RunAnnotation.Mark("m", 1.0)), emptyList(), null)).isEmpty())
    }

    fun testHeaderNamesTheSectionAndSummarisesIt() {
        val labels = com.intellij.util.ui.UIUtil.findComponentsOfType(ManualChangesSection(timeline, { false }, { }), com.intellij.ui.components.JBLabel::class.java).map { it.text }
        assertTrue(labels.contains("MANUAL CHANGES"))
        assertTrue(labels.contains(timeline.summary()))
    }
}
