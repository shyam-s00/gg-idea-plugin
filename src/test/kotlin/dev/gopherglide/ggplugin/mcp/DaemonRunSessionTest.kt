package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.mcp.DaemonTestSupport.labels
import java.util.concurrent.Executor

class DaemonRunSessionTest : BasePlatformTestCase() {
    private val daemon = FakeDaemon()
    private val direct = Executor { it.run() }
    private val ended = mutableListOf<DaemonRunSession>()
    private var forced = 0

    override fun tearDown() {
        try {
            daemon.close()
        } finally {
            super.tearDown()
        }
    }

    private fun session(panel: GopherGlideRunPanel, origin: AnnotationSource = AnnotationSource.USER) =
        DaemonRunSession(panel, PluginHttp(daemon.info), origin, { ended += it }, { forced++ }, direct, { it() }, 600)

    private fun running(origin: AnnotationSource = AnnotationSource.USER): Pair<DaemonRunSession, GopherGlideRunPanel> {
        val panel = GopherGlideRunPanel()
        val session = session(panel, origin)
        session.adopt("sim_1")
        session.onEvent(DaemonTestSupport.started("sim_1", origin.wire))
        return session to panel
    }

    private fun sent(index: Int = daemon.controlBodies.size - 1) = JsonParser.parseString(daemon.controlBodies[index]).asJsonObject

