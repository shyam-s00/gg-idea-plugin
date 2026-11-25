package dev.gopherglide.ggplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import java.awt.Component

/** Lets an action that needs `gg` v1.5.0 or newer proceed, or offers the way to a binary that has it. */
object BinaryFeatureGate {
    const val AUTOPILOT = "The built-in Autopilot"
    const val REPORTS = "Mission reports"
    const val AI_SETUP = "Connecting AI agents"

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
    fun ensure(project: Project, feature: String, parent: Component? = null, onReady: () -> Unit) {
        BinaryStatus.instance.loadAsync { snapshot ->
            when (val decision = decide(snapshot.installed, snapshot.latest)) {
                Decision.Proceed -> onReady()
                is Decision.Upgrade -> offerUpgrade(project, parent, feature, decision, onReady)
                is Decision.UpdateCustom -> info(
                    project, parent,
                    "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+. " +
                        "Installed: ${decision.installed.version} at ${decision.installed.path}. " +
                        "Update that binary, or clear the custom path in Settings.",
                    "Needs a Newer gg"
                )
                is Decision.NoRelease -> info(
                    project, parent,
                    "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+ " +
                        "(installed: ${decision.installed.version}), and no such release is available yet.",
                    "Needs a Newer gg"
                )
            }
        }
    }

    /** Runs [action] directly, or through [ensure] when the run needs the autopilot. */
    fun runWith(project: Project, needsAutopilot: Boolean, action: () -> Unit) {
        if (needsAutopilot) ensure(project, AUTOPILOT, onReady = action) else action()
    }

    private fun info(project: Project, parent: Component?, message: String, title: String) {
        if (parent != null) Messages.showInfoMessage(parent, message, title) else Messages.showInfoMessage(project, message, title)
    }

    private fun offerUpgrade(project: Project, parent: Component?, feature: String, decision: Decision.Upgrade, onReady: () -> Unit) {
        val message = "$feature needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+ " +
            "(installed: ${decision.installed.version}).\n\nDownload gg ${decision.latest} for the plugin to use?"
        val yes = "Install gg ${decision.latest}"
        val builder = MessageDialogBuilder.yesNo("Needs a Newer gg", message).yesText(yes).noText("Cancel")
        val accepted = if (parent != null) builder.ask(parent) else builder.ask(project)
        if (!accepted) return
        val modality = ModalityState.current()
        GopherGlideNotifications.downloadWithProgress(project).whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater({
                if (error == null) {
                    onReady()
                } else {
                    val failure = "Could not download gg: ${error.message}"
                    if (parent != null) Messages.showErrorDialog(parent, failure, "Needs a Newer gg")
                    else Messages.showErrorDialog(project, failure, "Needs a Newer gg")
                }
            }, modality)
        }
    }
}
