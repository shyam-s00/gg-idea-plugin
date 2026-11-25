package dev.gopherglide.ggplugin.mcp

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Delay before each reconnect attempt: it doubles up to a ceiling and starts over after a connection works. */
class Backoff(private val firstMillis: Long = 500, private val maxMillis: Long = 10_000) {
    private var next = firstMillis

    fun nextDelay(): Long = next.also { next = (next * 2).coerceAtMost(maxMillis) }

    fun reset() {
        next = firstMillis
    }
}

/**
 * Follows `/plugin/events` and hands each event to [listener] on its own thread, reconnecting with
 * [backoff] when the stream drops. The daemon replays the active run's state on every connect.
 */
class PluginBridgeSseClient(
    private val info: DaemonInfo,
    private val listener: Listener,
    private val backoff: Backoff = Backoff(),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) {
    interface Listener {
        fun onEvent(event: PluginEvent)

        fun onConnectionChanged(connected: Boolean)

        /** The daemon refused our token; reconnecting would not help. */
        fun onRejected() {}
    }

    private val closed = AtomicBoolean(false)
    @Volatile private var stream: InputStream? = null
    private var thread: Thread? = null

    fun start() {
        check(thread == null) { "already started" }
        thread = Thread(::run, "Gopher Glide plugin bridge").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { stream?.close() }
        thread?.interrupt()
        thread?.join(2_000)
    }

    private fun run() {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        while (!closed.get()) {
            var connected = false
            try {
                val request = HttpRequest.newBuilder(URI.create(info.baseUrl + "/plugin/events"))
                    .header("Authorization", "Bearer ${info.token}")
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() == 401) {
                    listener.onRejected()
                    return
                }
                if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
                stream = response.body()
                connected = true
                backoff.reset()
                listener.onConnectionChanged(true)
                read(response.body())
            } catch (e: InterruptedException) {
                return
            } catch (e: IOException) {
                if (closed.get()) return
            } finally {
                stream = null
                if (connected && !closed.get()) listener.onConnectionChanged(false)
            }
            if (closed.get()) return
            try {
                sleep(backoff.nextDelay())
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun read(body: InputStream) {
        val parser = SseParser()
        body.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (closed.get()) return
                parser.feed(line)?.let(listener::onEvent)
            }
        }
    }
}
