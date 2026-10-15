package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary

/** What the Run panel can ask of the running process. Replies are delivered on the EDT. */
interface RunControls {
    fun requestStop()

    fun sendBias(amount: Int, onReply: (ControlReply) -> Unit)

    fun sendMark(label: String, onReply: (ControlReply) -> Unit)

    /** Reads the installed binary and the latest release, for the older-binary notice. */
    fun loadBinaryInfo(onLoaded: (installed: InstalledBinary?, latest: GopherGlideVersion?) -> Unit)

    /** Downloads the latest release; the error is null on success. */
    fun upgradeBinary(onDone: (error: Throwable?) -> Unit)
}
