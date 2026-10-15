package dev.gopherglide.ggplugin.execution.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JButton

class LiveControlPanelTest : BasePlatformTestCase() {
    private class FakeControls : RunControls {
        val sent = mutableListOf<Int>()
        val replies = mutableListOf<(ControlReply) -> Unit>()
        val marksSent = mutableListOf<String>()
        val markReplies = mutableListOf<(ControlReply) -> Unit>()
        override fun requestStop() {}
        override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) {
            sent += amount
            replies += onReply
        }
        override fun sendMark(label: String, onReply: (ControlReply) -> Unit) {
            marksSent += label
            markReplies += onReply
        }
        val infoLoaders = mutableListOf<(InstalledBinary?, GopherGlideVersion?) -> Unit>()
        val upgrades = mutableListOf<(Throwable?) -> Unit>()
        override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) { infoLoaders += onLoaded }
        override fun upgradeBinary(onDone: (Throwable?) -> Unit) { upgrades += onDone }
    }

    private val all = listOf("control.bias", "control.mark", "control.stop")
    private val annotations = mutableListOf<RunAnnotation>()
    private var clock = 42.0

    private fun ack(bias: Int) = ControlReply.Ack(HeartbeatPayload(event = "ack", bias = bias))

    private var nextLabel: String? = null
    private var duringPrompt: () -> Unit = {}

    private fun panel(): LiveControlPanel =
        LiveControlPanel({ clock }, { annotations += it }, { duringPrompt(); nextLabel })

    private fun active(controls: FakeControls = FakeControls()): Pair<LiveControlPanel, FakeControls> {
        val panel = panel()
        panel.reset()
        panel.bind(controls)
        panel.onStarted(all)
        return panel to controls
    }

    private fun button(panel: LiveControlPanel, text: String): JButton =
        UIUtil.findComponentsOfType(panel, JButton::class.java).first { it.text == text }

    private fun labels(panel: LiveControlPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).map { it.text.orEmpty() }

    fun testHiddenUntilARunStarts() {
        assertFalse(panel().isVisible)
    }

    fun testShownButDisabledBeforeTheHandshake() {
        val panel = panel()
        panel.reset()
        assertTrue(panel.isVisible)
        assertTrue(labels(panel).contains("Bias —"))
        assertTrue(labels(panel).contains("Waiting for gg to report capabilities…"))
        assertFalse(button(panel, "+5").isEnabled)
        assertFalse(button(panel, "Reset").isEnabled)
    }

    private fun shown(panel: LiveControlPanel, c: Component): Boolean =
        generateSequence(c) { it.parent }.takeWhile { it !== panel.parent }.all { it.isVisible }

    private fun older(controls: FakeControls = FakeControls()): Pair<LiveControlPanel, FakeControls> {
        val panel = panel()
        panel.reset()
        panel.bind(controls)
        panel.onStarted(null)
        return panel to controls
    }

    private val plugin12 = InstalledBinary("/plugins/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.PLUGIN)
    private val v13 = GopherGlideVersion(1, 3, 0)

    private fun upgradeButton(panel: LiveControlPanel) = button(panel, "Upgrade gg")

    fun testOlderBinaryShowsTheNoticeInsteadOfTheControls() {
        val (panel, _) = older()
        assertTrue(panel.isVisible)
        assertTrue(labels(panel).contains("Live Control needs gg v1.3.0+."))
        assertFalse(shown(panel, button(panel, "+10")))
        assertFalse(shown(panel, button(panel, "Mark…")))
    }

    fun testNoticeLooksUpTheBinaryOnlyForOlderBinaries() {
        val (_, controls) = older()
        assertEquals(1, controls.infoLoaders.size)
        val (_, active) = active()
        assertTrue(active.infoLoaders.isEmpty())
    }

    fun testNoticeNamesTheInstalledVersionOnceLoaded() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        assertTrue(labels(panel).contains("Live Control needs gg v1.3.0+ (installed: v1.2.0)."))
    }

    fun testUpgradeWaitsForTheRunToEnd() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        assertTrue(shown(panel, upgradeButton(panel)))
        assertFalse(upgradeButton(panel).isEnabled)
        assertTrue(labels(panel).contains("Upgrade is available when the run ends"))
        panel.onEnded()
        assertTrue(upgradeButton(panel).isEnabled)
    }

    fun testUpgradeButtonIsAbsentForACustomBinary() {
        val (panel, controls) = older()
        controls.infoLoaders[0](InstalledBinary("/opt/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.CUSTOM), v13)
        panel.onEnded()
        assertFalse(shown(panel, upgradeButton(panel)))
        assertTrue(labels(panel).any { it.contains("at /opt/gg") })
    }

    fun testClickingUpgradeShowsProgressThenTheOutcome() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        panel.onEnded()
        upgradeButton(panel).doClick()
        assertEquals(1, controls.upgrades.size)
        assertFalse(upgradeButton(panel).isEnabled)
        assertTrue(labels(panel).any { it.endsWith("Upgrading…") })
        controls.upgrades[0](null)
        assertTrue(labels(panel).contains("gg v1.3.0 is installed. Live Control will be available from the next run."))
        assertFalse(shown(panel, upgradeButton(panel)))
    }

    fun testFailedUpgradeShowsWhyAndAllowsAnotherTry() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        panel.onEnded()
        upgradeButton(panel).doClick()
        controls.upgrades[0](RuntimeException("no network"))
        assertTrue(labels(panel).contains("Upgrade failed: no network"))
        assertTrue(upgradeButton(panel).isEnabled)
    }

    fun testBinaryInfoArrivingAfterANewRunStartedIsIgnored() {
        val (panel, controls) = older()
        panel.reset()
        controls.infoLoaders[0](plugin12, v13)
        assertFalse(labels(panel).any { it.contains("installed: v1.2.0") })
    }

    fun testShownButOffWhenControlIsDisabledForTheRun() {
        val panel = panel()
        panel.reset()
        panel.onStarted(emptyList())
        assertTrue(panel.isVisible)
        assertTrue(labels(panel).contains("Live control is off for this run"))
        assertFalse(button(panel, "+5").isEnabled)
    }

    fun testSteppersEnabledAndResetDisabledAtZero() {
        val (panel, _) = active()
        listOf("−10", "−5", "+5", "+10").forEach { assertTrue(it, button(panel, it).isEnabled) }
        assertFalse(button(panel, "Reset").isEnabled)
        assertTrue(labels(panel).contains("Bias 0 RPS"))
    }

    fun testClickSendsTheStepAndShowsItAtOnce() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        assertEquals(listOf(10), controls.sent)
        assertTrue(labels(panel).contains("Bias +10 RPS"))
        assertTrue(button(panel, "Reset").isEnabled)
    }

    fun testAckRecordsABiasChangeAnnotationWithTheCumulativeValue() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        controls.replies[0](ack(10))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.BiasChange(10, 10, 42.0)), annotations)
        assertTrue(labels(panel).contains("Bias +10 RPS"))
    }

    fun testRapidClicksConvergeOnGgsTotal() {
        val (panel, controls) = active()
        button(panel, "+5").doClick()
        button(panel, "+5").doClick()
        button(panel, "−5").doClick()
        assertTrue(labels(panel).contains("Bias +5 RPS"))
        controls.replies[0](ack(5))
        controls.replies[1](ack(10))
        controls.replies[2](ack(5))
        assertTrue(labels(panel).contains("Bias +5 RPS"))
        assertEquals(3, annotations.size)
    }

    fun testResetSendsTheNegativeOfTheShownBiasOnlyOnce() {
        val (panel, controls) = active()
        panel.onHeartbeat(15)
        button(panel, "Reset").doClick()
        button(panel, "Reset").doClick()
        assertEquals(listOf(-15), controls.sent)
        assertTrue(labels(panel).contains("Bias 0 RPS"))
        assertFalse(button(panel, "Reset").isEnabled)
    }

    fun testNegativeBiasShowsWithATrueMinus() {
        val (panel, _) = active()
        button(panel, "−10").doClick()
        assertTrue(labels(panel).contains("Bias −10 RPS"))
    }

    fun testRejectedCommandRevertsTheBadgeAndShowsTheReason() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        controls.replies[0](ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "invalid_argument", message = "amount must be a non-zero integer")))
        assertTrue(labels(panel).contains("Bias 0 RPS"))
        assertTrue(labels(panel).contains("gg rejected a command: invalid_argument — amount must be a non-zero integer"))
        assertTrue(annotations.isEmpty())
    }

    fun testFailedCommandRevertsTheBadgeAndShowsWhy() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        controls.replies[0](ControlReply.Failed("Broken pipe"))
        assertTrue(labels(panel).contains("Bias 0 RPS"))
        assertTrue(labels(panel).contains("Command failed: Broken pipe"))
    }

    fun testNextSuccessfulCommandClearsTheErrorNote() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        controls.replies[0](ControlReply.Failed("Broken pipe"))
        button(panel, "+5").doClick()
        controls.replies[1](ack(5))
        assertFalse(labels(panel).any { it.startsWith("Command failed") })
    }

    fun testHeartbeatBiasCorrectsTheBadge() {
        val (panel, _) = active()
        panel.onHeartbeat(20)
        assertTrue(labels(panel).contains("Bias +20 RPS"))
    }

    fun testControlsDisableOnceStopIsRequested() {
        val (panel, controls) = active()
        panel.onHeartbeat(10)
        panel.onStopRequested()
        assertFalse(button(panel, "+5").isEnabled)
        assertFalse(button(panel, "Reset").isEnabled)
        button(panel, "+5").doClick()
        assertTrue(controls.sent.isEmpty())
    }

    fun testControlsDisableWhenTheRunEnds() {
        val (panel, _) = active()
        panel.onEnded()
        assertFalse(button(panel, "+10").isEnabled)
    }

    private fun markReply(label: String, at: Double) =
        ControlReply.MarkRecorded(HeartbeatPayload(event = "mark", label = label, elapsedSeconds = at))

    private fun lastMarkLabel(panel: LiveControlPanel): JBLabel? =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).firstOrNull { it.text?.startsWith("Last mark") == true }

    fun testMarkButtonFollowsTheMarkCapability() {
        val (panel, _) = active()
        assertTrue(button(panel, "Mark…").isEnabled)
        panel.onStopRequested()
        assertFalse(button(panel, "Mark…").isEnabled)

        val biasOnly = panel()
        biasOnly.reset()
        biasOnly.onStarted(listOf("control.bias"))
        assertFalse(button(biasOnly, "Mark…").isEnabled)
        assertFalse(button(panel(), "Mark…").isEnabled)
    }

    fun testMarkSendsTheCleanedLabel() {
        val (panel, controls) = active()
        nextLabel = "  cache-warm \n"
        button(panel, "Mark…").doClick()
        assertEquals(listOf("cache-warm"), controls.marksSent)
    }

    fun testCancelledOrBlankPromptSendsNothing() {
        val (panel, controls) = active()
        nextLabel = null
        button(panel, "Mark…").doClick()
        nextLabel = "   "
        button(panel, "Mark…").doClick()
        assertTrue(controls.marksSent.isEmpty())
    }

    fun testRecordedMarkUsesGgsTimeAndShowsAsTheLastMark() {
        val (panel, controls) = active()
        nextLabel = "cache-warm"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply("cache-warm", 18.4))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.Mark("cache-warm", 18.4)), annotations)
        assertEquals("Last mark: “cache-warm” at 0:18 · 1 mark", lastMarkLabel(panel)!!.text)
    }

    fun testSecondMarkUpdatesTheLabelAndTheTooltipListsEveryMark() {
        val (panel, controls) = active()
        nextLabel = "cache-warm"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply("cache-warm", 18.4))
        nextLabel = "canary-deploy"
        button(panel, "Mark…").doClick()
        controls.markReplies[1](markReply("canary-deploy", 58.1))
        val label = lastMarkLabel(panel)!!
        assertEquals("Last mark: “canary-deploy” at 0:58 · 2 marks", label.text)
        assertTrue(label.toolTipText.contains("0:18") && label.toolTipText.contains("cache-warm"))
        assertTrue(label.toolTipText.contains("0:58") && label.toolTipText.contains("canary-deploy"))
    }

    fun testLongLabelIsShortenedInTheLabelButFullInTheTooltip() {
        val (panel, controls) = active()
        val long = "x".repeat(120)
        nextLabel = long
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply(long, 5.0))
        val label = lastMarkLabel(panel)!!
        assertTrue(label.text, label.text.contains("“" + "x".repeat(40) + "…”"))
        assertTrue(label.toolTipText.contains(long))
    }

    fun testTooltipEscapesHtmlInLabels() {
        val (panel, controls) = active()
        nextLabel = "<b>boom</b>"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply("<b>boom</b>", 5.0))
        assertTrue(lastMarkLabel(panel)!!.toolTipText.contains("&lt;b&gt;boom&lt;/b&gt;"))
    }

    fun testRejectedMarkShowsTheReasonAndRecordsNothing() {
        val (panel, controls) = active()
        nextLabel = "x"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "invalid_argument", message = "label must be a non-empty string")))
        assertTrue(labels(panel).contains("gg rejected a command: invalid_argument — label must be a non-empty string"))
        assertTrue(annotations.isEmpty())
        assertNull(lastMarkLabel(panel)?.takeIf { it.isVisible })
    }

    fun testFailedMarkShowsWhy() {
        val (panel, controls) = active()
        nextLabel = "x"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](ControlReply.Failed("Broken pipe"))
        assertTrue(labels(panel).contains("Command failed: Broken pipe"))
    }

    fun testRunEndingWhileThePromptIsOpenSendsNothingAndSaysSo() {
        val (panel, controls) = active()
        nextLabel = "late"
        duringPrompt = { panel.onEnded() }
        button(panel, "Mark…").doClick()
        assertTrue(controls.marksSent.isEmpty())
        assertTrue(labels(panel).contains("The run ended before the mark could be sent"))
    }

    fun testHeartbeatsDoNotDisturbTheLastMark() {
        val (panel, controls) = active()
        nextLabel = "cache-warm"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply("cache-warm", 18.4))
        panel.onHeartbeat(5)
        panel.onHeartbeat(10)
        assertEquals("Last mark: “cache-warm” at 0:18 · 1 mark", lastMarkLabel(panel)!!.text)
    }

    fun testNewRunClearsTheMarksAndIgnoresLateReplies() {
        val (panel, controls) = active()
        nextLabel = "old"
        button(panel, "Mark…").doClick()
        panel.reset()
        controls.markReplies[0](markReply("old", 3.0))
        assertTrue(annotations.isEmpty())
        assertNull(lastMarkLabel(panel)?.takeIf { it.isVisible })
    }

    private fun layoutAll(c: Container) {
        c.doLayout()
        c.components.forEach { if (it is Container) layoutAll(it) }
    }

    private fun preferredHeightAt(panel: LiveControlPanel, width: Int): Int {
        repeat(2) {
            panel.size = Dimension(width, 200)
            layoutAll(panel)
        }
        return panel.preferredSize.height
    }

    fun testControlsWrapOntoMoreRowsWhenTheStripIsNarrow() {
        val (panel, _) = active()
        assertTrue(preferredHeightAt(panel, 480) > preferredHeightAt(panel, 1100))
    }

    fun testReplyFromAnEarlierRunIsIgnored() {
        val (panel, controls) = active()
        button(panel, "+10").doClick()
        panel.reset()
        controls.replies[0](ack(10))
        assertTrue(annotations.isEmpty())
        assertTrue(labels(panel).contains("Bias —"))
    }
}
