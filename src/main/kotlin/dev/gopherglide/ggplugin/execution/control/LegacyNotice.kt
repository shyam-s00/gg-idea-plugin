package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary

enum class UpgradeButton { HIDDEN, WAIT_FOR_RUN_END, AVAILABLE, RUNNING }

enum class UpgradeProgress { IDLE, DOWNLOADING, DONE }

data class LegacyNotice(val text: String, val button: UpgradeButton, val buttonHint: String? = null)

/** The explanation shown, in place of the controls, for a `gg` binary without live control. */
object LegacyNotices {
    private val minimum = GopherGlideVersion.MIN_LIVE_CONTROL

    fun build(
        installed: InstalledBinary?,
        latest: GopherGlideVersion?,
        runActive: Boolean,
        progress: UpgradeProgress
    ): LegacyNotice {
        val need = "Live Control needs gg $minimum+"
        if (installed == null) return LegacyNotice("$need.", UpgradeButton.HIDDEN)
        val installedText = installed.version?.toString() ?: "unknown version"

        if (installed.source != InstalledBinary.Source.PLUGIN) {
            val advice = if (installed.source == InstalledBinary.Source.CUSTOM) {
                "Update that binary, or clear the custom path in Settings > Tools > Gopher Glide."
            } else {
                "Update that binary."
            }
            return LegacyNotice("$need. Installed: $installedText at ${installed.path}. $advice", UpgradeButton.HIDDEN)
        }

        val base = "$need (installed: $installedText)."
        if (progress == UpgradeProgress.DONE) {
            return LegacyNotice("gg ${latest ?: "update"} is installed. Live Control will be available from the next run.", UpgradeButton.HIDDEN)
        }
        if (progress == UpgradeProgress.DOWNLOADING) return LegacyNotice("$base Upgrading…", UpgradeButton.RUNNING)
        if (latest == null) return LegacyNotice("$base Could not check for a newer release.", UpgradeButton.HIDDEN)
        if (!latest.coreAtLeast(minimum)) return LegacyNotice("$base No release with Live Control is available yet.", UpgradeButton.HIDDEN)
        return if (runActive) {
            LegacyNotice(base, UpgradeButton.WAIT_FOR_RUN_END, "Upgrade is available when the run ends")
        } else {
            LegacyNotice(base, UpgradeButton.AVAILABLE)
        }
    }
}
