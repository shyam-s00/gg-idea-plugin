package dev.gopherglide.ggplugin.services

/** The `gg` binary a run uses, where it came from, and its version when it could be read. */
data class InstalledBinary(val path: String, val version: GopherGlideVersion?, val source: Source) {
    enum class Source { PLUGIN, CUSTOM, SYSTEM }
}