    fun testARefusedStartEndsTheRunWithTheDaemonsOwnWords() {
        daemon.startReply = """{"event":"error","message":"1 user simulation(s) already running","reason":"session_limit"}"""
        val panel = GopherGlideRunPanel()
        val session = session(panel)
        session.begin()
        session.startRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject)
        assertTrue(labels(panel).any { it.startsWith("✗ EXITED (code 1) — 1 user simulation(s) already running") })
        assertFalse(panel.isRunning())
        assertEquals(listOf(session), ended)
    }

    fun testAnAcceptedStartRecordsTheSessionAndForwardsTheRequestUntouched() {
        val panel = GopherGlideRunPanel()
        val session = session(panel)
        session.begin()
        session.startRun(JsonParser.parseString("""{"http_path":"/a.http","profile":"smoke","snap":true}""").asJsonObject)
        assertEquals("sim_user1", session.sessionId)
        assertEquals("""{"http_path":"/a.http","profile":"smoke","snap":true}""", daemon.startBodies.single())
        assertTrue(panel.isRunning())
    }

    fun testAnUnreachableDaemonEndsTheRunWithAClearMessage() {
        val gone = FakeDaemon().also { it.close() }
        val panel = GopherGlideRunPanel()
        val session = DaemonRunSession(panel, PluginHttp(gone.info), AnnotationSource.USER, { ended += it }, {}, direct, { it() }, 600)
        session.begin()
        session.startRun(JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject)
        assertTrue(labels(panel).any { it.contains("Could not reach the shared gg daemon") })
        assertFalse(panel.isRunning())
    }

    fun testEveryCommandCarriesTheSessionId() {
        val (session, _) = running()
        session.sendBias(5) { }
        session.sendMark("deploy", 12.5) { }
        session.sendHold(40) { }
        assertEquals(listOf("sim_1", "sim_1", "sim_1"), daemon.controlBodies.indices.map { sent(it)["session_id"].asString })
        assertEquals("bias", sent(0)["command"].asString)
        assertEquals(12.5, sent(1)["elapsed_s"].asDouble, 0.0)
        assertEquals(40, sent(2)["rps"].asInt)
    }

    fun testAnAckCompletesTheCommandAndARefusalIsReportedAsOne() {
        val (session, _) = running()
        val replies = mutableListOf<ControlReply>()
        session.sendHold(15) { replies += it }
        daemon.controlReply = { """{"event":"error","message":"autopilot owns the rate","reason":"autopilot_active"}""" }
        session.sendBias(5) { replies += it }
        assertTrue(replies[0] is ControlReply.Ack)
        assertEquals("autopilot_active", (replies[1] as ControlReply.Rejected).payload.reason)
    }

    fun testOnlyIdsItSentAreItsOwn() {
        val (session, _) = running()
        session.sendBias(5) { }
        val id = sent()["id"].asString
        assertTrue(session.ownsCommand(id))
        assertFalse(session.ownsCommand("c1"))
    }

    fun testACommandBeforeTheSessionIsKnownFailsInsteadOfGuessing() {
        val panel = GopherGlideRunPanel()
        val session = session(panel)
        session.begin()
        var reply: ControlReply? = null
        session.sendBias(5) { reply = it }
        assertTrue(reply is ControlReply.Failed)
        assertTrue(daemon.controlBodies.isEmpty())
    }

    fun testTheSessionIdIsLearnedFromTheFirstEventWhenTheStartAnswerIsSlow() {
        val panel = GopherGlideRunPanel()
        val session = session(panel)
        session.begin()
        session.onEvent(DaemonTestSupport.started("sim_early", "user"))
        assertEquals("sim_early", session.sessionId)
    }

    fun testATerminalEventEndsTheRunOnceAndLaterEventsAreIgnored() {
        val (session, panel) = running()
        session.onEvent(DaemonTestSupport.stopped("sim_1", "user"))
        assertFalse(panel.isRunning())
        assertEquals(listOf(session), ended)
        session.onEvent(DaemonTestSupport.heartbeat("sim_1", "user"))
        assertEquals(1, ended.size)
    }

    fun testStopAsksTheDaemonAndShowsTheSnapshotBeingSaved() {
        val (session, panel) = running()
        session.requestStop()
        assertEquals("stop", sent()["command"].asString)
        assertEquals("sim_1", sent()["session_id"].asString)
        assertTrue(labels(panel).contains("■ Stopping — saving snapshot…"))
        assertEquals(0, forced)
    }

    fun testASecondStopClickForcesTheDaemonDown() {
        val (session, panel) = running()
        session.requestStop()
        session.requestStop()
        assertEquals(1, forced)
        assertTrue(labels(panel).contains("■ Stopping — interrupting…"))
        session.requestStop()
        assertEquals(1, forced)
    }

    fun testAStopTheDaemonRefusesForcesItDown() {
        val (session, _) = running()
        daemon.controlReply = { """{"event":"error","message":"nope","reason":"session_not_running"}""" }
        session.requestStop()
        assertEquals(1, forced)
    }

    fun testCommandsAreRefusedOnceStopIsRequested() {
        val (session, _) = running()
        session.requestStop()
        var reply: ControlReply? = null
        session.sendBias(5) { reply = it }
        assertEquals("stop already requested", (reply as ControlReply.Failed).reason)
    }

    fun testAnAdoptedAgentRunShowsItsOriginAndCannotBeSharedBack() {
        val (session, panel) = running(AnnotationSource.AGENT)
        assertTrue(labels(panel).contains("🤖 STARTED BY AI AGENT"))
        assertFalse(session.agentSteerSupported)
        assertTrue(running().first.agentSteerSupported)
    }

    fun testAUserRunDoesNotShowTheAgentBadge() {
        val (_, panel) = running()
        assertTrue(labels(panel).none { it.contains("STARTED BY AI AGENT") })
    }

    fun testLettingAgentsSteerSendsTheCommandWithTheFlag() {
        val (session, _) = running()
        session.setAgentSteer(true) { }
        session.setAgentSteer(false) { }
        assertEquals("agent_steer", sent(0)["command"].asString)
        assertTrue(sent(0)["allow"].asBoolean)
        assertFalse(sent(1)["allow"].asBoolean)
    }

    fun testFailingTheRunShowsTheReasonAndEndsItOnce() {
        val (session, panel) = running()
        session.fail("The shared gg daemon stopped")
        session.fail("again")
        assertTrue(labels(panel).any { it == "✗ EXITED (code 1) — The shared gg daemon stopped" })
        assertEquals(1, ended.size)
    }

    fun testHeartbeatsReachThePanel() {
        val (session, panel) = running()
        session.onEvent(DaemonTestSupport.event("heartbeat", "sim_1", "user", HeartbeatPayload(event = "heartbeat", elapsedSeconds = 65.0, stage = 1, totalStages = 1, bias = 0)))
        assertTrue(labels(panel).contains("01:05"))
    }
}
