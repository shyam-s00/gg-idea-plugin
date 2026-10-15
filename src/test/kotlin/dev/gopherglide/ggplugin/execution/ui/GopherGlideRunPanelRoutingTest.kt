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

class GopherGlideRunPanelRoutingTest : BasePlatformTestCase() {
    private val gson = Gson()

    private fun parse(json: String): HeartbeatPayload = gson.fromJson(json, HeartbeatPayload::class.java)

    private fun fixture(event: String, id: String? = null): HeartbeatPayload =
        checkNotNull(javaClass.getResourceAsStream("/lcp/stream-v130-control.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse).first { it.event == event && (id == null || it.id == id) }

    private val noControls = object : RunControls {
        override fun requestStop() {}
        override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) {}
        override fun sendMark(label: String, onReply: (ControlReply) -> Unit) {}
        override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {}
        override fun upgradeBinary(onDone: (Throwable?) -> Unit) {}
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
        checkNotNull(javaClass.getResourceAsStream("/lcp/stream-v130-natural.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse).first { it.event == "heartbeat" }

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
