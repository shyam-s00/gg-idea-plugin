package io.gopherglide.intellij.ggideaplugin.execution

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import io.gopherglide.intellij.ggideaplugin.services.BinaryManager

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
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            try {
                val terminalManager = TerminalToolWindowManager.getInstance(project)
                val widget = terminalManager.createShellWidget(project.basePath, "Gopher-Glide", true, true)
                
                val command = "\"$binaryPath\" \"$configPath\""
                widget.sendCommandToExecute(command)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
