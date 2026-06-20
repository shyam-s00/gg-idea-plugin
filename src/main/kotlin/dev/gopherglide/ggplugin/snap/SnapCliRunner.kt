package dev.gopherglide.ggplugin.snap

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager

/**
 * Runs a non-streaming `gg snap <subcommand>` to completion and hands back its captured
 * stdout/stderr/exit code — for one-shot commands (assert, prune) that return a single report
 * rather than a live render loop, so there's no need for [GopherGlideHeadlessRunner]'s heartbeat
 * parsing or a terminal widget.
 */
object SnapCliRunner {
    private val log = Logger.getInstance(SnapCliRunner::class.java)

    fun run(project: Project, args: List<String>, onComplete: (ProcessOutput) -> Unit) {
        val binaryPath = BinaryManager.instance.resolveBinaryPath()
        if (binaryPath == null) {
            GopherGlideNotifications.downloadWithProgress(project).thenAccept { downloadedPath ->
                runOnPooledThread(project, downloadedPath, args, onComplete)
            }.exceptionally { ex ->
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to locate or download Gopher-Glide binary.\nError: ${ex.message}",
                        "Binary Resolution Error"
                    )
                }
                null
            }
        } else {
            runOnPooledThread(project, binaryPath, args, onComplete)
        }
    }

    private fun runOnPooledThread(project: Project, binaryPath: String, args: List<String>, onComplete: (ProcessOutput) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commandLine = GeneralCommandLine(binaryPath).withParameters(args)
                val output = CapturingProcessHandler(commandLine).runProcess()
                ApplicationManager.getApplication().invokeLater { onComplete(output) }
            } catch (e: Exception) {
                log.warn("Failed to run gg ${args.joinToString(" ")}", e)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Failed to run Gopher-Glide: ${e.message}", "Gopher Glide Error")
                }
            }
        }
    }
}
