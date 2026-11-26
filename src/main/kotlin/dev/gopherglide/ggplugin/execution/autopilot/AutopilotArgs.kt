package dev.gopherglide.ggplugin.execution.autopilot

import java.math.BigDecimal

/** SLO limits for a guard, already in the units gg takes. */
data class SloLimits(val maxP99Ms: Int, val maxErrorRate: BigDecimal)

/** Parses what the launch dialogs accept and builds the matching `gg` flags. */
object AutopilotArgs {
    const val MIN_FIND_CEILING_SECONDS = 30

    private val percent = Regex("""\d+(\.\d+)?""")
    private val latency = Regex("""(\d+(?:\.\d+)?)(ms|s)?""")
    private val durationPart = Regex("""(\d+(?:\.\d+)?)(ms|s|m|h)""")

    /** "250", "250ms", "0.5s" → milliseconds; null when not a positive latency. */
    fun parseLatencyMillis(text: String): Int? {
        val match = latency.matchEntire(text.trim()) ?: return null
        val value = match.groupValues[1].toBigDecimal()
        val millis = if (match.groupValues[2] == "s") value.multiply(BigDecimal(1000)) else value
        return millis.toInt().takeIf { it >= 1 }
    }

    /** "1.0" (a percentage) → 0.01 (the fraction gg takes); null outside (0, 100]. */
    fun parseErrorFraction(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (!percent.matches(trimmed)) return null
        val value = BigDecimal(trimmed)
        if (value.signum() <= 0 || value > BigDecimal(100)) return null
        return value.movePointLeft(2).stripTrailingZeros()
    }

    /** Go-style durations such as "90s", "5m" or "1h30m"; null when malformed or zero. */
    fun parseDurationSeconds(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        var rest = trimmed
        var total = 0.0
        while (rest.isNotEmpty()) {
            val part = durationPart.matchAt(rest, 0) ?: return null
            val value = part.groupValues[1].toDouble()
            total += value * when (part.groupValues[2]) {
                "ms" -> 0.001
                "s" -> 1.0
                "m" -> 60.0
                else -> 3600.0
            }
            rest = rest.substring(part.value.length)
        }
        return total.takeIf { it > 0.0 }
    }

    enum class Field { START_RPS, MAX_RPS, P99, ERROR_RATE, DURATION }

    data class Problem(val field: Field, val message: String)

    /** The first problem with the two limits, or null when both parse. */
    fun checkLimits(p99Text: String, errorPercentText: String): Problem? = when {
        parseLatencyMillis(p99Text) == null -> Problem(Field.P99, "Max P99 must be a latency such as 250ms or 1s")
        parseErrorFraction(errorPercentText) == null ->
            Problem(Field.ERROR_RATE, "Max errors must be a percentage above 0 and up to 100")
        else -> null
    }

    /** The first problem with a find-ceiling form, or null when it can run. */
    fun checkFindCeiling(
        startText: String, maxText: String, p99Text: String, errorPercentText: String, durationText: String
    ): Problem? {
        val start = startText.trim().toIntOrNull()?.takeIf { it >= 1 }
            ?: return Problem(Field.START_RPS, "Start RPS must be a whole number of at least 1")
        val max = maxText.trim().toIntOrNull() ?: return Problem(Field.MAX_RPS, "Max RPS must be a whole number")
        if (max < start) return Problem(Field.MAX_RPS, "Max RPS must not be below Start RPS")
        checkLimits(p99Text, errorPercentText)?.let { return it }
        val seconds = parseDurationSeconds(durationText)
            ?: return Problem(Field.DURATION, "Max duration must look like 90s, 5m or 1h")
        if (seconds < MIN_FIND_CEILING_SECONDS) {
            return Problem(Field.DURATION, "Finding a ceiling needs at least ${MIN_FIND_CEILING_SECONDS}s")
        }
        return null
    }

    fun sloGuard(limits: SloLimits): List<String> =
        listOf("--autopilot", "slo-guard") + limitFlags(limits)

    fun findCeiling(startRps: Int, maxRps: Int, limits: SloLimits, duration: String): List<String> =
        listOf(
            "--autopilot", "find-ceiling",
            "--start-rps", startRps.toString(),
            "--max-rps", maxRps.toString(),
            "--duration", duration.trim()
        ) + limitFlags(limits)

    private fun limitFlags(limits: SloLimits) =
        listOf("--max-p99", "${limits.maxP99Ms}ms", "--max-error-rate", limits.maxErrorRate.toPlainString())
}
