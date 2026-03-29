package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.gopherglide.ggplugin.services.BinaryManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object TerminalExecutor {
    fun execute(project: Project, configPath: String) {
        val binaryManager = BinaryManager.instance
        val binaryPath = binaryManager.resolveBinaryPath()

        if (binaryPath == null) {
            binaryManager.downloadLatestRelease().thenAccept { downloadedPath ->
                runInTerminal(project, downloadedPath, configPath)
            }.exceptionally {
                null
            }
        } else {
            runInTerminal(project, binaryPath, configPath)
        }
    }

    private fun runInTerminal(project: Project, binaryPath: String, configPath: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val terminalManager = TerminalToolWindowManager.getInstance(project)
                val workingDir = project.basePath ?: System.getProperty("user.home")
                val widget = terminalManager.createShellWidget(workingDir, "Gopher-Glide", true, false)
                val command = "\"$binaryPath\" \"$configPath\""
                widget.sendCommandToExecute(command)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
