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
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }

    private fun payloads(name: String): List<HeartbeatPayload> =
        lines(name).map { gson.fromJson(it, HeartbeatPayload::class.java) }

    private fun parse(json: String): HeartbeatPayload = gson.fromJson(json, HeartbeatPayload::class.java)

    private val allStreams = listOf(
        "stream-v130-control.ndjson", "stream-v130-control-none.ndjson",
        "stream-v130-natural.ndjson", "stream-v120.ndjson",
        "stream-v150-autopilot.ndjson", "stream-v150-find-ceiling.ndjson", "stream-v150-errors.ndjson"
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

    @Test
    fun v150StartedAdvertisesHoldAutopilotAndClickTimeMarks() {
        val started = payloads("stream-v150-autopilot.ndjson").first { it.event == "started" }
        assertEquals(
            listOf("control.bias", "control.mark", "control.mark.elapsed", "control.stop", "control.hold", "control.autopilot"),
            started.capabilities
        )
    }

    @Test
    fun olderStreamsLeaveEveryNewFieldNull() {
        allStreams.filter { "v150" !in it }.forEach { name ->
            payloads(name).forEach {
                assertNull(name, it.holdRps)
                assertNull(name, it.autopilot)
                assertNull(name, it.window)
                assertNull(name, it.errorMix)
                assertNull(name, it.source)
                assertNull(name, it.mode)
                assertNull(name, it.ceilingRps)
            }
        }
    }

    @Test
    fun heartbeatCarriesWindowHealth() {
        val window = payloads("stream-v150-errors.ndjson").first { it.event == "heartbeat" }.window!!
        assertEquals(1, window.seconds)
        assertEquals(60, window.samples)
        assertEquals(0.6667, window.errorRate, 0.001)
        assertTrue(window.p99Ms > 0.0)
        assertFalse(window.capped)
    }

    @Test
    fun errorMixListsTheFailingEndpointsAndCodes() {
        val mix = payloads("stream-v150-errors.ndjson").first { it.event == "heartbeat" }.errorMix!!
        assertEquals(10, mix.windowSeconds)
        assertEquals(40, mix.sampled)
        assertEquals(20, mix.http5xx)
        assertEquals(20, mix.http4xx)
        assertEquals(0, mix.transport)
        assertEquals(listOf(404 to 20, 503 to 20), mix.topCodes.map { it.code to it.n })
        val top = mix.topEndpoints.first { it.code == 503 }
        assertEquals("GET /fail/checkout", top.endpoint)
        assertEquals("http_5xx", top.kind)
        assertEquals(20, top.n)
    }

    @Test
    fun errorMixIsAbsentWhileNothingFails() {
        val heartbeats = payloads("stream-v150-find-ceiling.ndjson").filter { it.event == "heartbeat" }
        assertTrue(heartbeats.isNotEmpty())
        assertTrue(heartbeats.all { it.errorMix == null })
    }

    @Test
    fun holdShowsOnHeartbeatsUntilReleasedAndSurvivesTheAutopilotThatSetIt() {
        val heartbeats = payloads("stream-v150-autopilot.ndjson").filter { it.event == "heartbeat" }
        assertEquals(15, heartbeats.first { it.holdRps != null }.holdRps)
        val clamped = heartbeats.filter { it.autopilot?.state == "clamped" }
        assertEquals(listOf(17, 17), clamped.map { it.holdRps })
        val afterAutopilot = heartbeats.dropWhile { it.autopilot?.state != "clamped" }.dropWhile { it.autopilot != null }
        assertEquals(listOf(17, 17), afterAutopilot.takeWhile { it.holdRps != null }.map { it.holdRps })
        assertTrue(afterAutopilot.last().holdRps == null)
    }

    @Test
    fun heartbeatAutopilotIsANestedObject() {
        val watching = payloads("stream-v150-autopilot.ndjson").first { it.autopilot != null }
        assertEquals(AutopilotBrief("slo-guard", "watching"), watching.autopilot)
        assertNull(watching.mode)
        assertNull(watching.state)
    }

    @Test
    fun autopilotEventsCarryModeAndStateAtTheTopLevel() {
        val events = payloads("stream-v150-autopilot.ndjson").filter { it.event == "autopilot" }
        assertEquals(listOf("watching", "clamped", "done"), events.map { it.state })
        assertTrue(events.all { it.mode == "slo-guard" })
        assertNull(events.first().autopilot)
        assertEquals(17, events[1].holdRps)
        assertEquals("stopped", events.last().outcome)
        assertTrue(events.none { it.isTerminal })
    }

    @Test
    fun findCeilingEndsWithTheCeilingOnBothTheEventAndTheFinish() {
        val stream = payloads("stream-v150-find-ceiling.ndjson")
        val done = stream.last { it.event == "autopilot" }
        assertEquals("done", done.state)
        assertEquals("ceiling_found", done.outcome)
        assertEquals(28, done.ceilingRps)
        val finished = stream.last()
        assertEquals("finished", finished.event)
        assertEquals("find-ceiling", finished.mode)
        assertEquals(28, finished.ceilingRps)
        assertNull(stream.first { it.event == "started" }.profile)
    }

    @Test
    fun autopilotMarksAreUnsolicitedAndNamedAsTheirSource() {
        val marks = payloads("stream-v150-autopilot.ndjson").filter { it.event == "mark" }
        val user = marks.first { it.id == "m_1" }
        assertNull(user.source)
        assertEquals(1.5, user.elapsedSeconds!!, 0.0)
        val auto = marks.single { it.source == "autopilot" }
        assertNull(auto.id)
        assertTrue(auto.label!!.startsWith("Autopilot SLO Guard triggered"))
    }

    @Test
    fun holdAckCarriesTheRateAndReleaseAckDoesNot() {
        val acks = payloads("stream-v150-autopilot.ndjson").filter { it.event == "ack" }.associateBy { it.id }
        assertEquals(15, acks.getValue("h_1").holdRps)
        assertEquals("hold", acks.getValue("h_1").command)
        assertNull(acks.getValue("h_2").holdRps)
        assertEquals("release", acks.getValue("h_2").command)
        assertEquals("slo-guard", acks.getValue("a_1").mode)
    }

    @Test
    fun manualCommandsDuringAnAutopilotAreRejectedWithoutEndingTheRun() {
        val rejected = payloads("stream-v150-autopilot.ndjson").filter { it.reason == "autopilot_active" }
        assertEquals(listOf("b_2", "h_3"), rejected.map { it.id })
        assertTrue(rejected.all { it.isCommandError && !it.isTerminal })
    }

    @Test
    fun heartbeatElapsedIsRunTimeAndIncreases() {
        val heartbeats = payloads("stream-v150-errors.ndjson").filter { it.event == "heartbeat" }
        assertEquals(1.0, heartbeats.first().elapsedSeconds!!, 0.05)
        assertTrue(heartbeats.zipWithNext().all { (a, b) -> b.elapsedSeconds!! > a.elapsedSeconds!! })
    }

    @Test
    fun clampedAndSourceReadFromAMarkReply() {
        val mark = parse("""{"event":"mark","id":"m1","label":"x","elapsed_s":0.01,"clamped":true,"source":"user"}""")
        assertEquals(true, mark.clamped)
        assertEquals("user", mark.source)
        assertEquals(true, parse("""{"event":"error","reason":"autopilot_active","message":"m"}""").isCommandError)
    }
}
