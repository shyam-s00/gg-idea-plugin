package dev.gopherglide.ggplugin.snap.ui

import com.google.gson.Gson
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.snap.SnapModel
import javax.swing.Action
import javax.swing.JComponent

class SnapViewDialogTest : BasePlatformTestCase() {
    private val gson = Gson()

    private fun snap(name: String): SnapModel =
        gson.fromJson(checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).bufferedReader().readText(), SnapModel::class.java)

    /** The dialog and the content it would show; the root pane is only filled once a dialog is on screen. */
    private class Shown(val dialog: SnapViewDialog, val content: JComponent) {
        val rootPane: JComponent get() = content

        @Suppress("UNCHECKED_CAST")
        val leftActions: List<String> = (call("createLeftSideActions") as Array<Action>).map { it.getValue(Action.NAME) as String }

        private fun call(name: String): Any =
            SnapViewDialog::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(dialog)
    }

    private fun withDialog(snap: SnapModel, check: (Shown) -> Unit) {
        val dialog = SnapViewDialog(project, snap)
        try {
            val content = SnapViewDialog::class.java.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }.invoke(dialog) as JComponent
            check(Shown(dialog, content))
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    private fun labels(shown: Shown) = UIUtil.findComponentsOfType(shown.rootPane, JBLabel::class.java).map { it.text.orEmpty() }

    fun testAutopilotRunShowsItsOutcomeBannerAboveTheTimeline() {
        withDialog(snap("snap-v150-find-ceiling.snap")) { dialog ->
            val banner = UIUtil.findComponentsOfType(dialog.rootPane, AutopilotBanner::class.java).single()
            assertEquals("⚡ Autopilot Find Ceiling: Ceiling found: 28 RPS", banner.headline.text)
            assertTrue(banner.detail.text.startsWith("Limits: p99 ≤ 250ms"))
            assertTrue(banner.toolTipText.contains("+0:07 stepping (13 RPS)"))
        }
    }

    fun testRunWithoutAnAutopilotHasNoBanner() {
        withDialog(snap("snap-v130-annotated.snap")) { dialog ->
            assertTrue(UIUtil.findComponentsOfType(dialog.rootPane, AutopilotBanner::class.java).isEmpty())
        }
        withDialog(snap("snap-v120-plain.snap")) { dialog ->
            assertTrue(UIUtil.findComponentsOfType(dialog.rootPane, AutopilotBanner::class.java).isEmpty())
        }
    }

    fun testOriginBadgeAppearsOnlyWhenTheSnapshotNamesWhoStartedTheRun() {
        val agent = snap("snap-v150-autopilot.snap").let { it.copy(meta = it.meta!!.copy(origin = "agent")) }
        withDialog(agent) { assertTrue(labels(it).contains("Origin: 🤖 Agent")) }
        withDialog(snap("snap-v150-autopilot.snap")) { dialog -> assertTrue(labels(dialog).none { it.startsWith("Origin:") }) }
        withDialog(snap("snap-v130-annotated.snap")) { dialog -> assertTrue(labels(dialog).none { it.startsWith("Origin:") }) }
    }

    fun testTheReportActionsAreOfferedInTheDialog() {
        withDialog(snap("snap-v150-autopilot.snap")) { dialog ->
            assertEquals(listOf("📄 Export Mission Report…", "Copy PR Markdown"), dialog.leftActions)
        }
    }

    fun testTheTimelineSummaryNamesTheHolds() {
        withDialog(snap("snap-v150-autopilot.snap")) { dialog ->
            assertTrue(labels(dialog).contains("1 hold · 1 bias change (final +5) · 2 marks · 0:16 run"))
        }
    }
}
