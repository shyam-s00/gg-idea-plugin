package dev.gopherglide.ggplugin.ai

import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.BinaryStatus
import dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings
import dev.gopherglide.ggplugin.settings.GopherGlideSettings

/** What happens for AI agents when a project opens. */
object AiStartup {
    private val log = Logger.getInstance(AiStartup::class.java)

    fun onProjectOpened(project: Project) {
        val projectSettings = GopherGlideProjectSettings.getInstance(project)
        if (GopherGlideSettings.instance.startDaemonWithProject || projectSettings.aiLinked) {
            GgMcpDaemonService.getInstance(project).ensureRunning()
        }
        ApplicationManager.getApplication().executeOnPooledThread { syncPaths(project) }
        val removeListener = BinaryStatus.instance.addListener {
            ApplicationManager.getApplication().executeOnPooledThread { syncPaths(project) }
        }
        Disposer.register(GgMcpDaemonService.getInstance(project)) { removeListener() }
        ApplicationManager.getApplication().executeOnPooledThread { offerIfNeeded(project) }
    }

    /** Brings the launcher and `.mcp.json` up to date with the binary and plugin in use, for a project that is connected. */
    fun syncPaths(project: Project) {
        if (!GopherGlideProjectSettings.getInstance(project).aiLinked) return
        val target = AiTargets.forProject(project.basePath) ?: return
        val managedDir = BinaryManager.instance.getDownloadedBinaryPath().parent
        runCatching { AiPathSync.sync(target, LauncherFiles(LauncherFiles.defaultDir()), managedDir) }
            .onFailure { log.warn("Could not sync the AI agent config to the new gg path", it) }
    }

    private fun offerIfNeeded(project: Project) {
        val settings = GopherGlideSettings.instance
        val projectSettings = GopherGlideProjectSettings.getInstance(project)
        if (settings.dontAskForAiAgents || projectSettings.aiOfferShown || projectSettings.aiLinked) return
        val target = AiTargets.forProject(project.basePath) ?: return
        val usable = BinaryStatus.instance.load(target.ggBinary).installed?.version?.supportsAutopilotAndMcp == true
        val status = AiEnvironmentDetector(AiEnvironmentDetector.defaultAcpFile(), LauncherFiles(LauncherFiles.defaultDir())).inspect(target.projectDir)
        val hasFiles = hasGgFiles(project)
        if (!AiOnboarding.shouldOffer(settings.dontAskForAiAgents, projectSettings.aiOfferShown, projectSettings.aiLinked, hasFiles, usable, status)) return
        projectSettings.aiOfferShown = true
        GopherGlideNotifications.info(
            project,
            "Connect AI agents (Claude Code, Cursor, Junie, JetBrains Air) so their gg runs show live in the Run tab.",
            NotificationAction.createSimpleExpiring("Configure AI Agents…") { AiSetup.open(project) },
            NotificationAction.createSimpleExpiring("Don't Ask Again") { settings.dontAskForAiAgents = true }
        )
    }

    private fun hasGgFiles(project: Project): Boolean = ReadAction.nonBlocking<Boolean> {
        val scope = GlobalSearchScope.projectScope(project)
        FilenameIndex.getAllFilesByExt(project, "http", scope).isNotEmpty() ||
            FilenameIndex.getAllFilesByExt(project, "yaml", scope).any { it.name.endsWith(".gg.yaml") }
    }.inSmartMode(project).executeSynchronously()
}
