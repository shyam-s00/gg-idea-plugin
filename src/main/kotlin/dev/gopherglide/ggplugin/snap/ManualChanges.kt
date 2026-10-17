package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.execution.annotations.formatSigned

/** Words for the notice shown when a compared snapshot had marks or bias changes. */
object ManualChanges {
    private data class Counts(val marks: Int, val bias: Int, val finalBias: Int)

    private fun counts(meta: SnapMeta?): Counts {
        val events = meta?.biasEvents.orEmpty()
        return Counts(meta?.marks.orEmpty().size, events.size, meta?.finalBias ?: events.lastOrNull()?.cumulative ?: 0)
    }

    /** What each side recorded, plus a caveat when bias may have skewed the numbers. */
    data class Notice(val summary: String, val caveat: String?)

    /** Null when neither snapshot has marks or bias changes. */
    fun describe(baseline: SnapMeta?, target: SnapMeta?): Notice? {
        val a = counts(baseline)
        val b = counts(target)
        if (a.marks + a.bias + b.marks + b.bias == 0) return null
        val caveat = "Peak RPS and latency may not be like for like.".takeIf { a.bias + b.bias > 0 }
        return Notice("baseline had ${phrase(a)}; target had ${phrase(b)}.", caveat)
    }

    private fun phrase(c: Counts): String {
        if (c.bias == 0 && c.marks == 0) return "no bias changes or marks"
        val bias = if (c.bias > 0) "${c.bias} bias ${plural(c.bias, "change", "changes")} (final ${formatSigned(c.finalBias)})" else "no bias changes"
        val marks = if (c.marks > 0) "${c.marks} ${plural(c.marks, "mark", "marks")}" else "no marks"
        return "$bias and $marks"
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
}
