package dev.gopherglide.ggplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications

/** Lets an action that needs `gg` v1.5.0 or newer proceed, or offers the way to a binary that has it. */
object BinaryFeatureGate {
    const val AUTOPILOT = "The built-in Autopilot"
    const val REPORTS = "Mission reports"

    sealed interface Decision {
        data object Proceed : Decision
        /** The plugin can download a release that has the autopilot. */
        data class Upgrade(val installed: InstalledBinary, val latest: GopherGlideVersion) : Decision
        /** The user's own binary is too old; the plugin does not replace it. */
        data class UpdateCustom(val installed: InstalledBinary) : Decision
        data class NoRelease(val installed: InstalledBinary) : Decision
    }

    /** An unknown binary or version proceeds: gg itself reports a flag it does not know. */
    fun decide(installed: InstalledBinary?, latest: GopherGlideVersion?): Decision {
        val version = installed?.version ?: return Decision.Proceed
        if (version.supportsAutopilotAndMcp) return Decision.Proceed
        return when {
            installed.source == InstalledBinary.Source.CUSTOM -> Decision.UpdateCustom(installed)
            latest == null || !latest.supportsAutopilotAndMcp -> Decision.NoRelease(installed)
            else -> Decision.Upgrade(installed, latest)
        }
    }

    /** True when the binary already known to the plugin is too old; used to disable options up front. */
    fun knownTooOld(): Boolean =
        BinaryStatus.instance.installed()?.version?.supportsAutopilotAndMcp == false

    /** Runs [onReady] once the binary has [feature], after offering an upgrade when it does not. */
    fun ensure(project: Project, feature: String, onReady: () -> Unit) {
        BinaryStatus.instance.loadAsync { snapshot ->
            when (val decision = decide(snapshot.installed, snapshot.latest)) {
                Decision.Proceed -> onReady()
                is Decision.Upgrade -> offerUpgrade(project, feature, decision, onReady)
                is Decision.UpdateCustom -> Messages.showInfoMessage(
                    project,
                    "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+. " +
                        "Installed: ${decision.installed.version} at ${decision.installed.path}. " +
                        "Update that binary, or clear the custom path in Settings.",
                    "Needs a Newer gg"
                )
                is Decision.NoRelease -> Messages.showInfoMessage(
                    project,
                    "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+ " +
                        "(installed: ${decision.installed.version}), and no such release is available yet.",
                    "Needs a Newer gg"
                )
            }
        }
    }

    /** Runs [action] directly, or through [ensure] when the run needs the autopilot. */
    fun runWith(project: Project, needsAutopilot: Boolean, action: () -> Unit) {
        if (needsAutopilot) ensure(project, AUTOPILOT, action) else action()
    }

    private fun offerUpgrade(project: Project, feature: String, decision: Decision.Upgrade, onReady: () -> Unit) {
        val answer = Messages.showYesNoDialog(
            project,
            "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+ " +
                "(installed: ${decision.installed.version}).\n\nDownload gg ${decision.latest} for the plugin to use?",
            "Needs a Newer gg",
            "Install gg ${decision.latest}",
            "Cancel",
            Messages.getQuestionIcon()
        )
        if (answer != Messages.YES) return
        GopherGlideNotifications.downloadWithProgress(project).whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater {
                if (error == null) {
                    onReady()
                } else {
                    Messages.showErrorDialog(project, "Could not download gg: ${error.message}", "Needs a Newer gg")
                }
            }
        }
    }
}
