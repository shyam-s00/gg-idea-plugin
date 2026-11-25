package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonParser
import dev.gopherglide.ggplugin.execution.control.ControlCommand
import dev.gopherglide.ggplugin.execution.control.ControlReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlWireTest {
    @Test
    fun everyCommandNamesItsSession() {
        val body = JsonParser.parseString(ControlWire.body(ControlCommand.Hold("h1", 15), "sim_9")).asJsonObject
        assertEquals("sim_9", body["session_id"].asString)
        assertEquals("hold", body["command"].asString)
        assertEquals(15, body["rps"].asInt)
        assertEquals("h1", body["id"].asString)
    }

    @Test
    fun withoutASessionNoneIsSent() {
        assertTrue(!JsonParser.parseString(ControlWire.body(ControlCommand.Stop("s1"), null)).asJsonObject.has("session_id"))
    }

    @Test
    fun agentSteerHasItsOwnWireForm() {
        assertEquals(
            """{"id":"g1","command":"agent_steer","allow":true,"session_id":"sim_1"}""",
            ControlWire.body(ControlCommand.AgentSteer("g1", true), "sim_1")
        )
    }

    @Test
    fun anAckIsASuccess() {
        val reply = ControlWire.reply(200, """{"event":"ack","command":"hold","id":"h1","hold_rps":15,"elapsed_s":2.5}""")
        assertEquals(15, (reply as ControlReply.Ack).payload.holdRps)
    }

    @Test
    fun aMarkAnswerIsARecordedMarkWithItsClampFlag() {
        val reply = ControlWire.reply(200, """{"event":"mark","id":"m1","label":"x","elapsed_s":0.01,"clamped":true,"source":"user"}""")
        assertEquals(true, (reply as ControlReply.MarkRecorded).payload.clamped)
    }

    @Test
    fun anErrorWithAReasonIsARefusal() {
        val reply = ControlWire.reply(200, """{"event":"error","message":"unknown session_id","reason":"unknown_session"}""")
        assertEquals("unknown_session", (reply as ControlReply.Rejected).payload.reason)
    }

    @Test
    fun theAckForAgentSteerIsAnAckAndCarriesNoRunStats() {
        assertTrue(ControlWire.reply(200, """{"allow":true,"command":"agent_steer","event":"ack","id":"as1","session_id":"sim_1"}""") is ControlReply.Ack)
    }

    @Test
    fun anythingElseIsAFailureThatNamesTheStatus() {
        listOf("", "not json", """{"event":"heartbeat"}""", """{"event":"error"}""").forEach {
            val reply = ControlWire.reply(502, it)
            assertTrue(it, reply is ControlReply.Failed)
            assertTrue((reply as ControlReply.Failed).reason.contains("502"))
        }
    }
}
