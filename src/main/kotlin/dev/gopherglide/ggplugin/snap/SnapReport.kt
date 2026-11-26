package dev.gopherglide.ggplugin.snap

/** The `gg snap report` calls behind the export and copy actions. */
object SnapReport {
    enum class Format(val flag: String, val label: String) { MARKDOWN("md", "Markdown"), HTML("html", "HTML") }

    /** The format gg's own rule gives for [fileName]: `.html` or `.htm` is HTML, anything else Markdown. */
    fun formatFor(fileName: String): Format =
        if (fileName.lowercase().let { it.endsWith(".html") || it.endsWith(".htm") }) Format.HTML else Format.MARKDOWN

    fun exportArgs(snapPath: String, outPath: String): List<String> =
        listOf("snap", "report", snapPath, "--format", formatFor(outPath).flag, "--out", outPath)

    /** Prints the Markdown report on stdout. */
    fun markdownArgs(snapPath: String): List<String> =
        listOf("snap", "report", snapPath, "--format", Format.MARKDOWN.flag)

    /** A file name for the report that is safe on every platform, based on the tag or else the id. */
    fun defaultFileName(snap: SnapModel, format: Format = Format.MARKDOWN): String {
        val base = snap.meta?.tag?.ifBlank { null } ?: snap.id.ifBlank { "snapshot" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_") + "-report.${format.flag}"
    }
}
