package dev.gopherglide.ggplugin.ai

import java.io.File
import java.nio.file.Files

/** Keeps the files this plugin wrote pointing at the gg binary the IDE uses now. */
object AiPathSync {
    /**
     * The `.mcp.json` text with its command updated, or null when it should stay as it is: only an entry
     * that attaches to the daemon and runs a plugin-managed or vanished path is ours to move.
     */
    fun syncedMcp(text: String, desired: String, managedDir: String, exists: (String) -> Boolean = { File(it).exists() }): String? {
        val status = McpConfig.inspect(text)
        val entry = status.entry ?: return null
        if (status.health != McpHealth.LINKED || entry.command == desired) return null
        val absolute = File(entry.command).isAbsolute
        val ours = absolute && (!exists(entry.command) || File(entry.command).parent == managedDir)
        return if (ours) McpConfig.withCommand(text, desired) else null
    }

    /** Re-points the launcher and `.mcp.json` at [target]; true when something changed. */
    fun sync(target: AiTarget, launcher: LauncherFiles, managedDir: String): Boolean {
        var changed = false
        if (launcher.isInstalled()) changed = launcher.install(target.ggBinary) || changed
        if (Files.isRegularFile(target.mcpFile)) {
            val text = Files.readString(target.mcpFile)
            syncedMcp(text, target.mcpCommand, managedDir)?.let {
                Files.writeString(target.mcpFile, it)
                changed = true
            }
        }
        return changed
    }
}

/** Whether to make the one-time offer to connect AI agents. */
object AiOnboarding {
    fun shouldOffer(
        dontAsk: Boolean,
        offerShown: Boolean,
        linked: Boolean,
        hasGgFiles: Boolean,
        ggUsable: Boolean,
        status: AiStatus
    ): Boolean = !dontAsk && !offerShown && !linked && hasGgFiles && ggUsable && !status.anythingLinked
}
