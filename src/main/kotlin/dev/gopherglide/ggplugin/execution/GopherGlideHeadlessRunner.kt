package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunToolWindowFactory
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.mcp.DaemonRouting
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs `gg --headless --reporter json` through a [GopherGlideRunSession] (not the terminal widget) and feeds it
 * to the Gopher Glide Run panel. This is the default run path: the interactive TUI's per-frame ANSI redraw
 * inside the IDE terminal widget is what pins the CPU, so this avoids it entirely. With the shared-daemon
 * setting on, the run goes to the project's `gg mcp` daemon instead, and falls back here if that is not possible.
 */
object GopherGlideHeadlessRunner {
    private val log = Logger.getInstance(GopherGlideHeadlessRunner::class.java)
    private val sessions = ConcurrentHashMap<Project, GopherGlideRunSession>()

    fun run(project: Project, binaryPath: String, args: List<String>) {
        val panel = GopherGlideRunToolWindowFactory.showAndGetPanel(project) ?: return

        // Only one run per project at a time — retire whatever was previously running.
        sessions.remove(project)?.detach()

        if (GopherGlideSettings.instance.routeRunsThroughDaemon &&
            DaemonRouting.tryRun(project, args, panel) { runDirect(project, binaryPath, args, panel) }
        ) {
            return
        }
        runDirect(project, binaryPath, args, panel)
    }

    private fun runDirect(project: Project, binaryPath: String, args: List<String>, panel: GopherGlideRunPanel) {
        val session = GopherGlideRunSession(project, binaryPath, args, panel) { ended ->
            sessions.remove(project, ended)
            project.getServiceIfCreated(GgMcpDaemonService::class.java)?.bridge?.panelFreed()
        }
        sessions[project] = session
        try {
            session.start()
        } catch (e: Exception) {
            sessions.remove(project, session)
            log.warn("Failed to start gg --headless process", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "Failed to start Gopher-Glide: ${e.message}", "Gopher Glide Error")
            }
        }
    }
}
