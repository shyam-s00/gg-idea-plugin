package dev.gopherglide.ggplugin.snap.ui

enum class DiffSeverity { NEUTRAL, IMPROVEMENT, WARNING, REGRESSION, ADDED, REMOVED }

data class DiffCell(val text: String, val severity: DiffSeverity = DiffSeverity.NEUTRAL) {
    override fun toString(): String = text
}
