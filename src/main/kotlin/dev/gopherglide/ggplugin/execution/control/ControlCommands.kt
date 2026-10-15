package dev.gopherglide.ggplugin.execution.control

import com.google.gson.JsonObject

/** A command written to the run's stdin, one JSON object per line. */
sealed interface ControlCommand {
    val id: String

    data class Bias(override val id: String, val amount: Int) : ControlCommand
    data class Mark(override val id: String, val label: String) : ControlCommand
    data class Stop(override val id: String) : ControlCommand
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
        }
        is ControlCommand.Stop -> json.addProperty("command", "stop")
    }
    return json.toString()
}
