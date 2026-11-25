package dev.gopherglide.ggplugin.mcp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginBridgeSseClientTest {
    private val daemon = FakeDaemon()
    private val events = CopyOnWriteArrayList<PluginEvent>()
    private val connections = CopyOnWriteArrayList<Boolean>()
    private val rejected = AtomicInteger()
    private var client: PluginBridgeSseClient? = null

    private val listener = object : PluginBridgeSseClient.Listener {
        override fun onEvent(event: PluginEvent) { events += event }
        override fun onConnectionChanged(connected: Boolean) { connections += connected }
        override fun onRejected() { rejected.incrementAndGet() }
    }

    @After
    fun tearDown() {
        client?.close()
        daemon.close()
    }

    private fun start(info: DaemonInfo = daemon.info, sleep: (Long) -> Unit = { Thread.sleep(20) }) {
        client = PluginBridgeSseClient(info, listener, Backoff(20, 50), sleep).also { it.start() }
    }

    private fun waitFor(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun lcp(event: String, extra: String = "") = """{"event":"$event"$extra}"""

    @Test
    fun eventsArriveWithTheirEnvelopeInOrder() {
        start()
        waitFor("connection") { daemon.openStreams() == 1 }
        daemon.emit("started", "sim_1", "agent", lcp("started"))
        daemon.emit("heartbeat", "sim_1", "agent", lcp("heartbeat", ""","target_rps":10"""))
        waitFor("two events") { events.size == 2 }
        assertEquals(listOf("started", "heartbeat"), events.map { it.name })
        assertEquals("sim_1", events[0].sessionId)
        assertEquals("agent", events[0].origin)
        assertEquals(10, events[1].payload!!.targetRps)
        assertEquals(listOf(true), connections)
    }

    @Test
    fun theTokenIsSentAsABearerHeader() {
        start()
        waitFor("connection") { daemon.openStreams() == 1 }
        assertEquals(0, rejected.get())
    }

    @Test
    fun aDroppedStreamIsReopenedAndTheReplayArrivesAgain() {
        daemon.onConnect = { listOf("event: started\ndata: {\"session_id\":\"sim_1\",\"origin\":\"agent\",\"lcp\":{\"event\":\"started\"}}\n\n") }
        start()
        waitFor("first replay") { events.size == 1 }
        daemon.dropStreams()
        waitFor("second replay") { events.size == 2 }
        assertEquals(listOf("started", "started"), events.map { it.name })
        assertEquals(2, daemon.connects.get())
        assertTrue(connections.contains(false))
        assertEquals(true, connections.last())
    }

    @Test
    fun aServerErrorIsRetriedUntilItRecovers() {
        daemon.eventsStatus = 503
        start()
        waitFor("a few attempts") { daemon.connects.get() >= 3 }
        assertTrue(events.isEmpty())
        daemon.eventsStatus = 200
        waitFor("connection") { daemon.openStreams() == 1 }
        assertEquals(listOf(true), connections)
    }

    @Test
    fun aRefusedTokenIsReportedOnceAndNotRetried() {
        start(DaemonInfo(daemon.info.baseUrl, "wrong-token-0123456789", 0))
        waitFor("rejection") { rejected.get() == 1 }
        Thread.sleep(200)
        assertEquals(1, daemon.connects.get())
        assertEquals(1, rejected.get())
    }

    @Test
    fun closingStopsTheStreamAndNoFurtherAttemptsAreMade() {
        start()
        waitFor("connection") { daemon.openStreams() == 1 }
        client!!.close()
        val attempts = daemon.connects.get()
        Thread.sleep(200)
        assertEquals(attempts, daemon.connects.get())
    }

    @Test
    fun anUnreachableDaemonDoesNotThrowAndKeepsTrying() {
        val gone = FakeDaemon().also { it.close() }
        val retried = CountDownLatch(3)
        start(gone.info) { retried.countDown(); Thread.sleep(10) }
        assertTrue(retried.await(5, TimeUnit.SECONDS))
        assertTrue(events.isEmpty())
    }

    @Test
    fun aLaggedNoticeIsPassedOn() {
        start()
        waitFor("connection") { daemon.openStreams() == 1 }
        daemon.emitRaw("event: lagged\ndata: {\"event\":\"lagged\",\"dropped\":4}\n\n")
        waitFor("notice") { events.size == 1 }
        assertEquals(4, events[0].dropped)
    }
}
