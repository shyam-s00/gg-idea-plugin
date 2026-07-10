package dev.gopherglide.ggplugin.snap.ui

import kotlin.math.abs

/** Shared delta-formatting helpers for both the endpoint diff table and the schema diff tree. */
object DiffFormat {
    fun pctDelta(before: Double, after: Double): Double =
        if (before == 0.0) (if (after == 0.0) 0.0 else Double.POSITIVE_INFINITY) else (after - before) / before

    /** " ▲12%"/" ▼8pp"-style suffix; empty when the change is negligible. */
    fun deltaSuffix(delta: Double, unit: String): String {
        if (!delta.isFinite()) return " ▲∞$unit"
        if (abs(delta) < 0.0005) return ""
        val arrow = if (delta > 0) "▲" else "▼"
        return " $arrow" + "%.0f".format(abs(delta) * 100) + unit
    }
}
