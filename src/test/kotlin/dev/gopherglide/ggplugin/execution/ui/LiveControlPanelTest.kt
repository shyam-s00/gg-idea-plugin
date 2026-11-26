package dev.gopherglide.ggplugin.execution.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.AutopilotBrief
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.autopilot.SloLimits
import dev.gopherglide.ggplugin.execution.control.BinaryActions
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import java.math.BigDecimal
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JButton

class LiveControlPanelTest : BasePlatformTestCase() {
    private class FakeControls : RunControls {
        val sent = mutableListOf<Int>()
        val replies = mutableListOf<(ControlReply) -> Unit>()
        val marksSent = mutableListOf<String>()
        val markTimes = mutableListOf<Double?>()
        val markReplies = mutableListOf<(ControlReply) -> Unit>()
        override fun requestStop() {}
        override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) {
            sent += amount
            replies += onReply
        }
        override fun sendMark(label: String, elapsedSeconds: Double?, onReply: (ControlReply) -> Unit) {
            marksSent += label
            markTimes += elapsedSeconds
            markReplies += onReply
        }
        var steerSupported = false
        override val agentSteerSupported: Boolean get() = steerSupported
        val steerCalls = mutableListOf<Boolean>()
        val steerReplies = mutableListOf<(ControlReply) -> Unit>()
        override fun setAgentSteer(allow: Boolean, onReply: (ControlReply) -> Unit) {
            steerCalls += allow
            steerReplies += onReply
        }
        val holdsSent = mutableListOf<Int>()
        val holdReplies = mutableListOf<(ControlReply) -> Unit>()
        var releases = 0
        val releaseReplies = mutableListOf<(ControlReply) -> Unit>()
        override fun sendHold(rps: Int, onReply: (ControlReply) -> Unit) {
            holdsSent += rps
            holdReplies += onReply
        }
        override fun sendRelease(onReply: (ControlReply) -> Unit) {
            releases++
            releaseReplies += onReply
        }
        class GuardStart(val mode: String, val maxP99Ms: Int?, val maxErrorRate: Double?)
        val guards = mutableListOf<GuardStart>()
        val autopilotReplies = mutableListOf<(ControlReply) -> Unit>()
        var autopilotStops = 0
        override fun startAutopilot(mode: String, maxP99Ms: Int?, maxErrorRate: Double?, onReply: (ControlReply) -> Unit) {
            guards += GuardStart(mode, maxP99Ms, maxErrorRate)
            autopilotReplies += onReply
        }
        override fun stopAutopilot(onReply: (ControlReply) -> Unit) {
            autopilotStops++
            autopilotReplies += onReply
        }
        val infoLoaders = mutableListOf<(InstalledBinary?, GopherGlideVersion?) -> Unit>()
        override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) { infoLoaders += onLoaded }
    }

    private class FakeBinaryActions : BinaryActions {
        val statusLoaders = mutableListOf<(InstalledBinary?, GopherGlideVersion?) -> Unit>()
        val installs = mutableListOf<(Throwable?) -> Unit>()
        val listeners = mutableListOf<() -> Unit>()
        var settingsOpened = 0
        override fun loadStatus(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) { statusLoaders += onLoaded }
        override fun install(onDone: (Throwable?) -> Unit) { installs += onDone }
        override fun openSettings() { settingsOpened++ }
        override fun onChanged(listener: () -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }
    }

    private val binary = FakeBinaryActions()

    private val all = listOf("control.bias", "control.mark", "control.stop")
    private val v15 = all + listOf("control.mark.elapsed", "control.hold", "control.autopilot")
    private val annotations = mutableListOf<RunAnnotation>()
    private var clock = 42.0

    private fun ack(bias: Int) = ControlReply.Ack(HeartbeatPayload(event = "ack", bias = bias))

    private var nextLabel: String? = null
    private var duringPrompt: () -> Unit = {}

    private var nextRate: Int? = 15
    private var suggestedRate: Int? = null
    private var nextLimits: SloLimits? = SloLimits(120, BigDecimal("0.02"))

    private fun panel(): LiveControlPanel = LiveControlPanel(
        { clock }, { annotations += it }, { duringPrompt(); nextLabel }, binary,
        { _, suggested -> suggestedRate = suggested; duringPrompt(); nextRate },
        { nextLimits }
    )

    private fun active(
        controls: FakeControls = FakeControls(),
        capabilities: List<String> = all
    ): Pair<LiveControlPanel, FakeControls> {
        val panel = panel()
        panel.reset()
        panel.bind(controls)
        panel.onStarted(capabilities)
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

    private val system12 = InstalledBinary("/usr/local/bin/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.SYSTEM)
    private val custom12 = InstalledBinary("/opt/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.CUSTOM)
    private val system13 = InstalledBinary("/usr/local/bin/gg", v13, InstalledBinary.Source.SYSTEM)

    private fun upgradeButton(panel: LiveControlPanel) = button(panel, "Upgrade gg")

    private fun visibleButtons(panel: LiveControlPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JButton::class.java).filter { shown(panel, it) }.map { it.text.orEmpty() }

    private fun idlePanel(): LiveControlPanel = panel().also { it.addNotify() }

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

    fun testCustomBinaryGetsAnOpenSettingsButtonAndNoDownload() {
        val (panel, controls) = older()
        controls.infoLoaders[0](custom12, v13)
        panel.onEnded()
        assertFalse(visibleButtons(panel).any { it.contains("Upgrade") || it.contains("Install") })
        assertTrue(labels(panel).any { it.contains("at /opt/gg") })
        button(panel, "Open Settings").doClick()
        assertEquals(1, binary.settingsOpened)
        assertTrue(binary.installs.isEmpty())
    }

    fun testSystemBinaryCanBeInstalledEvenWhileTheRunIsActive() {
        val (panel, controls) = older()
        controls.infoLoaders[0](system12, v13)
        val install = button(panel, "Install gg v1.3.0")
        assertTrue(shown(panel, install))
        assertTrue(install.isEnabled)
        install.doClick()
        assertEquals(1, binary.installs.size)
    }

    fun testSecondaryOpenSettingsLinkOpensSettings() {
        val (panel, controls) = older()
        controls.infoLoaders[0](system12, v13)
        val link = UIUtil.findComponentsOfType(panel, ActionLink::class.java).first { it.text == "Open Settings" }
        assertTrue(shown(panel, link))
        link.doClick()
        assertEquals(1, binary.settingsOpened)
    }

    fun testClickingUpgradeShowsProgressThenTheOutcome() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        panel.onEnded()
        upgradeButton(panel).doClick()
        assertEquals(1, binary.installs.size)
        assertTrue(labels(panel).any { it.endsWith("Upgrading…") })
        assertFalse(button(panel, "Upgrading…").isEnabled)
        binary.installs[0](null)
        assertTrue(labels(panel).contains("gg v1.3.0 is installed. Live Control will be available from the next run."))
        assertFalse(visibleButtons(panel).any { it.contains("Upgrade") })
    }

    fun testFailedUpgradeShowsWhyAndAllowsAnotherTry() {
        val (panel, controls) = older()
        controls.infoLoaders[0](plugin12, v13)
        panel.onEnded()
        upgradeButton(panel).doClick()
        binary.installs[0](RuntimeException("no network"))
        assertTrue(labels(panel).contains("Upgrade failed: no network"))
        assertTrue(upgradeButton(panel).isEnabled)
    }

    fun testIdlePanelStaysHiddenWhenNothingIsKnownAboutTheBinary() {
        val panel = idlePanel()
        assertEquals(1, binary.statusLoaders.size)
        assertFalse(panel.isVisible)
        binary.statusLoaders[0](null, v13)
        assertFalse(panel.isVisible)
    }

    fun testIdlePanelShowsTheNoticeForAnOlderBinaryBeforeAnyRun() {
        val panel = idlePanel()
        binary.statusLoaders[0](system12, v13)
        assertTrue(panel.isVisible)
        assertTrue(labels(panel).any { it.contains("Installed: v1.2.0 at /usr/local/bin/gg") })
        assertTrue(shown(panel, button(panel, "Install gg v1.3.0")))
        assertFalse(shown(panel, button(panel, "+5")))
    }

    fun testIdlePanelHintsAtWhatV15AddsForAV13Binary() {
        val panel = idlePanel()
        binary.statusLoaders[0](system13, v13)
        assertTrue(labels(panel).contains("gg v1.5.0 adds Autopilot, Rate Hold and click-time marks"))
    }

    fun testIdlePanelHasNoUpgradeHintForAV15Binary() {
        val panel = idlePanel()
        binary.statusLoaders[0](InstalledBinary("/usr/local/bin/gg", GopherGlideVersion(1, 5, 0), InstalledBinary.Source.SYSTEM), v13)
        assertTrue(labels(panel).contains("Live Control, Rate Hold and Autopilot available (gg v1.5.0)"))
        assertFalse(labels(panel).any { it.contains("adds Autopilot") })
    }

    fun testIdlePanelShowsAQuietLineForACurrentBinary() {
        val panel = idlePanel()
        binary.statusLoaders[0](system13, v13)
        assertTrue(panel.isVisible)
        assertTrue(labels(panel).contains("Live Control available (gg v1.3.0)"))
        assertTrue(visibleButtons(panel).none { it.isNotEmpty() })
    }

    fun testIdleStatusRefreshesWhenTheBinaryChanges() {
        val panel = idlePanel()
        binary.statusLoaders[0](system12, v13)
        binary.listeners.forEach { it() }
        assertEquals(2, binary.statusLoaders.size)
        binary.statusLoaders[1](system13, v13)
        assertTrue(labels(panel).contains("Live Control available (gg v1.3.0)"))
        assertFalse(labels(panel).any { it.contains("Installed: v1.2.0") })
    }

    fun testIdleListenerIsRemovedWhenThePanelLeavesTheScreen() {
        val panel = idlePanel()
        assertEquals(1, binary.listeners.size)
        panel.removeNotify()
        assertTrue(binary.listeners.isEmpty())
    }

    fun testIdleStatusIsIgnoredOnceARunHasStarted() {
        val panel = idlePanel()
        panel.reset()
        binary.statusLoaders[0](system12, v13)
        assertFalse(labels(panel).any { it.contains("Installed: v1.2.0") })
        binary.listeners.forEach { it() }
        assertEquals(1, binary.statusLoaders.size)
    }

    fun testInstallFromTheIdleNoticeReloadsTheStatus() {
        val panel = idlePanel()
        binary.statusLoaders[0](system12, v13)
        button(panel, "Install gg v1.3.0").doClick()
        binary.installs[0](null)
        binary.listeners.forEach { it() }
        binary.statusLoaders[1](InstalledBinary("/plugins/gg", v13, InstalledBinary.Source.PLUGIN), v13)
        assertTrue(labels(panel).contains("Live Control available (gg v1.3.0)"))
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

    fun testMarkIsStampedWithTheClickNotTheTimeTheLabelWasEntered() {
        val (panel, controls) = active(capabilities = v15)
        clock = 18.0
        nextLabel = "cache-warm"
        duringPrompt = { clock = 42.0 }
        button(panel, "Mark…").doClick()
        assertEquals(listOf<Double?>(18.0), controls.markTimes)
    }

    fun testClickTimeIsRoundedToTheMillisecond() {
        val (panel, controls) = active(capabilities = v15)
        clock = 18.23456
        nextLabel = "x"
        button(panel, "Mark…").doClick()
        assertEquals(listOf<Double?>(18.235), controls.markTimes)
    }

    fun testBinaryWithoutClickTimeMarksSendsNoTime() {
        val (panel, controls) = active()
        clock = 18.0
        nextLabel = "x"
        button(panel, "Mark…").doClick()
        assertEquals(listOf<Double?>(null), controls.markTimes)
    }

    fun testReplyWithoutATimeFallsBackToTheClickNotTheReplyMoment() {
        val (panel, controls) = active(capabilities = v15)
        clock = 10.0
        nextLabel = "x"
        duringPrompt = { clock = 30.0 }
        button(panel, "Mark…").doClick()
        clock = 31.0
        controls.markReplies[0](ControlReply.MarkRecorded(HeartbeatPayload(event = "mark")))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.Mark("x", 10.0)), annotations)
    }

    fun testGgsClampedTimeWinsOverTheClick() {
        val (panel, controls) = active(capabilities = v15)
        nextLabel = "x"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](ControlReply.MarkRecorded(HeartbeatPayload(event = "mark", label = "x", elapsedSeconds = 40.0, clamped = true)))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.Mark("x", 40.0)), annotations)
    }

    fun testMarksListChronologicallyEvenWhenRepliesArriveOutOfOrder() {
        val (panel, controls) = active(capabilities = v15)
        nextLabel = "first"
        button(panel, "Mark…").doClick()
        nextLabel = "second"
        button(panel, "Mark…").doClick()
        controls.markReplies[1](markReply("second", 58.1))
        controls.markReplies[0](markReply("first", 18.4))
        val label = lastMarkLabel(panel)!!
        assertEquals("Last mark: “second” at 0:58 · 2 marks", label.text)
        assertTrue(label.toolTipText.indexOf("first") < label.toolTipText.indexOf("second"))
    }

    fun testRunningAutopilotLocksTheBiasControlsUntilItIsDone() {
        val (panel, _) = active(capabilities = v15)
        panel.onHeartbeat(0, 17, AutopilotBrief("slo-guard", "clamped"))
        listOf("−10", "−5", "+5", "+10").forEach { assertFalse(it, button(panel, it).isEnabled) }
        assertTrue(button(panel, "Mark…").isEnabled)
        panel.onAutopilotEvent("slo-guard", "done")
        panel.onHeartbeat(0, 17, null)
        listOf("−10", "−5", "+5", "+10").forEach { assertTrue(it, button(panel, it).isEnabled) }
    }

    fun testAutopilotEventLocksTheBiasControlsBeforeTheNextHeartbeat() {
        val (panel, _) = active(capabilities = v15)
        panel.onAutopilotEvent("find-ceiling", "stepping")
        assertFalse(button(panel, "+5").isEnabled)
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

    private fun shownButtons(panel: LiveControlPanel) = visibleButtons(panel)

    private fun holdAck(rps: Int?, at: Double = 5.0, source: String? = null) =
        ControlReply.Ack(HeartbeatPayload(event = "ack", command = if (rps != null) "hold" else "release", holdRps = rps, elapsedSeconds = at, source = source))

    fun testHoldAndAutopilotControlsAreHiddenOnABinaryWithoutThem() {
        val (panel, _) = active()
        val buttons = shownButtons(panel)
        assertFalse(buttons.contains("Hold RPS"))
        assertFalse(buttons.contains("🛡️ Guard SLO…"))
        assertFalse(buttons.contains("Stop Autopilot (Take Over)"))
    }

    fun testHoldAndGuardControlsAppearOnV15() {
        val (panel, _) = active(capabilities = v15)
        val buttons = shownButtons(panel)
        assertTrue(buttons.contains("Hold RPS"))
        assertTrue(buttons.contains("🛡️ Guard SLO…"))
        assertFalse(buttons.contains("Stop Autopilot (Take Over)"))
    }

    fun testHoldAsksForARateSuggestingTheCurrentTarget() {
        val (panel, controls) = active(capabilities = v15)
        panel.onHeartbeat(0, null, null, 40)
        button(panel, "Hold RPS").doClick()
        assertEquals(40, suggestedRate)
        assertEquals(listOf(15), controls.holdsSent)
    }

    fun testCancellingTheRatePromptSendsNothing() {
        val (panel, controls) = active(capabilities = v15)
        nextRate = null
        button(panel, "Hold RPS").doClick()
        assertTrue(controls.holdsSent.isEmpty())
    }

    fun testHoldAckShowsTheBadgeAtOnceAndFlipsTheButton() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "Hold RPS").doClick()
        assertFalse(labels(panel).contains("📌 Hold 15 RPS") && shown(panel, UIUtil.findComponentsOfType(panel, JBLabel::class.java).first { it.text == "📌 Hold 15 RPS" }))
        controls.holdReplies[0](holdAck(15))
        val badge = UIUtil.findComponentsOfType(panel, JBLabel::class.java).first { it.text == "📌 Hold 15 RPS" }
        assertTrue(shown(panel, badge))
        assertTrue(shownButtons(panel).contains("Release Hold"))
        assertFalse(shownButtons(panel).contains("Hold RPS"))
    }

    fun testHoldAckRecordsAnAnnotationAtGgsTime() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "Hold RPS").doClick()
        controls.holdReplies[0](holdAck(15, at = 7.5))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.HoldChange(15, 7.5)), annotations)
    }

    fun testReleaseSendsReleaseAndClearsTheBadge() {
        val (panel, controls) = active(capabilities = v15)
        panel.onHeartbeat(0, 15)
        button(panel, "Release Hold").doClick()
        assertEquals(1, controls.releases)
        controls.releaseReplies[0](holdAck(null, at = 9.0))
        assertTrue(shownButtons(panel).contains("Hold RPS"))
        assertEquals(listOf<RunAnnotation>(RunAnnotation.HoldChange(null, 9.0)), annotations)
        val badge = UIUtil.findComponentsOfType(panel, JBLabel::class.java).first { it.text.orEmpty().startsWith("📌") }
        assertFalse(shown(panel, badge))
    }

    fun testRejectedHoldShowsWhyAndRecordsNothing() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "Hold RPS").doClick()
        controls.holdReplies[0](ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "invalid_argument", message = "rps must be an integer >= 1")))
        assertTrue(labels(panel).contains("gg rejected a command: invalid_argument — rps must be an integer >= 1"))
        assertTrue(annotations.isEmpty())
    }

    fun testHoldReplyFromAnEarlierRunIsIgnored() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "Hold RPS").doClick()
        panel.reset()
        controls.holdReplies[0](holdAck(15))
        assertTrue(annotations.isEmpty())
    }

    fun testRunEndingWhileTheRatePromptIsOpenSendsNothing() {
        val (panel, controls) = active(capabilities = v15)
        duringPrompt = { panel.onEnded() }
        button(panel, "Hold RPS").doClick()
        assertTrue(controls.holdsSent.isEmpty())
        assertTrue(labels(panel).contains("The run ended before the hold could be sent"))
    }

    fun testAHoldFromAHeartbeatShowsWithoutAnyClick() {
        val (panel, _) = active(capabilities = v15)
        panel.onHeartbeat(0, 280)
        assertTrue(shownButtons(panel).contains("Release Hold"))
    }

    fun testAutopilotShowsItsPillAndSwapsGuardForStop() {
        val (panel, _) = active(capabilities = v15)
        panel.onHeartbeat(0, 28, AutopilotBrief("find-ceiling", "stepping"))
        assertTrue(labels(panel).contains("⚡ Autopilot: Find Ceiling · Stepping (28 RPS)"))
        val buttons = shownButtons(panel)
        assertTrue(buttons.contains("Stop Autopilot (Take Over)"))
        assertFalse(buttons.contains("🛡️ Guard SLO…"))
    }

    fun testManualRateControlsAreOffWithAnExplanationWhileTheAutopilotRuns() {
        val (panel, _) = active(capabilities = v15)
        panel.onHeartbeat(0, 28, AutopilotBrief("slo-guard", "clamped"))
        assertFalse(button(panel, "+5").isEnabled)
        assertFalse(button(panel, "Release Hold").isEnabled)
        assertEquals("The autopilot owns the rate. Stop it to steer by hand.", button(panel, "+5").toolTipText)
        panel.onAutopilotEvent("slo-guard", "done")
        panel.onHeartbeat(0, 28, null)
        assertTrue(button(panel, "+5").isEnabled)
        assertTrue(button(panel, "Release Hold").isEnabled)
        assertNull(button(panel, "+5").toolTipText)
    }

    fun testStoppingTheAutopilotKeepsTheHoldVisibleUntilReleased() {
        val (panel, controls) = active(capabilities = v15)
        panel.onHeartbeat(0, 280, AutopilotBrief("slo-guard", "clamped"))
        button(panel, "Stop Autopilot (Take Over)").doClick()
        assertEquals(1, controls.autopilotStops)
        panel.onAutopilotEvent("slo-guard", "done")
        controls.autopilotReplies[0](ControlReply.Ack(HeartbeatPayload(event = "ack", command = "autopilot")))
        panel.onHeartbeat(0, 280, null)
        assertTrue(shownButtons(panel).contains("Release Hold"))
        assertTrue(button(panel, "Release Hold").isEnabled)
        assertTrue(labels(panel).contains("📌 Hold 280 RPS"))
        assertFalse(shownButtons(panel).contains("Stop Autopilot (Take Over)"))
    }

    fun testGuardStartsTheSloGuardWithTheChosenLimits() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "🛡️ Guard SLO…").doClick()
        val start = controls.guards.single()
        assertEquals("slo-guard", start.mode)
        assertEquals(120, start.maxP99Ms)
        assertEquals(0.02, start.maxErrorRate!!, 0.0)
    }

    fun testCancellingTheGuardDialogStartsNothing() {
        val (panel, controls) = active(capabilities = v15)
        nextLimits = null
        button(panel, "🛡️ Guard SLO…").doClick()
        assertTrue(controls.guards.isEmpty())
    }

    fun testRefusedGuardShowsWhy() {
        val (panel, controls) = active(capabilities = v15)
        button(panel, "🛡️ Guard SLO…").doClick()
        controls.autopilotReplies[0](ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "invalid_argument", message = "mode must be slo-guard")))
        assertTrue(labels(panel).contains("gg rejected a command: invalid_argument — mode must be slo-guard"))
    }

    fun testAutopilotControlsGoQuietWhenTheRunEnds() {
        val (panel, _) = active(capabilities = v15)
        panel.onHeartbeat(0, 28, AutopilotBrief("find-ceiling", "stepping"))
        panel.onEnded()
        assertFalse(button(panel, "Stop Autopilot (Take Over)").isEnabled)
        assertFalse(labels(panel).any { it.startsWith("⚡ Autopilot:") && shown(panel, UIUtil.findComponentsOfType(panel, JBLabel::class.java).first { l -> l.text == it }) })
    }

    fun testAutopilotMarkIsRecordedWithItsSourceAndShownAsTheLastMark() {
        val (panel, _) = active(capabilities = v15)
        panel.onUnsolicitedMark("ceiling breached at 35 RPS", 31.0, AnnotationSource.AUTOPILOT)
        assertEquals(listOf<RunAnnotation>(RunAnnotation.Mark("ceiling breached at 35 RPS", 31.0, AnnotationSource.AUTOPILOT)), annotations)
        assertEquals("Last mark: “⚡ ceiling breached at 35 RPS” at 0:31 · 1 mark", lastMarkLabel(panel)!!.text)
    }

    fun testAutopilotMarksSitInTimeOrderAmongTheUsersOwn() {
        val (panel, controls) = active(capabilities = v15)
        nextLabel = "mine"
        button(panel, "Mark…").doClick()
        controls.markReplies[0](markReply("mine", 40.0))
        panel.onUnsolicitedMark("auto", 31.0, AnnotationSource.AUTOPILOT)
        val tooltip = lastMarkLabel(panel)!!.toolTipText
        assertTrue(tooltip.indexOf("auto") < tooltip.indexOf("mine"))
        assertEquals("Last mark: “mine” at 0:40 · 2 marks", lastMarkLabel(panel)!!.text)
    }

    private fun steerBox(panel: LiveControlPanel) =
        UIUtil.findComponentsOfType(panel, com.intellij.ui.components.JBCheckBox::class.java).first { it.text == "🤖 Allow AI Steer" }

    fun testAllowAiSteerIsHiddenForARunThatCannotBeShared() {
        val (panel, _) = active(capabilities = v15)
        assertFalse(shown(panel, steerBox(panel)))
    }

    fun testAllowAiSteerAppearsOffForARunOnTheSharedDaemon() {
        val (panel, _) = active(FakeControls().apply { steerSupported = true }, v15)
        assertTrue(shown(panel, steerBox(panel)))
        assertFalse(steerBox(panel).isSelected)
    }

    fun testTickingItLetsAgentsSteerAndUntickingTakesItBack() {
        val (panel, controls) = active(FakeControls().apply { steerSupported = true }, v15)
        steerBox(panel).doClick()
        controls.steerReplies[0](ControlReply.Ack(HeartbeatPayload(event = "ack", command = "agent_steer")))
        steerBox(panel).doClick()
        controls.steerReplies[1](ControlReply.Ack(HeartbeatPayload(event = "ack", command = "agent_steer")))
        assertEquals(listOf(true, false), controls.steerCalls)
        assertFalse(steerBox(panel).isSelected)
    }

    fun testARefusedChangeRevertsTheBoxAndSaysWhy() {
        val (panel, controls) = active(FakeControls().apply { steerSupported = true }, v15)
        steerBox(panel).doClick()
        assertTrue(steerBox(panel).isSelected)
        controls.steerReplies[0](ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "unknown_session", message = "unknown session_id")))
        assertFalse(steerBox(panel).isSelected)
        assertTrue(labels(panel).contains("gg rejected a command: unknown_session — unknown session_id"))
    }

    fun testANewRunStartsWithAgentsLockedOut() {
        val (panel, _) = active(FakeControls().apply { steerSupported = true }, v15)
        steerBox(panel).doClick()
        panel.reset()
        assertFalse(steerBox(panel).isSelected)
        assertFalse(shown(panel, steerBox(panel)))
    }

    fun testTheBoxIsDisabledOnceTheRunIsOver() {
        val (panel, _) = active(FakeControls().apply { steerSupported = true }, v15)
        panel.onEnded()
        assertFalse(steerBox(panel).isEnabled)
    }
}
