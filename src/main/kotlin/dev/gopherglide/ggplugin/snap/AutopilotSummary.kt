package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.autopilot.AutopilotOutcomes

/** The headline and details of the autopilot outcome banner shown above a snapshot's timeline. */
data class AutopilotSummary(val headline: String, val detail: String, val tone: Tone) {
    enum class Tone { GOOD, NEUTRAL, WARN }

    companion object {
        private val breachStates = setOf("breached", "clamped")

        fun from(autopilot: SnapAutopilot?): AutopilotSummary? {
            if (autopilot == null || autopilot.mode.isBlank()) return null
            val outcome = AutopilotOutcomes.plain(autopilot.mode, autopilot.outcome, autopilot.ceilingRps)
            val headline = "⚡ Autopilot ${AutopilotOutcomes.modeName(autopilot.mode)}: $outcome"

            val events = autopilot.events.orEmpty()
            val parts = listOfNotNull(
                limits(autopilot.params),
                events.firstOrNull { it.state in breachStates }?.reason?.removePrefix("Autopilot: ")
            )
            val tone = when (autopilot.outcome) {
                "ceiling_found" -> Tone.GOOD
                "unstable" -> Tone.WARN
                else -> Tone.NEUTRAL
            }
            return AutopilotSummary(headline, parts.joinToString(" · "), tone)
        }

        private fun limits(params: SnapAutopilotParams?): String? {
            val p99 = params?.maxP99Ms?.let { "p99 ≤ ${trim(it)}ms" }
            val errors = params?.maxErrorRate?.let { "errors ≤ ${trim(it * 100)}%" }
            return listOfNotNull(p99, errors).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Limits: ")
        }

        private fun trim(value: Double): String =
            if (value == Math.floor(value)) value.toLong().toString() else "%.1f".format(value)
    }
}

/** Who started a run, from a snapshot's `origin`. */
object SnapOrigin {
    /** "🤖 Agent" or "👤 User"; null when the snapshot does not say or names someone else. */
    fun label(origin: String?): String? {
        val source = AnnotationSource.values().firstOrNull { it.wire == origin } ?: return null
        return "${source.badge} ${source.label}"
    }
}
