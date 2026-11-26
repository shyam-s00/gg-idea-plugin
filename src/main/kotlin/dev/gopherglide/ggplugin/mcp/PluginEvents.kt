package dev.gopherglide.ggplugin.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.gopherglide.ggplugin.execution.HeartbeatPayload

/**
 * One event from `/plugin/events`. [payload] is the run event inside the envelope; [dropped] is set on a
 * "lagged" notice, which names how many heartbeats this client missed.
 */
data class PluginEvent(
    val name: String,
    val sessionId: String?,
    /** "user" or "agent". */
    val origin: String?,
    val payload: HeartbeatPayload?,
    val dropped: Int? = null
) {
    val isTerminal: Boolean get() = payload?.isTerminal == true
}

object PluginEvents {
    private val gson = Gson()

    /** Null when [data] is not a JSON object; an unknown shape gives an event without a payload. */
    fun parse(name: String, data: String): PluginEvent? {
        val json = try {
            JsonParser.parseString(data).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val lcp = json["lcp"]?.takeIf { it.isJsonObject }?.asJsonObject
        val payload = lcp?.let { gson.fromJson(it, HeartbeatPayload::class.java) }
        return PluginEvent(
            name = name,
            sessionId = json.string("session_id"),
            origin = json.string("origin"),
            payload = payload,
            dropped = json["dropped"]?.takeIf { it.isJsonPrimitive }?.asInt
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.takeIf { it.isJsonPrimitive }?.asString
}

/** Turns the lines of a Server-Sent Events stream into events, one call per line. */
class SseParser {
    private var name = "message"
    private val data = StringBuilder()

    /** Returns the event a blank line completes, or null. Comment lines and unknown fields are ignored. */
    fun feed(line: String): PluginEvent? {
        if (line.isEmpty()) return flush()
        when {
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> name = line.substring(6).trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).removePrefix(" "))
            }
        }
        return null
    }

    private fun flush(): PluginEvent? {
        val event = if (data.isEmpty()) null else PluginEvents.parse(name, data.toString())
        name = "message"
        data.setLength(0)
        return event
    }
}
