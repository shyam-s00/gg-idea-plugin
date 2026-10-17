package dev.gopherglide.ggplugin.services

/** A `gg` release number such as 1.3.0 or v1.3.0-dev. */
data class GopherGlideVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<GopherGlideVersion> {

    /** Full semver order: a pre-release sorts before its release. */
    override fun compareTo(other: GopherGlideVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    /** Compares major.minor.patch only, so `1.3.0-dev` counts as 1.3.0. */
    fun coreAtLeast(minimum: GopherGlideVersion): Boolean =
        compareValuesBy(this, minimum, { it.major }, { it.minor }, { it.patch }) >= 0

    val supportsLiveControl: Boolean get() = coreAtLeast(MIN_LIVE_CONTROL)

    override fun toString(): String = "v$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        val MIN_LIVE_CONTROL = GopherGlideVersion(1, 3, 0)

        private val labelled = Regex("""Version:\s*(\S+)""")
        private val semver = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.\-]+))?""")

        /** Reads a tag, a bare number, or the full `--version` output; null when no version is found. */
        fun parse(text: String?): GopherGlideVersion? {
            if (text.isNullOrBlank()) return null
            val candidate = labelled.find(text)?.groupValues?.get(1) ?: text
            val match = semver.find(candidate) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return GopherGlideVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
                pre.ifEmpty { null }
            )
        }
    }
}
