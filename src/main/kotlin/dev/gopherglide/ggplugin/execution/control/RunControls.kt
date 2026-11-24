package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary

/** What the Run panel can ask of the running process. Replies are delivered on the EDT. */
interface RunControls {
    fun requestStop()

    fun sendBias(amount: Int, onReply: (ControlReply) -> Unit)

    /** [elapsedSeconds] is when the mark was asked for, in run time; null stamps it on arrival. */
    fun sendMark(label: String, elapsedSeconds: Double?, onReply: (ControlReply) -> Unit)

    fun sendHold(rps: Int, onReply: (ControlReply) -> Unit)

    fun sendRelease(onReply: (ControlReply) -> Unit)

    /** Null limits leave gg's own defaults. */
    fun startAutopilot(mode: String, maxP99Ms: Int?, maxErrorRate: Double?, onReply: (ControlReply) -> Unit)

    fun stopAutopilot(onReply: (ControlReply) -> Unit)

    /** Reads the installed binary and the latest release, for the older-binary notice. */
    fun loadBinaryInfo(onLoaded: (installed: InstalledBinary?, latest: GopherGlideVersion?) -> Unit)
}
