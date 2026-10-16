package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary

enum class NoticeButton { NONE, UPGRADE, INSTALL, OPEN_SETTINGS }

enum class InstallProgress { IDLE, DOWNLOADING, DONE }

/** [quiet] marks a plain status line instead of a call-out; [settingsLink] adds a secondary "Open Settings". */
data class BinaryNotice(
    val text: String,
    val button: NoticeButton = NoticeButton.NONE,
    val buttonLabel: String = "",
    val buttonEnabled: Boolean = true,
    val hint: String? = null,
    val settingsLink: Boolean = true,
    val quiet: Boolean = false
)

/** What the Run tab says about the `gg` binary: before a run, and in place of the controls when a run has none. */
object BinaryNotices {
    private val minimum = GopherGlideVersion.MIN_LIVE_CONTROL
    private val need = "Live Control needs gg $minimum+"

    /** Before any run; null when nothing is known about the binary, so no claim is made. */
    fun idle(installed: InstalledBinary?, latest: GopherGlideVersion?, progress: InstallProgress): BinaryNotice? {
        val version = installed?.version ?: return null
        if (version.supportsLiveControl) {
            return BinaryNotice("Live Control available (gg $version)", settingsLink = false, quiet = true)
        }
        return older(installed, latest, runActive = false, progress)
    }

    /** For a run whose binary did not offer live control. */
    fun forRun(installed: InstalledBinary?, latest: GopherGlideVersion?, runActive: Boolean, progress: InstallProgress): BinaryNotice =
        if (installed == null) BinaryNotice("$need.") else older(installed, latest, runActive, progress)

    private fun older(installed: InstalledBinary, latest: GopherGlideVersion?, runActive: Boolean, progress: InstallProgress): BinaryNotice {
        val installedText = installed.version?.toString() ?: "unknown version"
        if (installed.source == InstalledBinary.Source.CUSTOM) {
            return BinaryNotice(
                "$need. Installed: $installedText at ${installed.path}. Update that binary, or clear the custom path in Settings.",
                NoticeButton.OPEN_SETTINGS, "Open Settings", settingsLink = false
            )
        }
        val plugin = installed.source == InstalledBinary.Source.PLUGIN
        val base = if (plugin) "$need (installed: $installedText)." else "$need. Installed: $installedText at ${installed.path}."
        val progressLabel = if (plugin) "Upgrading…" else "Installing…"

        if (progress == InstallProgress.DONE) {
            return BinaryNotice("gg ${latest ?: "update"} is installed. Live Control will be available from the next run.")
        }
        if (progress == InstallProgress.DOWNLOADING) {
            return BinaryNotice("$base $progressLabel", if (plugin) NoticeButton.UPGRADE else NoticeButton.INSTALL, progressLabel, buttonEnabled = false)
        }
        if (latest == null) return BinaryNotice("$base Could not check for a newer release.")
        if (!latest.coreAtLeast(minimum)) return BinaryNotice("$base No release with Live Control is available yet.")
        if (plugin) {
            val label = "Upgrade gg"
            return if (runActive) {
                BinaryNotice(base, NoticeButton.UPGRADE, label, buttonEnabled = false, hint = "Upgrade is available when the run ends")
            } else {
                BinaryNotice(base, NoticeButton.UPGRADE, label)
            }
        }
        return BinaryNotice(base, NoticeButton.INSTALL, "Install gg $latest", hint = "Adds a copy the plugin uses first; the gg on your PATH is not changed.")
    }
}
