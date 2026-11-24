package dev.gopherglide.ggplugin.execution.ui

import com.google.gson.Gson
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.execution.control.StopPhase
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import javax.swing.JButton

class GopherGlideRunPanelRoutingTest : BasePlatformTestCase() {
    private val gson = Gson()

    private fun parse(json: String): HeartbeatPayload = gson.fromJson(json, HeartbeatPayload::class.java)

    private fun fixture(event: String, id: String? = null): HeartbeatPayload =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v130-control.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse).first { it.event == event && (id == null || it.id == id) }

    private val noControls = object : RunControls {
        override fun requestStop() {}
        override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) {}
        override fun sendMark(label: String, elapsedSeconds: Double?, onReply: (ControlReply) -> Unit) {}
        override fun sendHold(rps: Int, onReply: (ControlReply) -> Unit) {}
        override fun sendRelease(onReply: (ControlReply) -> Unit) {}
        override fun startAutopilot(mode: String, maxP99Ms: Int?, maxErrorRate: Double?, onReply: (ControlReply) -> Unit) {}
        override fun stopAutopilot(onReply: (ControlReply) -> Unit) {}
        override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {}
    }

    private fun runningPanel() = GopherGlideRunPanel().also { it.onProcessStarted(noControls) }

    private fun labels(panel: GopherGlideRunPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).map { it.text.orEmpty() }

    fun testCommandErrorKeepsTheRunGoing() {
        val panel = runningPanel()
        panel.update(fixture("error"))
        assertTrue(panel.isRunning())
    }

    fun testFatalErrorEndsTheRun() {
        val panel = runningPanel()
        panel.update(parse("""{"event":"error","message":"engine exited with error: boom"}"""))
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).any { it.startsWith("✗ ERROR") })
    }

    fun testAckAndMarkAreIgnored() {
        val panel = runningPanel()
        panel.update(fixture("ack", "b_1"))
        panel.update(fixture("mark"))
        assertTrue(panel.isRunning())
    }

    fun testStoppedEndsTheRunAndShowsTheSnapshotLine() {
        val panel = runningPanel()
        panel.update(fixture("stopped"))
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).any { it.startsWith("■ STOPPED — ✓ Snapshot saved") })
    }

    fun testStoppedWithGenericMessageShowsPlainStopped() {
        val panel = runningPanel()
        panel.update(parse("""{"event":"stopped","message":"Load test completed"}"""))
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).contains("■ STOPPED"))
    }

    private fun heartbeat(): HeartbeatPayload =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v130-natural.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse).first { it.event == "heartbeat" }

    private fun v150(name: String = "stream-v150-autopilot.ndjson"): List<HeartbeatPayload> =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse)

    private fun button(panel: GopherGlideRunPanel, text: String): JButton =
        UIUtil.findComponentsOfType(panel, JButton::class.java).first { it.text == text }

    fun testHeartbeatElapsedTimeReplacesTheLocalClock() {
        val panel = runningPanel()
        panel.update(v150().first { it.event == "started" })
        panel.update(parse("""{"event":"heartbeat","elapsed_s":423.4,"stage":1,"total_stages":1}"""))
        assertTrue(labels(panel).contains("07:03"))
    }

    fun testHeartbeatWithoutElapsedTimeKeepsTheLocalClock() {
        val panel = runningPanel()
        panel.update(heartbeat())
        assertTrue(labels(panel).contains("00:00"))
    }

    fun testAutopilotInTheStreamLocksAndUnlocksTheBiasSteppers() {
        val panel = runningPanel()
        val stream = v150()
        panel.update(stream.first { it.event == "started" })
        assertTrue(button(panel, "+5").isEnabled)
        panel.update(stream.first { it.autopilot != null })
        assertFalse(button(panel, "+5").isEnabled)
        panel.update(stream.last { it.event == "autopilot" })
        panel.update(stream.last { it.event == "heartbeat" })
        assertTrue(button(panel, "+5").isEnabled)
    }

    fun testAutopilotEventsDoNotEndTheRun() {
        val panel = runningPanel()
        v150("stream-v150-find-ceiling.ndjson").filter { it.event == "autopilot" || it.event == "mark" }.forEach { panel.update(it) }
        assertTrue(panel.isRunning())
    }

    private fun strip(panel: GopherGlideRunPanel) = UIUtil.findComponentsOfType(panel, ErrorMixStrip::class.java).single()

    private fun tooltips(panel: GopherGlideRunPanel) =
        UIUtil.findComponentsOfType(panel, javax.swing.JComponent::class.java).mapNotNull { it.toolTipText }

    fun testFindCeilingRunShowsThePillWhileSteppingAndTheCeilingWhenDone() {
        val panel = runningPanel()
        val stream = v150("stream-v150-find-ceiling.ndjson")
        val steppingAt = stream.indexOfFirst { it.event == "heartbeat" && it.autopilot?.state == "stepping" }
        stream.take(steppingAt + 1).forEach { panel.update(it) }
        assertTrue(labels(panel).any { it.startsWith("⚡ Autopilot: Find Ceiling · Stepping") })
        stream.drop(steppingAt + 1).forEach { panel.update(it) }
        assertTrue(labels(panel).any { it.startsWith("✓ FINISHED — ⚡ Ceiling found: 28 RPS") })
        assertFalse(panel.isRunning())
    }

    fun testTheAutopilotsOwnMarksAppearAsTheLastMark() {
        val panel = runningPanel()
        v150("stream-v150-find-ceiling.ndjson").forEach { panel.update(it) }
        val last = labels(panel).first { it.startsWith("Last mark") }
        assertTrue(last, last.startsWith("Last mark: “⚡ Autopilot: ceiling confirmed at 28 RPS”"))
        assertTrue(last, last.endsWith("· 2 marks"))
    }

    fun testAMarkThatAnswersOneOfOurCommandsIsLeftToTheCommandReply() {
        val panel = runningPanel()
        panel.update(v150().first { it.event == "started" })
        panel.update(v150().first { it.event == "mark" && it.id == "m_1" })
        assertTrue(labels(panel).none { it.startsWith("Last mark") && it.isNotBlank() })
    }

    fun testWindowHealthExplainsTheCardsInATooltip() {
        val panel = runningPanel()
        panel.update(v150("stream-v150-errors.ndjson").first { it.event == "started" })
        panel.update(v150("stream-v150-errors.ndjson").first { it.event == "heartbeat" })
        val tips = tooltips(panel)
        assertTrue(tips.toString(), tips.any { it.startsWith("Cumulative: 66.67% · Last 1s: 66.67% (60 samples)") })
        assertTrue(tips.toString(), tips.any { it.startsWith("Last 1s P99:") })
    }

    fun testErrorStripShowsRecentFailuresOnAV15Run() {
        val panel = runningPanel()
        assertFalse(strip(panel).isVisible)
        panel.update(v150("stream-v150-errors.ndjson").first { it.event == "heartbeat" })
        assertTrue(strip(panel).isVisible)
        assertTrue(labels(panel).any { it.startsWith("▲ Recent errors (10s): 5xx 20 · 4xx 20") })
    }

    fun testErrorStripStaysAwayOnAnOlderBinary() {
        val panel = runningPanel()
        panel.update(heartbeat())
        assertFalse(strip(panel).isVisible)
        assertTrue(tooltips(panel).none { it.startsWith("Cumulative:") })
    }

    fun testANewRunClearsTheStripAndTheTooltips() {
        val panel = runningPanel()
        panel.update(v150("stream-v150-errors.ndjson").first { it.event == "heartbeat" })
        panel.reset()
        assertFalse(strip(panel).isVisible)
        assertTrue(tooltips(panel).none { it.startsWith("Cumulative:") })
    }

    fun testUserStopOfAnAutopilotRunAddsNoOutcomeLine() {
        val panel = runningPanel()
        panel.update(v150().first { it.event == "started" })
        panel.update(parse("""{"event":"stopped","mode":"slo-guard","outcome":"stopped","message":"✓ Snapshot saved → /x.snap"}"""))
        assertTrue(labels(panel).contains("■ STOPPED — ✓ Snapshot saved → /x.snap"))
    }

    fun testFinishedRunWithoutAutopilotShowsNoOutcome() {
        val panel = runningPanel()
        panel.update(parse("""{"event":"finished","message":"Load test completed"}"""))
        assertTrue(labels(panel).contains("✓ FINISHED — Traffic simulation completed"))
    }

    fun testFinishedAfterInterruptedKeepsTheStoppedStatus() {
        val panel = runningPanel()
        panel.update(parse("""{"event":"interrupted","message":"Run interrupted by signal"}"""))
        panel.update(parse("""{"event":"finished","message":"Load test completed"}"""))
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).contains("■ STOPPED — Run interrupted by signal"))
        assertFalse(labels(panel).any { it.startsWith("✓ FINISHED") })
    }

    fun testStopPhasesShowInTheStatusAndTurnStopIntoForceStop() {
        val panel = runningPanel()
        assertFalse(panel.isStopping())
        panel.showStopPhase(StopPhase.AWAITING_ACK)
        assertTrue(panel.isStopping())
        assertTrue(labels(panel).contains("■ Stopping…"))
        panel.showStopPhase(StopPhase.FINALIZING)
        assertTrue(labels(panel).contains("■ Stopping — saving snapshot…"))
        panel.showStopPhase(StopPhase.INTERRUPTING)
        assertTrue(labels(panel).contains("■ Stopping — interrupting…"))
        panel.showStopPhase(StopPhase.NO_ACK_INTERRUPTING)
        assertTrue(labels(panel).contains("■ Stopping — gg did not respond, interrupting…"))
    }

    fun testHeartbeatDuringStopDoesNotOverwriteTheStoppingStatus() {
        val panel = runningPanel()
        panel.showStopPhase(StopPhase.FINALIZING)
        panel.update(heartbeat())
        assertTrue(labels(panel).contains("■ Stopping — saving snapshot…"))
    }

    fun testStoppingEndsWhenTheRunDoes() {
        val panel = runningPanel()
        panel.showStopPhase(StopPhase.FINALIZING)
        panel.update(fixture("stopped"))
        assertFalse(panel.isStopping())
    }

    fun testStopPhaseIsIgnoredWhenNothingIsRunning() {
        val panel = GopherGlideRunPanel()
        panel.showStopPhase(StopPhase.FINALIZING)
        assertFalse(panel.isStopping())
    }

    fun testProcessExitAfterStoppedKeepsTheStoppedStatus() {
        val panel = runningPanel()
        panel.update(fixture("stopped"))
        panel.onProcessTerminated(0)
        assertTrue(labels(panel).any { it.startsWith("■ STOPPED") })
    }
}
