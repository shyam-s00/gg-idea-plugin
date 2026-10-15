package dev.gopherglide.ggplugin.snap.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.snap.ManualChanges
import dev.gopherglide.ggplugin.snap.RunTimeline
import dev.gopherglide.ggplugin.snap.SnapBiasEvent
import dev.gopherglide.ggplugin.snap.SnapMark
import dev.gopherglide.ggplugin.snap.SnapMeta
import dev.gopherglide.ggplugin.snap.SnapModel

class RunTimelineSectionTest : BasePlatformTestCase() {
    private val timeline = RunTimeline(150.0, listOf(RunAnnotation.Mark("m", 10.0)), listOf(RunAnnotation.BiasChange(5, 5, 20.0)), 5)

    fun testStartsExpandedByDefault() {
        assertTrue(RunTimelineSection(timeline, { false }, { }).isExpanded)
    }

    fun testStartsCollapsedWhenThatWasRemembered() {
        assertFalse(RunTimelineSection(timeline, { true }, { }).isExpanded)
    }

    fun testToggleFlipsTheBodyAndRemembersTheChoice() {
        var stored = false
        val section = RunTimelineSection(timeline, { stored }, { stored = it })
        section.toggle()
        assertFalse(section.isExpanded)
        assertTrue(stored)
        section.toggle()
        assertTrue(section.isExpanded)
        assertFalse(stored)
    }

    fun testANewSectionPicksUpTheRememberedChoice() {
        var stored = false
        RunTimelineSection(timeline, { stored }, { stored = it }).toggle()
        assertFalse(RunTimelineSection(timeline, { stored }, { stored = it }).isExpanded)
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
}
