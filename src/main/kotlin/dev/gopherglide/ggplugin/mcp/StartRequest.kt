package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonObject
import dev.gopherglide.ggplugin.execution.autopilot.AutopilotArgs

/** A run's command-line arguments turned into the body of `POST /plugin/start`. */
sealed interface StartRequest {
    data class Routable(val body: JsonObject) : StartRequest

    /** The daemon cannot express these arguments; the run goes through a direct process instead. */
    data class Unroutable(val reason: String) : StartRequest

    companion object {
        private val valueFlags = setOf(
            "--profile", "-p", "--http-file", "--peak-rps", "-r", "--duration", "-d", "--snap-tag",
            "--autopilot", "--max-p99", "--max-error-rate", "--start-rps", "--max-rps"
        )

        fun fromArgs(args: List<String>): StartRequest {
            val values = mutableMapOf<String, String>()
            var snap = false
            var config: String? = null
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                when {
                    arg == "--snap" -> snap = true
                    arg in valueFlags -> {
                        val value = args.getOrNull(i + 1) ?: return Unroutable("$arg has no value")
                        values[canonical(arg)] = value
                        i++
                    }
                    arg.startsWith("-") -> return Unroutable("$arg is not supported through the daemon")
                    i == 0 -> config = arg
                    else -> return Unroutable("unexpected argument $arg")
                }
                i++
            }
            return build(values, snap, config)
        }

        private fun canonical(flag: String) = when (flag) {
            "-p" -> "--profile"
            "-r" -> "--peak-rps"
            "-d" -> "--duration"
            else -> flag
        }

        private fun build(values: Map<String, String>, snap: Boolean, config: String?): StartRequest {
            val mode = values["--autopilot"]
            val body = JsonObject()
            when {
                config != null -> body.addProperty("config_path", config)
                values["--http-file"] == null -> return Unroutable("no .http file or config to run")
            }
            values["--http-file"]?.let { body.addProperty("http_path", it) }

            if (mode == "find-ceiling") {
                if (config != null || values.containsKey("--profile") || values.containsKey("--peak-rps")) {
                    return Unroutable("find-ceiling sets its own schedule")
                }
            } else if (config == null) {
                body.addProperty("profile", values["--profile"] ?: return Unroutable("no profile to run"))
                values["--peak-rps"]?.let { body.addProperty("peak_rps", it.toIntOrNull() ?: return Unroutable("bad --peak-rps")) }
                values["--duration"]?.let { body.addProperty("duration", it) }
            }

            if (mode != null) body.add("autopilot", autopilot(mode, values) ?: return Unroutable("bad autopilot flags"))
            if (snap) {
                body.addProperty("snap", true)
                values["--snap-tag"]?.let { body.addProperty("snap_tag", it) }
            }
            return Routable(body)
        }

        private fun autopilot(mode: String, values: Map<String, String>): JsonObject? {
            val json = JsonObject().apply { addProperty("mode", mode) }
            values["--max-p99"]?.let { json.addProperty("max_p99_ms", AutopilotArgs.parseLatencyMillis(it) ?: return null) }
            values["--max-error-rate"]?.let { json.addProperty("max_error_rate", it.toDoubleOrNull() ?: return null) }
            if (mode == "find-ceiling") {
                values["--start-rps"]?.let { json.addProperty("start_rps", it.toIntOrNull() ?: return null) }
                values["--max-rps"]?.let { json.addProperty("max_rps", it.toIntOrNull() ?: return null) }
                values["--duration"]?.let { json.addProperty("max_duration", it) }
            }
            return json
        }
    }
}
