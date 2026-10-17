package dev.gopherglide.ggplugin.services

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Installed binary plus the latest release, shared by the Run tab, the run notice and the startup check.
 * The release lookup is remembered (a failed one for a shorter time) so an offline machine is not retried
 * on every panel load.
 */
class BinaryStatusCache(
    private val resolvePath: () -> String?,
    private val inspect: (path: String) -> InstalledBinary,
    private val fetchLatest: () -> GopherGlideVersion?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val foundTtlMillis: Long = 15 * 60_000L,
    private val missTtlMillis: Long = 5 * 60_000L
) {
    data class Snapshot(val installed: InstalledBinary?, val latest: GopherGlideVersion?)

    private val lock = Any()
    private var latestAt: Long? = null
    private var latestValue: GopherGlideVersion? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Blocking: reads the binary the next run would use. Call off the EDT. */
    fun load(): Snapshot = load(resolvePath())

    /** Blocking: like [load] for a specific binary, e.g. the one a run already started with. */
    fun load(binaryPath: String?): Snapshot = Snapshot(binaryPath?.let(inspect), latest())

    /** Blocking: only the installed binary, with no release lookup. */
    fun installed(): InstalledBinary? = resolvePath()?.let(inspect)

    fun latest(): GopherGlideVersion? = synchronized(lock) {
        val ttl = if (latestValue != null) foundTtlMillis else missTtlMillis
        if (latestAt.let { it == null || nowMillis() - it >= ttl }) {
            latestValue = fetchLatest()
            latestAt = nowMillis()
        }
        latestValue
    }

    /** Called when the binary or the settings that pick it may have changed. */
    fun notifyChanged() = listeners.forEach { it() }

    /** Returns a function that removes the listener. */
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
