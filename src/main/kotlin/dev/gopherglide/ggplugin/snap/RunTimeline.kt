package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.abs

/** What the bias amounted to over a run, worked out from the recorded bias changes. */
data class ChangeStats(
    val finalBias: Int,
    val peakBias: Int,
    val peakAtSeconds: Double,
    val activeSeconds: Double,
    val firstChangeSeconds: Double
)

/** The marks and bias changes recorded in a snapshot, with the run's length when it is known. */
data class RunTimeline(
    val durationSeconds: Double,
    val marks: List<RunAnnotation.Mark>,
    val biasChanges: List<RunAnnotation.BiasChange>,
    val finalBias: Int?
) {
    /** Every event in time order; a mark sorts before a bias change at the same instant. */
    val events: List<RunAnnotation> = (marks + biasChanges).sortedBy { it.elapsedSeconds }

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (biasChanges.isNotEmpty()) {
            val count = "${biasChanges.size} bias ${if (biasChanges.size == 1) "change" else "changes"}"
            parts += finalBias?.let { "$count (final ${formatSigned(it)})" } ?: count
        }
        if (marks.isNotEmpty()) parts += "${marks.size} ${if (marks.size == 1) "mark" else "marks"}"
        if (durationSeconds > 0.0) parts += "${formatElapsed(durationSeconds)} run"
        return parts.joinToString(" · ")
    }

    /** Null without bias changes or a run length, where there is nothing to total up. */
    fun stats(): ChangeStats? {
        if (biasChanges.isEmpty() || durationSeconds <= 0.0) return null
        var level = 0
        var since: Double? = null
        var active = 0.0
        var peak = 0
        var peakAt = 0.0
        for (change in biasChanges.sortedBy { it.elapsedSeconds }) {
            val at = change.elapsedSeconds
            if (level == 0 && change.cumulative != 0) since = at
            if (level != 0 && change.cumulative == 0) {
                active += at - (since ?: at)
                since = null
            }
            level = change.cumulative
            if (abs(level) > abs(peak)) {
                peak = level
                peakAt = at
            }
        }
        since?.let { active += (durationSeconds - it).coerceAtLeast(0.0) }
        return ChangeStats(
            finalBias = finalBias ?: level,
            peakBias = peak,
            peakAtSeconds = peakAt,
            activeSeconds = active.coerceAtMost(durationSeconds),
            firstChangeSeconds = biasChanges.minOf { it.elapsedSeconds }
        )
    }

    companion object {
        /** Null when the snapshot has no marks or bias events. */
        fun from(meta: SnapMeta?): RunTimeline? {
            if (meta == null) return null
            val marks = meta.marks.orEmpty().map { RunAnnotation.Mark(it.label, it.elapsedSeconds) }
            val changes = meta.biasEvents.orEmpty().map { RunAnnotation.BiasChange(it.amount, it.cumulative, it.elapsedSeconds) }
            if (marks.isEmpty() && changes.isEmpty()) return null
            val latest = (marks + changes).maxOf { it.elapsedSeconds }
            val duration = maxOf(wallClockSeconds(meta) ?: 0.0, latest)
            return RunTimeline(duration, marks.sortedBy { it.elapsedSeconds }, changes.sortedBy { it.elapsedSeconds }, meta.finalBias ?: changes.lastOrNull()?.cumulative)
        }

        /**
         * The one-line row for a snapshot without a timeline. Only gg v1.3+ writes `final_bias` (even when 0),
         * so its absence means the events were never recorded. Null when events exist.
         */
        fun absenceNote(meta: SnapMeta?): String? {
            if (meta == null || meta.marks.orEmpty().isNotEmpty() || meta.biasEvents.orEmpty().isNotEmpty()) return null
            return if (meta.finalBias != null) "No marks or bias changes during this run" else "Marks and bias changes: not recorded (needs gg v1.3.0+)"
        }

        private fun wallClockSeconds(meta: SnapMeta): Double? = try {
            val millis = Duration.between(OffsetDateTime.parse(meta.startTime), OffsetDateTime.parse(meta.endTime)).toMillis()
            (millis / 1000.0).takeIf { it > 0.0 }
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
