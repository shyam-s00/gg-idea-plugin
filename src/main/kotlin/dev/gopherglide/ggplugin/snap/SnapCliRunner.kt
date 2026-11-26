package dev.gopherglide.ggplugin.snap

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager
import java.awt.Component

/**
 * Runs a one-shot `gg snap <subcommand>` (assert, prune) to completion and returns its captured
 * stdout, stderr and exit code.
 */
object SnapCliRunner {
    private val log = Logger.getInstance(SnapCliRunner::class.java)

    fun run(project: Project, args: List<String>, parent: Component? = null, onComplete: (ProcessOutput) -> Unit) {
        val modality = ModalityState.current()
        val binaryPath = BinaryManager.instance.resolveBinaryPath()
        if (binaryPath == null) {
            GopherGlideNotifications.downloadWithProgress(project).thenAccept { downloadedPath ->
                runOnPooledThread(project, parent, modality, downloadedPath, args, onComplete)
            }.exceptionally { ex ->
                ApplicationManager.getApplication().invokeLater({
                    showError(project, parent, "Failed to locate or download Gopher-Glide binary.\nError: ${ex.message}", "Binary Resolution Error")
                }, modality)
                null
            }
        } else {
            runOnPooledThread(project, parent, modality, binaryPath, args, onComplete)
        }
    }

    private fun runOnPooledThread(project: Project, parent: Component?, modality: ModalityState, binaryPath: String, args: List<String>, onComplete: (ProcessOutput) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commandLine = GeneralCommandLine(binaryPath).withParameters(args)
                val output = CapturingProcessHandler(commandLine).runProcess()
                ApplicationManager.getApplication().invokeLater({ onComplete(output) }, modality)
            } catch (e: Exception) {
                log.warn("Failed to run gg ${args.joinToString(" ")}", e)
                ApplicationManager.getApplication().invokeLater({
                    showError(project, parent, "Failed to run Gopher-Glide: ${e.message}", "Gopher Glide Error")
                }, modality)
            }
        }
    }

    private fun showError(project: Project, parent: Component?, message: String, title: String) {
        if (parent != null) Messages.showErrorDialog(parent, message, title) else Messages.showErrorDialog(project, message, title)
    }
}
