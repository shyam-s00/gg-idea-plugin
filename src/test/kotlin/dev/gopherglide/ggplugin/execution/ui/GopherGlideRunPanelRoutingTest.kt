package dev.gopherglide.ggplugin.execution.ui

import com.google.gson.Gson
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.HeartbeatPayload

class GopherGlideRunPanelRoutingTest : BasePlatformTestCase() {
    private val gson = Gson()

    private fun parse(json: String): HeartbeatPayload = gson.fromJson(json, HeartbeatPayload::class.java)

    private fun fixture(event: String, id: String? = null): HeartbeatPayload =
        checkNotNull(javaClass.getResourceAsStream("/lcp/stream-v130-control.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map(::parse).first { it.event == event && (id == null || it.id == id) }

    private fun runningPanel() = GopherGlideRunPanel().also { it.onProcessStarted { } }

    private fun labels(panel: GopherGlideRunPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).map { it.text }

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

    fun testProcessExitAfterStoppedKeepsTheStoppedStatus() {
        val panel = runningPanel()
        panel.update(fixture("stopped"))
        panel.onProcessTerminated(0)
        assertTrue(labels(panel).any { it.startsWith("■ STOPPED") })
    }
}
