package dev.gopherglide.ggplugin.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications

/** Sends a run to the project's shared daemon when it can, and says so when it falls back to a direct run. */
object DaemonRouting {
    private val log = Logger.getInstance(DaemonRouting::class.java)

    /**
     * True when the run was taken over (it may still be starting); false when the caller should run it
     * directly now. [fallback] runs later, on the EDT, if the daemon turns out not to be usable.
     */
    fun tryRun(project: Project, args: List<String>, panel: GopherGlideRunPanel, fallback: () -> Unit): Boolean {
        val body = when (val request = StartRequest.fromArgs(args)) {
            is StartRequest.Unroutable -> {
                log.info("Running directly: ${request.reason}")
                return false
            }
            is StartRequest.Routable -> request.body
        }
        val daemon = GgMcpDaemonService.getInstance(project)
        if (daemon.bridge?.userRunActive() == true) {
            Messages.showInfoMessage(project, "A run is already going in the shared daemon. Stop it before starting another.", "Run in Progress")
            return true
        }
        panel.reset()
        daemon.ensureRunning().whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater {
                val bridge = daemon.bridge
                if (error == null && bridge != null) {
                    bridge.startUserRun(body, panel)
                } else {
                    val reason = (error?.cause ?: error)?.message ?: "the daemon is not available"
                    GopherGlideNotifications.info(project, "Running directly: $reason")
                    fallback()
                }
            }
        }
        return true
    }
}
