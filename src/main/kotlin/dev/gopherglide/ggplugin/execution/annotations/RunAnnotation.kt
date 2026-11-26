package dev.gopherglide.ggplugin.execution.annotations

import kotlin.math.abs

/** Who caused an annotation. */
enum class AnnotationSource(val wire: String, val badge: String, val label: String) {
    USER("user", "👤", "User"),
    AGENT("agent", "🤖", "Agent"),
    AUTOPILOT("autopilot", "⚡", "Autopilot");

    companion object {
        /** Anything gg does not name, including a missing value, is the user. */
        fun fromWire(value: String?): AnnotationSource = values().firstOrNull { it.wire == value } ?: USER
    }
}

/** Something that happened during a run, positioned by seconds since the run started. */
sealed interface RunAnnotation {
    val elapsedSeconds: Double
    val source: AnnotationSource

    data class Mark(
        val label: String,
        override val elapsedSeconds: Double,
        override val source: AnnotationSource = AnnotationSource.USER
    ) : RunAnnotation

    data class BiasChange(
        val amount: Int,
        val cumulative: Int,
        override val elapsedSeconds: Double,
        override val source: AnnotationSource = AnnotationSource.USER
    ) : RunAnnotation

    /** A hold pinning the rate at [rps], or null when it was released. */
    data class HoldChange(
        val rps: Int?,
        override val elapsedSeconds: Double,
        override val source: AnnotationSource = AnnotationSource.USER
    ) : RunAnnotation
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
