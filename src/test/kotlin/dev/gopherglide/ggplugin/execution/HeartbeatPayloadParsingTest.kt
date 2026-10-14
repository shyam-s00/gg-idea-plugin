package dev.gopherglide.ggplugin.execution

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatPayloadParsingTest {
    private val gson = Gson()

    private fun lines(name: String): List<String> =
        checkNotNull(javaClass.getResourceAsStream("/lcp/$name")) { "missing fixture $name" }
            .bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }

    private fun payloads(name: String): List<HeartbeatPayload> =
        lines(name).map { gson.fromJson(it, HeartbeatPayload::class.java) }

    private fun parse(json: String): HeartbeatPayload = gson.fromJson(json, HeartbeatPayload::class.java)

    private val allStreams = listOf(
        "stream-v130-control.ndjson", "stream-v130-control-none.ndjson",
        "stream-v130-natural.ndjson", "stream-v120.ndjson"
    )

    @Test
    fun everyCapturedLineParsesToAnEvent() {
        allStreams.forEach { name ->
            payloads(name).forEach { assertTrue("$name: ${it.event}", it.event.isNotEmpty()) }
        }
    }

    @Test
    fun v130StartedCarriesProtocolVersionCapabilitiesAndStages() {
        val started = payloads("stream-v130-natural.ndjson").first { it.event == "started" }
        assertEquals(1, started.protocolVersion)
        assertEquals(listOf("control.bias", "control.mark", "control.stop"), started.capabilities)
        assertEquals("smoke", started.profile)
        assertEquals(2, started.stages?.size)
    }

    @Test
    fun controlNoneStartedHasEmptyNotNullCapabilities() {
        val started = payloads("stream-v130-control-none.ndjson").first { it.event == "started" }
        assertNotNull(started.capabilities)
        assertTrue(started.capabilities!!.isEmpty())
    }

    @Test
    fun v120StartedHasNullCapabilitiesAndProtocolVersion() {
        val started = payloads("stream-v120.ndjson").first { it.event == "started" }
        assertNull(started.capabilities)
        assertNull(started.protocolVersion)
    }

    @Test
    fun biasIsNullOnV120ButRealZeroOnV130() {
        assertTrue(payloads("stream-v120.ndjson").filter { it.event == "heartbeat" }.all { it.bias == null })
        assertEquals(0, payloads("stream-v130-natural.ndjson").first { it.event == "heartbeat" }.bias)
    }

    @Test
    fun biasAckCarriesIdCommandAndCumulativeBias() {
        val ack = payloads("stream-v130-control.ndjson").first { it.event == "ack" && it.id == "b_1" }
        assertEquals("bias", ack.command)
        assertEquals(10, ack.bias)
    }

    @Test
    fun stopAckHasNoBias() {
        val ack = payloads("stream-v130-control.ndjson").first { it.event == "ack" && it.command == "stop" }
        assertEquals("s_1", ack.id)
        assertNull(ack.bias)
    }

    @Test
    fun markEventCarriesLabelAndElapsedSeconds() {
        val mark = payloads("stream-v130-control.ndjson").first { it.event == "mark" }
        assertEquals("m_1", mark.id)
        assertEquals("cache-warm", mark.label)
        assertEquals(6.0, mark.elapsedSeconds!!, 0.1)
    }

    @Test
    fun commandErrorsAreRecognizedAndDoNotEndTheRun() {
        val errors = payloads("stream-v130-control.ndjson").filter { it.event == "error" }
        assertEquals(setOf("invalid_argument", "unknown_command", "parse_error"), errors.map { it.reason }.toSet())
        assertTrue(errors.all { it.isCommandError })
        assertTrue(errors.none { it.isTerminal })
    }

    @Test
    fun fatalErrorWithoutReasonStaysTerminal() {
        val fatal = parse("""{"time":"t","event":"error","message":"engine exited with error: boom"}""")
        assertFalse(fatal.isCommandError)
        assertTrue(fatal.isTerminal)
    }

    @Test
    fun emptyReasonIsNotACommandError() {
        val fatal = parse("""{"event":"error","reason":"","message":"boom"}""")
        assertFalse(fatal.isCommandError)
        assertTrue(fatal.isTerminal)
    }

    @Test
    fun stoppedAndFinishedAndInterruptedAreTerminal() {
        assertTrue(payloads("stream-v130-control.ndjson").last { it.event == "stopped" }.isTerminal)
        assertTrue(payloads("stream-v130-natural.ndjson").last().isTerminal)
        assertTrue(parse("""{"event":"interrupted"}""").isTerminal)
    }

    @Test
    fun runningEventsAreNotTerminal() {
        val running = payloads("stream-v130-control.ndjson")
            .filter { it.event in setOf("snap", "started", "heartbeat", "ack", "mark") }
        assertTrue(running.isNotEmpty())
        assertTrue(running.none { it.isTerminal })
    }

    @Test
    fun stoppedCarriesFinalStatsAndSnapshotMessage() {
        val stopped = payloads("stream-v130-control.ndjson").last { it.event == "stopped" }
        assertTrue(stopped.totalRequests > 0)
        assertTrue(stopped.message.contains("Snapshot saved"))
    }
}
