package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.formatSigned

/** Words for the notice shown when a compared snapshot had marks, bias changes, holds or an autopilot. */
object ManualChanges {
    private data class Counts(val marks: Int, val bias: Int, val holds: Int, val finalBias: Int, val autopilot: String?) {
        val any: Boolean get() = marks + bias + holds > 0 || autopilot != null
    }

    private fun counts(meta: SnapMeta?): Counts {
        val events = meta?.biasEvents.orEmpty()
        return Counts(
            marks = meta?.marks.orEmpty().size,
            bias = events.size,
            holds = meta?.holdEvents.orEmpty().count { it.released != true },
            finalBias = meta?.finalBias ?: events.lastOrNull()?.cumulative ?: 0,
            autopilot = meta?.autopilot?.mode?.takeIf { it.isNotBlank() }
        )
    }

    /** What each side recorded, plus a caveat when the rate may have been steered. */
    data class Notice(val summary: String, val caveat: String?)

    /** Null when neither snapshot has marks, bias changes, holds or an autopilot. */
    fun describe(baseline: SnapMeta?, target: SnapMeta?): Notice? {
        val a = counts(baseline)
        val b = counts(target)
        if (!a.any && !b.any) return null
        val caveat = when {
            a.autopilot != null || b.autopilot != null -> "The autopilot steered the rate, so peak RPS and latency may not be like for like."
            a.bias + b.bias + a.holds + b.holds > 0 -> "Peak RPS and latency may not be like for like."
            else -> null
        }
        return Notice("baseline had ${phrase(a)}; target had ${phrase(b)}.", caveat)
    }

    private fun phrase(c: Counts): String {
        val autopilot = c.autopilot?.let { ", with autopilot $it" }.orEmpty()
        if (c.bias == 0 && c.marks == 0 && c.holds == 0) return "no bias changes or marks$autopilot"
        val bias = if (c.bias > 0) "${c.bias} bias ${plural(c.bias, "change", "changes")} (final ${formatSigned(c.finalBias)})" else "no bias changes"
        val marks = if (c.marks > 0) "${c.marks} ${plural(c.marks, "mark", "marks")}" else "no marks"
        val holds = if (c.holds > 0) "${c.holds} ${plural(c.holds, "hold", "holds")}" else null
        return (if (holds != null) "$bias, $holds and $marks" else "$bias and $marks") + autopilot
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
}
