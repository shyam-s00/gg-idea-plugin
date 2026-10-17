package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary

/** What the Run tab needs from the IDE to show and fix the binary status. Callbacks are delivered on the EDT. */
interface BinaryActions {
    /** Reads the binary the next run would use and the latest release. */
    fun loadStatus(onLoaded: (installed: InstalledBinary?, latest: GopherGlideVersion?) -> Unit)

    /** Downloads the latest release as the plugin-managed binary; the error is null on success. */
    fun install(onDone: (error: Throwable?) -> Unit)

    fun openSettings()

    /** Calls [listener] when the binary or the settings that pick it change; the result stops it. */
    fun onChanged(listener: () -> Unit): () -> Unit

    object None : BinaryActions {
        override fun loadStatus(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {}
        override fun install(onDone: (Throwable?) -> Unit) {}
        override fun openSettings() {}
        override fun onChanged(listener: () -> Unit): () -> Unit = {}
    }
}
