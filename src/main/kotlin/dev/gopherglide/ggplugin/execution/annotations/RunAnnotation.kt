package dev.gopherglide.ggplugin.execution.annotations

import kotlin.math.abs

/** Something that happened during a run, positioned by seconds since the run started. */
sealed interface RunAnnotation {
    val elapsedSeconds: Double

    data class Mark(val label: String, override val elapsedSeconds: Double) : RunAnnotation
    data class BiasChange(val amount: Int, val cumulative: Int, override val elapsedSeconds: Double) : RunAnnotation
}

/** "+10", "−5" (true minus sign), "0". */
fun formatSigned(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${abs(value)}"
    else -> "0"
}

/** "m:ss" */
fun formatElapsed(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
