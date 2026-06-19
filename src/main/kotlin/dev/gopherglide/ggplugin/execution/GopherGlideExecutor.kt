package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager

/**
 * Default entry point for running `gg` from the IDE: launches headlessly into the native run panel
 * (see [GopherGlideHeadlessRunner]) instead of the interactive TUI in a terminal. [TerminalExecutor]
 * remains available as the explicit "Run in Terminal (interactive)" opt-in for live RPS-bias control.
 */
object GopherGlideExecutor {
    fun execute(project: Project, vararg args: String) {
        val binaryManager = BinaryManager.instance
        val binaryPath = binaryManager.resolveBinaryPath()

        if (binaryPath == null) {
            GopherGlideNotifications.downloadWithProgress(project).thenAccept { downloadedPath ->
                GopherGlideHeadlessRunner.run(project, downloadedPath, args.toList())
            }.exceptionally { ex ->
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to locate or download Gopher-Glide binary. Cannot start run.\nError: ${ex.message}",
                        "Binary Resolution Error"
                    )
                }
                null
            }
        } else {
            GopherGlideHeadlessRunner.run(project, binaryPath, args.toList())
        }
    }
}
