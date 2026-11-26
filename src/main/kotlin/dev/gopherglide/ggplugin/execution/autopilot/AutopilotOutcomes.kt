package dev.gopherglide.ggplugin.execution.autopilot

/** Wording for what the autopilot is doing and how it ended. */
object AutopilotOutcomes {
    fun modeName(mode: String): String = when (mode) {
        "find-ceiling" -> "Find Ceiling"
        "slo-guard" -> "SLO Guard"
        else -> mode
    }

    /** "⚡ Autopilot: Find Ceiling · Stepping (28 RPS)" */
    fun pill(mode: String, state: String, holdRps: Int?): String {
        val rate = holdRps?.let { " ($it RPS)" }.orEmpty()
        return "⚡ Autopilot: ${modeName(mode)} · ${state.replaceFirstChar { it.uppercaseChar() }}$rate"
    }

    /** The result line for a finished run, or null when there is nothing worth adding. */
    fun describe(mode: String?, outcome: String?, ceilingRps: Int?): String? {
        if (mode == null || outcome == null) return null
        if (outcome == "stopped") return null
        return "⚡ " + (plain(mode, outcome, ceilingRps))
    }

    /** The result without the badge; "stopped" is spelled out for places that always show a result. */
    fun plain(mode: String, outcome: String?, ceilingRps: Int?): String = when (outcome) {
        null -> "No result recorded"
        "ceiling_found" -> "Ceiling found: ${ceilingRps ?: "?"} RPS"
        "ceiling_not_reached" -> "No ceiling found up to ${ceilingRps ?: "the"} RPS cap"
        "unstable" -> "No stable rate confirmed before the time limit"
        "stopped" -> "Stopped before a result"
        else -> "Autopilot ${modeName(mode)}: ${outcome.replace('_', ' ')}"
    }
}
