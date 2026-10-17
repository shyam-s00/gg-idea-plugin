package dev.gopherglide.ggplugin.services

import java.util.concurrent.ConcurrentHashMap

/** Remembers each binary's version until the file changes, so `--version` is not spawned on every run. */
class BinaryVersionCache(
    private val readOutput: (path: String) -> String?,
    private val stampOf: (path: String) -> Stamp?
) {
    data class Stamp(val modifiedMillis: Long, val sizeBytes: Long)

    private class Entry(val stamp: Stamp, val version: GopherGlideVersion?)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun versionOf(path: String): GopherGlideVersion? {
        val stamp = stampOf(path) ?: return null
        entries[path]?.takeIf { it.stamp == stamp }?.let { return it.version }
        val version = GopherGlideVersion.parse(readOutput(path))
        entries[path] = Entry(stamp, version)
        return version
    }
}
