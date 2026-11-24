package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned

/**
 * One line of the manual-changes list. [total] is the running bias after a bias change, the pinned rate
 * for a hold, and empty for marks.
 */
data class ManualChangeRow(
    val time: String,
    val kind: Kind,
    val detail: String,
    val total: String,
    val source: AnnotationSource = AnnotationSource.USER
) {
    enum class Kind { MARK, BIAS, HOLD, RELEASE }

    companion object {
        fun from(event: RunAnnotation): ManualChangeRow = when (event) {
            is RunAnnotation.Mark -> ManualChangeRow(formatElapsed(event.elapsedSeconds), Kind.MARK, event.label, "", event.source)
            is RunAnnotation.BiasChange -> ManualChangeRow(
                formatElapsed(event.elapsedSeconds), Kind.BIAS, "${formatSigned(event.amount)} RPS", "total ${formatSigned(event.cumulative)}",
                event.source
            )
            is RunAnnotation.HoldChange ->
                if (event.rps != null) {
                    ManualChangeRow(formatElapsed(event.elapsedSeconds), Kind.HOLD, "Pinned at ${event.rps} RPS", "hold ${event.rps}", event.source)
                } else {
                    ManualChangeRow(formatElapsed(event.elapsedSeconds), Kind.RELEASE, "Hold released", "schedule", event.source)
                }
        }
    }
}

/** A labelled figure beside the list, such as "BIAS ACTIVE 1:46 · 71% of the run". */
data class ChangeTile(val label: String, val value: String, val detail: String)

object ChangeTiles {
    fun from(stats: ChangeStats, durationSeconds: Double): List<ChangeTile> {
        fun percent(seconds: Double) = Math.round(seconds / durationSeconds * 100)
        return listOf(
            ChangeTile("FINAL BIAS", formatSigned(stats.finalBias), "at the end of the run"),
            ChangeTile("PEAK BIAS", formatSigned(stats.peakBias), "reached at ${formatElapsed(stats.peakAtSeconds)}"),
            ChangeTile("BIAS ACTIVE", formatElapsed(stats.activeSeconds), "${percent(stats.activeSeconds)}% of the run"),
            ChangeTile("FIRST CHANGE", formatElapsed(stats.firstChangeSeconds), "${percent(stats.firstChangeSeconds)}% into the run")
        )
    }
}
