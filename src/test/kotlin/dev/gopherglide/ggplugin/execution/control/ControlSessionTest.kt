package dev.gopherglide.ggplugin.execution.control

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class ControlSessionTest {
    private val gson = Gson()
    private val direct = Executor { it.run() }

    private class RecordingStream : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        var flushes = 0
        override fun write(b: Int) = bytes.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = bytes.write(b, off, len)
        override fun flush() { flushes++ }
        fun lines(): List<String> = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
        fun raw(): String = bytes.toString(Charsets.UTF_8)
    }

    private class FailingStream : OutputStream() {
        override fun write(b: Int) = throw IOException("Broken pipe")
    }

    private fun fixture(event: String, id: String): HeartbeatPayload =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v130-control.ndjson")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { gson.fromJson(it, HeartbeatPayload::class.java) }
            .first { it.event == event && it.id == id }

    @Test
    fun commandIsWrittenAsOneNewlineTerminatedFlushedLine() {
        val stream = RecordingStream()
        ControlSession(stream, direct).sendBias(5)
        assertEquals("""{"id":"b_1","command":"bias","amount":5}""" + "\n", stream.raw())
        assertTrue(stream.flushes >= 1)
    }

    @Test
    fun singleThreadedSendsReachTheStreamInOrder() {
        val stream = RecordingStream()
        val session = ControlSession(stream, direct)
        val amounts = (1..50).map { if (it % 2 == 0) it else -it }
        amounts.forEach { session.sendBias(it) }
        assertEquals(amounts, stream.lines().map { JsonParser.parseString(it).asJsonObject["amount"].asInt })
    }

    @Test
    fun concurrentSendersKeepEachThreadsOrderThroughASingleThreadExecutor() {
        val stream = RecordingStream()
        val executor = Executors.newSingleThreadExecutor()
        val session = ControlSession(stream, executor)
        val threads = 4
        val perThread = 100
        val start = CountDownLatch(1)
        val workers = (0 until threads).map { t ->
            Thread {
                start.await()
                repeat(perThread) { i -> session.sendBias(t * 1000 + i + 1) }
            }.also { it.start() }
        }
        start.countDown()
        workers.forEach { it.join() }
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val amounts = stream.lines().map { JsonParser.parseString(it).asJsonObject["amount"].asInt }
        assertEquals(threads * perThread, amounts.size)
        (0 until threads).forEach { t ->
            val mine = amounts.filter { (it - 1) / 1000 == t }
            assertEquals((1..perThread).map { t * 1000 + it }, mine)
        }
    }

    @Test
    fun biasAckCompletesTheMatchingCommand() {
        val session = ControlSession(RecordingStream(), direct)
        var reply: ControlReply? = null
        session.sendBias(10) { reply = it }
        session.onEvent(fixture("ack", "b_1"))
        assertTrue(reply is ControlReply.Ack)
        assertEquals(10, (reply as ControlReply.Ack).payload.bias)
    }

    @Test
    fun markEventCompletesTheMatchingCommand() {
        val session = ControlSession(RecordingStream(), direct)
        var reply: ControlReply? = null
        session.sendMark("cache-warm") { reply = it }
        session.onEvent(fixture("mark", "m_1"))
        assertTrue(reply is ControlReply.MarkRecorded)
    }

    @Test
    fun stopAckCompletesTheStopCommand() {
        val session = ControlSession(RecordingStream(), direct)
        var reply: ControlReply? = null
        session.sendStop { reply = it }
        session.onEvent(fixture("ack", "s_1"))
        assertTrue(reply is ControlReply.Ack)
    }

    @Test
    fun commandErrorWithAnIdRejectsTheMatchingCommand() {
        val session = ControlSession(RecordingStream(), direct)
        var reply: ControlReply? = null
        session.sendBias(1) { reply = it }
        session.onEvent(gson.fromJson("""{"event":"error","id":"b_1","command":"bias","reason":"invalid_argument","message":"x"}""", HeartbeatPayload::class.java))
        assertTrue(reply is ControlReply.Rejected)
    }

    @Test
    fun eventsWithoutAMatchingPendingCommandAreIgnored() {
        val session = ControlSession(RecordingStream(), direct)
        var replies = 0
        session.sendBias(1) { replies++ }
        session.onEvent(fixture("ack", "b_2"))
        session.onEvent(gson.fromJson("""{"event":"error","reason":"parse_error","message":"x"}""", HeartbeatPayload::class.java))
        session.onEvent(gson.fromJson("""{"event":"heartbeat","id":"b_1"}""", HeartbeatPayload::class.java))
        assertEquals(0, replies)
    }

    @Test
    fun aReplyCompletesACommandOnlyOnce() {
        val session = ControlSession(RecordingStream(), direct)
        var replies = 0
        session.sendBias(10) { replies++ }
        session.onEvent(fixture("ack", "b_1"))
        session.onEvent(fixture("ack", "b_1"))
        assertEquals(1, replies)
    }

    @Test
    fun writeFailureFailsTheCommandAndLaterOnesWithoutThrowing() {
        val session = ControlSession(FailingStream(), direct)
        val replies = mutableListOf<ControlReply>()
        session.sendBias(1) { replies += it }
        session.sendMark("x") { replies += it }
        assertEquals(listOf(ControlReply.Failed("Broken pipe"), ControlReply.Failed("control channel closed")), replies)
    }

    @Test
    fun noCommandsAreAcceptedAfterStopIsSent() {
        val stream = RecordingStream()
        val session = ControlSession(stream, direct)
        session.sendStop { }
        val replies = mutableListOf<ControlReply>()
        session.sendBias(5) { replies += it }
        session.sendMark("late") { replies += it }
        assertEquals(List(2) { ControlReply.Failed("stop already requested") }, replies)
        assertEquals(1, stream.lines().size)
    }

    @Test
    fun closeFailsEverythingStillWaitingAndRefusesMore() {
        val queued = mutableListOf<Runnable>()
        val stream = RecordingStream()
        val session = ControlSession(stream, Executor { queued += it })
        val replies = mutableListOf<ControlReply>()
        session.sendBias(1) { replies += it }
        session.close()
        queued.forEach { it.run() }
        session.sendBias(2) { replies += it }
        assertEquals(List(2) { ControlReply.Failed("control channel closed") }, replies)
        assertEquals("", stream.raw())
    }

    @Test
    fun rejectedExecutionFailsTheCommand() {
        val session = ControlSession(RecordingStream(), Executor { throw RejectedExecutionException() })
        var reply: ControlReply? = null
        session.sendBias(1) { reply = it }
        assertEquals(ControlReply.Failed("control channel closed"), reply)
    }
}
