package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.mcp.DaemonTestSupport.annotations
import dev.gopherglide.ggplugin.mcp.DaemonTestSupport.labels

class PluginBridgeTest : BasePlatformTestCase() {
    private val daemon = FakeDaemon()
    private val panel = GopherGlideRunPanel()
    private var ended = 0
    private var queuedNotices = 0
    private var forced = 0

    private val host = object : BridgeHost {
        override fun panelIfFree(): GopherGlideRunPanel? = if (panel.isRunning()) null else panel
        override fun runEnded() { ended++ }
        override fun agentRunQueued() { queuedNotices++ }
        override fun edt(action: () -> Unit) = action()
    }

    private val bridge by lazy { PluginBridge(daemon.info, host, PluginHttp(daemon.info), java.util.concurrent.Executor { it.run() }) { forced++ } }

    override fun tearDown() {
        try {
            bridge.close()
            daemon.close()
        } finally {
            super.tearDown()
        }
    }

    private fun feed(events: List<PluginEvent>) = events.forEach(bridge::onEvent)

    fun testAnAgentsRunIsShownInTheFreePanelWithItsOriginAndSteers() {
        feed(DaemonTestSupport.agentRun().takeWhile { !it.isTerminal })
        assertTrue(panel.isRunning())
        assertTrue(labels(panel).contains("🤖 STARTED BY AI AGENT"))
        val notes = annotations(panel)
        assertTrue(notes.any { it is RunAnnotation.Mark && it.label == "agent-mark" && it.source == AnnotationSource.AGENT })
        assertTrue(notes.any { it is RunAnnotation.BiasChange && it.amount == 5 && it.cumulative == 5 && it.source == AnnotationSource.AGENT })
        assertTrue(notes.any { it is RunAnnotation.HoldChange && it.rps == 20 && it.source == AnnotationSource.AGENT })
        assertTrue(notes.any { it is RunAnnotation.HoldChange && it.rps == null && it.source == AnnotationSource.AGENT })
    }

    fun testTheAgentsMarkShowsAsTheLastMarkWithItsBadge() {
        feed(DaemonTestSupport.agentRun().takeWhile { !it.isTerminal })
        assertTrue(labels(panel).any { it.startsWith("Last mark: “🤖 agent-mark”") })
    }

    fun testTheWholeRunEndsTheSessionAndRefreshesSnaps() {
        feed(DaemonTestSupport.agentRun())
        assertFalse(panel.isRunning())
        assertTrue(ended >= 1)
        assertFalse(bridge.hasActiveRuns())
    }

    fun testAReplayAfterAReconnectDoesNotDuplicateAnything() {
        val run = DaemonTestSupport.agentRun().takeWhile { !it.isTerminal }
        feed(run)
        val before = annotations(panel).size
        feed(run)
        assertEquals(before, annotations(panel).size)
    }

    fun testARunWaitsWhileThePanelIsBusyAndTakesOverWhenItIsFree() {
        daemon.startReply = """{"session_id":"sim_user1","origin":"user","state":"starting"}"""
        val user = bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user")))
        assertTrue(panel.isRunning())

        val agent = DaemonTestSupport.agentRun()
        feed(agent.takeWhile { it.name != "ack" })
        assertEquals(1, queuedNotices)
        assertTrue(labels(panel).none { it.contains("STARTED BY AI AGENT") })
        assertTrue(bridge.hasActiveRuns())

        feed(listOf(DaemonTestSupport.stopped("sim_user1", "user")))
        assertTrue(user.ended)
        assertTrue(labels(panel).contains("🤖 STARTED BY AI AGENT"))
        assertTrue(annotations(panel).any { it is RunAnnotation.Mark && it.label == "agent-mark" })
    }

