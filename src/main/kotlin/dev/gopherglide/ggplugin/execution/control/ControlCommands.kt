package dev.gopherglide.ggplugin.execution.control

import com.google.gson.JsonObject

/** A command written to the run's stdin, one JSON object per line. */
sealed interface ControlCommand {
    val id: String

    data class Bias(override val id: String, val amount: Int) : ControlCommand

    /** [elapsedSeconds] places the mark at that point in the run; null lets gg use the moment it receives the command. */
    data class Mark(override val id: String, val label: String, val elapsedSeconds: Double? = null) : ControlCommand

    data class Stop(override val id: String) : ControlCommand

    /** Pins the rate at [rps] until a [Release]. */
    data class Hold(override val id: String, val rps: Int) : ControlCommand

    data class Release(override val id: String) : ControlCommand

    /** Lets AI agents steer the run (true) or leaves it read-only for them (false). */
    data class AgentSteer(override val id: String, val allow: Boolean) : ControlCommand

    /** The limits are only meaningful for a start; null leaves gg's defaults. */
    data class Autopilot(
        override val id: String,
        val action: Action,
        val mode: String? = null,
        val maxP99Ms: Int? = null,
        val maxErrorRate: Double? = null
    ) : ControlCommand {
        enum class Action(val wire: String) { START("start"), STOP("stop") }
    }
}

/** The wire form, without the trailing newline. */
fun ControlCommand.toLine(): String {
    val json = JsonObject()
    json.addProperty("id", id)
    when (this) {
        is ControlCommand.Bias -> {
            json.addProperty("command", "bias")
            json.addProperty("amount", amount)
        }
        is ControlCommand.Mark -> {
            json.addProperty("command", "mark")
            json.addProperty("label", label)
            elapsedSeconds?.let { json.addProperty("elapsed_s", it) }
        }
        is ControlCommand.Stop -> json.addProperty("command", "stop")
        is ControlCommand.Hold -> {
            json.addProperty("command", "hold")
            json.addProperty("rps", rps)
        }
        is ControlCommand.Release -> json.addProperty("command", "release")
        is ControlCommand.AgentSteer -> {
            json.addProperty("command", "agent_steer")
            json.addProperty("allow", allow)
        }
        is ControlCommand.Autopilot -> {
            json.addProperty("command", "autopilot")
            json.addProperty("action", action.wire)
            mode?.let { json.addProperty("mode", it) }
            maxP99Ms?.let { json.addProperty("max_p99_ms", it) }
            maxErrorRate?.let { json.addProperty("max_error_rate", it) }
        }
    }
    return json.toString()
}
