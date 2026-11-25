package dev.gopherglide.ggplugin.ai

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryFeatureGate
import dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

/** What the Run tab's AI Agents button says for a project's state. */
object AiButton {
    data class Look(val text: String, val description: String, val icon: javax.swing.Icon)

    fun look(linked: Boolean, daemon: GgMcpDaemonService.State?): Look = when {
        !linked -> Look("AI Agents: Set Up…", "Connect AI agents (MCP, JetBrains Air) to Gopher Glide", AllIcons.Nodes.Plugin)
        daemon == GgMcpDaemonService.State.RUNNING ->
            Look("AI Agents: Ready", "AI agents are connected and the shared daemon is running", AllIcons.RunConfigurations.TestPassed)
        else -> Look("AI Agents: Daemon Off", "Agents are connected, but the shared daemon is not running, so their runs will not show here", AllIcons.General.Warning)
    }
}

const val RUN_TOOLBAR_PLACE = "GopherGlideRunToolbar"

/** The Run tab's `AI Agents` button and the menu entry: shows whether agents are connected, and opens setup. */
class ConfigureAiAgentsAction : AnAction("AI Agents…", "Connect AI agents to Gopher Glide", AllIcons.Nodes.Plugin), CustomComponentAction {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        if (project == null || e.place != RUN_TOOLBAR_PLACE) return
        val linked = GopherGlideProjectSettings.getInstance(project).aiLinked
        val look = AiButton.look(linked, project.getServiceIfCreated(GgMcpDaemonService::class.java)?.state)
        e.presentation.text = look.text
        e.presentation.description = look.description
        e.presentation.icon = look.icon
    }

    override fun actionPerformed(e: AnActionEvent) {
        AiSetup.open(e.project ?: return)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
        ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
}

/** Gathers a project's AI status, shows the setup dialog, and applies what the user chose. */
object AiSetup {
    fun open(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val target = AiTargets.forProject(project.basePath)
            val launcher = LauncherFiles(LauncherFiles.defaultDir())
            val acpFile = AiEnvironmentDetector.defaultAcpFile()
            val status = target?.let { AiEnvironmentDetector(acpFile, launcher).inspect(it.projectDir) }
            val agentEnv = launcher.readAgentEnv()
            val git = target?.let { Files.isDirectory(it.projectDir.resolve(".git")) } == true
            ApplicationManager.getApplication().invokeLater {
                if (target == null || status == null) {
                    Messages.showInfoMessage(project, "The gg binary was not found. Install it from Settings → Gopher Glide first.", "Connect AI Agents")
                } else {
                    show(project, target, status, launcher, acpFile, agentEnv, git)
                }
            }
        }
    }

    private fun show(project: Project, target: AiTarget, status: AiStatus, launcher: LauncherFiles, acpFile: Path, agentEnv: Map<String, String>, git: Boolean) {
        val daemon = GgMcpDaemonService.getInstance(project)
        val view = AiSetupViews.of(status, daemon.state, daemon.lastError, target, git, AiEnvironmentDetector.jetBrainsDirPresent())
        val hosts = status.detectedHosts.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Found in this IDE: ") ?: ""
        val configurator = AiAgentConfigurator(GgRunner.forBinary(target.ggBinary), launcher, acpFile)
        val dialog = ConfigureAiAgentsDialog(project, view, hosts, agentEnv) { request, show ->
            val modality = ModalityState.current()
            ApplicationManager.getApplication().executeOnPooledThread {
                val text = configurator.preview(target, request)
                ApplicationManager.getApplication().invokeLater({ show(text) }, modality)
            }
        }
        if (!dialog.showAndGet()) return
        val result = dialog.result
        BinaryFeatureGate.ensure(project, BinaryFeatureGate.AI_SETUP) { run(project, target, configurator, launcher, result) }
    }

    private fun run(project: Project, target: AiTarget, configurator: AiAgentConfigurator, launcher: LauncherFiles, chosen: AiDialogResult) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Connecting AI agents to Gopher Glide…", false) {
            private var outcome: ApplyResult? = null

            override fun run(indicator: ProgressIndicator) {
                outcome = configurator.apply(target, chosen.request)
            }

            override fun onSuccess() {
                outcome?.let { finish(project, it, chosen) }
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(project, error.message ?: error.javaClass.simpleName, "Connect AI Agents")
            }
        })
    }

    private fun finish(project: Project, result: ApplyResult, chosen: AiDialogResult) {
        val settings = GopherGlideSettings.instance
        settings.daemonMaxRps = chosen.maxRps
        settings.daemonMaxDurationMinutes = chosen.maxDurationMinutes
        settings.daemonMaxAgentSessions = chosen.maxSessions
        settings.daemonAllowHosts = chosen.allowHosts
        if (result.connected) {
            GopherGlideProjectSettings.getInstance(project).aiLinked = true
            val daemon = GgMcpDaemonService.getInstance(project)
            daemon.ensureRunning().whenComplete { _, _ -> daemon.publishToLauncher() }
        }
        GopherGlideNotifications.info(project, summary(result))
    }

    /** One line per step, e.g. "✓ .mcp.json — gg mcp --attach". */
    fun summary(result: ApplyResult): String = result.steps.joinToString("<br>") { step ->
        val mark = when (step.outcome) {
            Outcome.DONE -> "✓"
            Outcome.UNCHANGED -> "="
            Outcome.SKIPPED -> "–"
            Outcome.FAILED -> "✗"
        }
        "$mark ${step.what}" + (step.detail?.let { " — ${it.replace("<", "&lt;")}" } ?: "")
    }.ifEmpty { "Nothing was selected." }
}