    fun testAQueuedRunThatEndsBeforeItsTurnIsDropped() {
        bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user")))
        feed(DaemonTestSupport.agentRun())
        feed(listOf(DaemonTestSupport.stopped("sim_user1", "user")))
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).none { it.contains("STARTED BY AI AGENT") })
        assertFalse(bridge.hasActiveRuns())
    }

    fun testAQueuedRunTakesOverWhenADirectRunEnds() {
        val direct = GopherGlideRunPanel()
        direct.onProcessStarted(object : dev.gopherglide.ggplugin.execution.control.RunControls {
            override fun requestStop() {}
            override fun sendBias(amount: Int, onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun sendMark(label: String, elapsedSeconds: Double?, onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun sendHold(rps: Int, onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun sendRelease(onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun startAutopilot(mode: String, maxP99Ms: Int?, maxErrorRate: Double?, onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun stopAutopilot(onReply: (dev.gopherglide.ggplugin.execution.control.ControlReply) -> Unit) {}
            override fun loadBinaryInfo(onLoaded: (dev.gopherglide.ggplugin.services.InstalledBinary?, dev.gopherglide.ggplugin.services.GopherGlideVersion?) -> Unit) {}
        })
        val busyHost = object : BridgeHost by host {
            override fun panelIfFree(): GopherGlideRunPanel? = if (direct.isRunning()) null else panel
        }
        val local = PluginBridge(daemon.info, busyHost, PluginHttp(daemon.info), java.util.concurrent.Executor { it.run() })
        try {
            DaemonTestSupport.agentRun().takeWhile { !it.isTerminal }.forEach(local::onEvent)
            assertFalse(panel.isRunning())
            direct.onProcessTerminated(0)
            local.panelFreed()
            assertTrue(labels(panel).contains("🤖 STARTED BY AI AGENT"))
        } finally {
            local.close()
        }
    }

    fun testUserEventsGoOnlyToTheRunStartedHere() {
        feed(listOf(DaemonTestSupport.started("sim_other", "user")))
        assertFalse(panel.isRunning())
        bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user"), DaemonTestSupport.heartbeat("sim_user1", "user", 65.0)))
        assertTrue(labels(panel).contains("01:05"))
        assertTrue(bridge.userRunActive())
    }

    fun testAFinishedUserRunFreesTheBridge() {
        bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user"), DaemonTestSupport.stopped("sim_user1", "user")))
        assertFalse(bridge.userRunActive())
        assertFalse(bridge.hasActiveRuns())
    }

    fun testEventsForAnAgentSessionThatNeverStartedAreIgnored() {
        feed(listOf(DaemonTestSupport.heartbeat("sim_ghost", "agent")))
        assertFalse(panel.isRunning())
        assertFalse(bridge.hasActiveRuns())
    }

    fun testClosingTheBridgeFailsEveryRunItHas() {
        feed(DaemonTestSupport.agentRun().takeWhile { !it.isTerminal })
        bridge.close()
        assertFalse(panel.isRunning())
        assertTrue(labels(panel).any { it == "✗ EXITED (code 1) — The shared gg daemon stopped" })
    }

    fun testAUserRunsOwnRepliesAreNotShownAsSomeoneElsesChanges() {
        val session = bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user")))
        session.sendHold(15) { }
        val id = JsonParser.parseString(daemon.controlBodies.last()).asJsonObject["id"].asString
        feed(listOf(DaemonTestSupport.event("ack", "sim_user1", "user", dev.gopherglide.ggplugin.execution.HeartbeatPayload(event = "ack", id = id, command = "hold", holdRps = 15, source = "user"))))
        assertTrue(annotations(panel).none { it is RunAnnotation.HoldChange })
    }

    fun testAnAgentSteeringTheUsersRunIsShownOnTheChart() {
        bridge.startUserRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject, panel)
        feed(listOf(DaemonTestSupport.started("sim_user1", "user")))
        feed(listOf(DaemonTestSupport.event("ack", "sim_user1", "user", dev.gopherglide.ggplugin.execution.HeartbeatPayload(event = "ack", id = "c1", command = "hold", holdRps = 30, source = "agent", elapsedSeconds = 4.0))))
        assertTrue(annotations(panel).any { it is RunAnnotation.HoldChange && it.rps == 30 && it.source == AnnotationSource.AGENT })
    }
}
