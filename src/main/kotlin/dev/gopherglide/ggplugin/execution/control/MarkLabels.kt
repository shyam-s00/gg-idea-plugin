package dev.gopherglide.ggplugin.execution.control

object MarkLabels {
    const val MAX_LENGTH = 200

    /** Trims, collapses whitespace and caps at [MAX_LENGTH] characters; null when nothing is left. */
    fun normalize(raw: String): String? {
        val label = raw.trim().replace(Regex("\\s+"), " ")
        if (label.isEmpty()) return null
        if (label.codePointCount(0, label.length) <= MAX_LENGTH) return label
        return label.substring(0, label.offsetByCodePoints(0, MAX_LENGTH))
    }
}
