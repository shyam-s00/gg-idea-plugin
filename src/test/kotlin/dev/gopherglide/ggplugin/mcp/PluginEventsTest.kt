package dev.gopherglide.ggplugin.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginEventsTest {
    private fun events(text: String): List<PluginEvent> {
        val parser = SseParser()
        return text.lines().mapNotNull { parser.feed(it) }
    }

    private val agentStream: List<PluginEvent> =
        events(checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v150-sse-agent.txt")).bufferedReader().readText())

    @Test
    fun theConnectedCommentIsNotAnEvent() {
        assertTrue(events(": connected\n\n").isEmpty())
    }

    @Test
    fun everyEventOfTheCapturedStreamParsesWithItsEnvelope() {
        assertEquals(
            listOf("snap", "started", "heartbeat", "heartbeat", "mark", "ack", "ack", "heartbeat", "heartbeat", "ack", "ack", "stopped"),
            agentStream.map { it.name }
        )
        assertTrue(agentStream.all { it.origin == "agent" && it.sessionId!!.startsWith("sim_") })
        assertTrue(agentStream.all { it.payload != null })
    }

    @Test
    fun theStartedEventCarriesCapabilitiesAndStages() {
        val started = agentStream.first { it.name == "started" }.payload!!
        assertTrue(started.capabilities!!.contains("control.hold"))
        assertEquals(2, started.stages!!.size)
    }

    @Test
    fun anAgentsMarkAndSteersCarryTheirSourceAndAnId() {
        val mark = agentStream.first { it.name == "mark" }.payload!!
        assertEquals("agent", mark.source)
        assertEquals("agent-mark", mark.label)
        assertNotNull(mark.id)
        val bias = agentStream.first { it.payload?.command == "bias" }.payload!!
        assertEquals(5, bias.bias)
        assertEquals("agent", bias.source)
        val hold = agentStream.first { it.payload?.command == "hold" }.payload!!
        assertEquals(20, hold.holdRps)
    }

    @Test
    fun theStopAckHasNoSourceAndTheRunEndsWithAStoppedEvent() {
        val stop = agentStream.first { it.payload?.command == "stop" }.payload!!
        assertNull(stop.source)
        assertTrue(agentStream.last().isTerminal)
        assertFalse(agentStream.first { it.name == "heartbeat" }.isTerminal)
    }

    @Test
    fun dataSplitOverSeveralLinesIsJoined() {
        val e = events("event: ack\ndata: {\"session_id\":\"s\",\ndata: \"origin\":\"user\"}\n\n").single()
        assertEquals("s", e.sessionId)
        assertEquals("user", e.origin)
    }

    @Test
    fun aLaggedNoticeNamesHowManyEventsWereDropped() {
        val e = events("event: lagged\ndata: {\"event\":\"lagged\",\"dropped\":7}\n\n").single()
        assertEquals("lagged", e.name)
        assertEquals(7, e.dropped)
        assertNull(e.payload)
    }

    @Test
    fun anEnvelopeLevelAutopilotStringDoesNotDisturbThePayload() {
        val e = events("event: heartbeat\ndata: {\"session_id\":\"s\",\"origin\":\"user\",\"autopilot\":\"find-ceiling\",\"lcp\":{\"event\":\"heartbeat\",\"autopilot\":{\"mode\":\"find-ceiling\",\"state\":\"stepping\"},\"hold_rps\":25}}\n\n").single()
        assertEquals("stepping", e.payload!!.autopilot!!.state)
        assertEquals(25, e.payload!!.holdRps)
    }

    @Test
    fun garbageIsSkippedAndTheNextEventStillParses() {
        val parsed = events("event: x\ndata: not json\n\nevent: ack\ndata: {\"session_id\":\"s\",\"lcp\":{\"event\":\"ack\"}}\n\n")
        assertEquals(1, parsed.size)
        assertEquals("ack", parsed[0].name)
    }

    @Test
    fun anEventNameDoesNotLeakIntoTheNextEvent() {
        val parsed = events("event: heartbeat\ndata: {\"session_id\":\"a\"}\n\ndata: {\"session_id\":\"b\"}\n\n")
        assertEquals(listOf("heartbeat", "message"), parsed.map { it.name })
    }
}
