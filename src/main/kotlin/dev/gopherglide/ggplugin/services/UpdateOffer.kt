package dev.gopherglide.ggplugin.services

/** What the update check remembers between IDE starts. */
interface UpdateCheckState {
    var enabled: Boolean
    var lastCheckMillis: Long
    var skippedVersion: String
}

data class UpdateOffer(val installed: GopherGlideVersion, val latest: GopherGlideVersion)

/**
 * Decides whether to tell the user about a newer `gg` release. Quiet by design: any doubt (no binary,
 * unreadable version, offline, already current, skipped) yields no offer.
 */
class UpdatePolicy(
    private val state: UpdateCheckState,
    private val installed: () -> InstalledBinary?,
    private val latest: () -> GopherGlideVersion?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val intervalMillis: Long = 24 * 60 * 60_000L
) {
    /** Blocking, may use the network. The last-check time is stored only once a release answer arrived. */
    fun check(): UpdateOffer? {
        if (!state.enabled) return null
        val now = nowMillis()
        if (now - state.lastCheckMillis in 0 until intervalMillis) return null

        val binary = installed() ?: return null
        // A custom path is the user's to maintain; a download would not replace it.
        if (binary.source == InstalledBinary.Source.CUSTOM) return null
        val current = binary.version ?: return null

        val newest = latest() ?: return null
        state.lastCheckMillis = now
        if (current.coreAtLeast(newest)) return null
        if (state.skippedVersion == newest.toString()) return null
        return UpdateOffer(current, newest)
    }
}
