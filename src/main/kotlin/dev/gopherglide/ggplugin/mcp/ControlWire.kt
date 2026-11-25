package dev.gopherglide.ggplugin.mcp

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.control.ControlCommand
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.toLine

/** The body of `POST /plugin/control` and the reading of its answer. */
object ControlWire {
    private val gson = Gson()

    /** The command's wire form plus the session it is for, so it can never land on another run. */
    fun body(command: ControlCommand, sessionId: String?): String {
        val json = JsonParser.parseString(command.toLine()).asJsonObject
        sessionId?.let { json.addProperty("session_id", it) }
        return json.toString()
    }

    /** An "ack" or "mark" answer is a success and an "error" with a reason is a refusal; anything else a failure. */
    fun reply(status: Int, body: String): ControlReply {
        val payload = try {
            gson.fromJson(body, HeartbeatPayload::class.java)
        } catch (e: Exception) {
            null
        } ?: return ControlReply.Failed("unexpected answer (HTTP $status)")
        return when {
            payload.event == "ack" -> ControlReply.Ack(payload)
            payload.event == "mark" -> ControlReply.MarkRecorded(payload)
            payload.isCommandError -> ControlReply.Rejected(payload)
            else -> ControlReply.Failed("unexpected answer (HTTP $status)")
        }
    }
}
