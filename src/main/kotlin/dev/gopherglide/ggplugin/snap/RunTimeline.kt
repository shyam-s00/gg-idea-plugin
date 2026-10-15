package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The marks and bias changes recorded in a snapshot, positioned on the run's length. */
data class RunTimeline(
    val durationSeconds: Double,
    val marks: List<RunAnnotation.Mark>,
    val biasChanges: List<RunAnnotation.BiasChange>,
    val finalBias: Int?
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (marks.isNotEmpty()) parts += "${marks.size} ${if (marks.size == 1) "mark" else "marks"}"
        if (biasChanges.isNotEmpty()) {
            parts += "${biasChanges.size} bias ${if (biasChanges.size == 1) "change" else "changes"}"
            finalBias?.let { parts += "final bias ${formatSigned(it)}" }
        }
        parts += "${formatElapsed(durationSeconds)} total"
        return parts.joinToString(" · ")
    }

    companion object {
        /** Null when the snapshot has no marks or bias events, or no usable length to place them on. */
        fun from(meta: SnapMeta?): RunTimeline? {
            if (meta == null) return null
            val marks = meta.marks.orEmpty().map { RunAnnotation.Mark(it.label, it.elapsedSeconds) }
            val changes = meta.biasEvents.orEmpty().map { RunAnnotation.BiasChange(it.amount, it.cumulative, it.elapsedSeconds) }
            if (marks.isEmpty() && changes.isEmpty()) return null
            val latest = (marks + changes).maxOf { it.elapsedSeconds }
            val duration = maxOf(wallClockSeconds(meta) ?: 0.0, latest)
            if (duration <= 0.0) return null
            return RunTimeline(duration, marks.sortedBy { it.elapsedSeconds }, changes.sortedBy { it.elapsedSeconds }, meta.finalBias ?: changes.lastOrNull()?.cumulative)
        }

        private fun wallClockSeconds(meta: SnapMeta): Double? = try {
            val millis = Duration.between(OffsetDateTime.parse(meta.startTime), OffsetDateTime.parse(meta.endTime)).toMillis()
            (millis / 1000.0).takeIf { it > 0.0 }
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
