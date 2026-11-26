package dev.gopherglide.ggplugin.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A stand-in for the `gg mcp` plugin endpoints: records what it is sent and streams the events it is given. */
class FakeDaemon : AutoCloseable {
    val token = "fake-token-0123456789"
    val startBodies = CopyOnWriteArrayList<String>()
    val controlBodies = CopyOnWriteArrayList<String>()
    val connects = AtomicInteger()

    @Volatile var startReply = """{"session_id":"sim_user1","origin":"user","state":"starting"}"""
    @Volatile var controlReply: (String) -> String = { body ->
        val id = Regex(""""id":"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "x"
        """{"event":"ack","id":"$id","command":"x"}"""
    }
    /** Events written at the start of every new connection, like the daemon's replay. */
    @Volatile var onConnect: () -> List<String> = { emptyList() }
    @Volatile var eventsStatus = 200

    private val streams = CopyOnWriteArrayList<LinkedBlockingQueue<String>>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool { r -> Thread(r, "fake-daemon").apply { isDaemon = true } }
        createContext("/plugin/start") { ex -> handle(ex) { body -> startBodies += body; startReply } }
        createContext("/plugin/control") { ex -> handle(ex) { body -> controlBodies += body; controlReply(body) } }
        createContext("/plugin/events", ::events)
        start()
    }

    val info: DaemonInfo get() = DaemonInfo("http://127.0.0.1:${server.address.port}", token, 0)

    /** Sends one event to every open stream. */
    fun emit(name: String, sessionId: String, origin: String, lcp: String) {
        val data = """{"session_id":"$sessionId","origin":"$origin","lcp":$lcp}"""
        streams.forEach { it.add("event: $name\ndata: $data\n\n") }
    }

    fun emitRaw(text: String) = streams.forEach { it.add(text) }

    /** Ends every open stream the way a dying connection would. */
    fun dropStreams() = streams.forEach { it.add(END) }

    fun openStreams(): Int = streams.size

    private fun authorized(ex: HttpExchange) = ex.requestHeaders.getFirst("Authorization") == "Bearer $token"

    private fun handle(ex: HttpExchange, reply: (String) -> String) {
        val body = ex.requestBody.readAllBytes().toString(Charsets.UTF_8)
        if (!authorized(ex)) return respond(ex, 401, "")
        respond(ex, 200, reply(body))
    }

    private fun respond(ex: HttpExchange, status: Int, text: String) {
        val bytes = text.toByteArray()
        ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
    }

    private fun events(ex: HttpExchange) {
        connects.incrementAndGet()
        if (!authorized(ex)) return respond(ex, 401, "")
        if (eventsStatus != 200) return respond(ex, eventsStatus, "")
        ex.responseHeaders.add("Content-Type", "text/event-stream")
        ex.sendResponseHeaders(200, 0)
        val queue = LinkedBlockingQueue<String>()
        streams += queue
        try {
            val out = ex.responseBody
            out.write(": connected\n\n".toByteArray())
            onConnect().forEach { out.write(it.toByteArray()) }
            out.flush()
            while (true) {
                val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (next == END) break
                out.write(next.toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            // The client went away.
        } finally {
            streams -= queue
            runCatching { ex.close() }
        }
    }

    override fun close() {
        streams.forEach { it.add(END) }
        server.stop(0)
        (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private companion object {
        const val END = "\u0000end"
    }
}
