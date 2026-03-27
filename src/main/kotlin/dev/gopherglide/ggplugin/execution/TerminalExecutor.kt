package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import dev.gopherglide.ggplugin.services.BinaryManager

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
                val command = listOf(binaryPath, configPath)
                terminalManager.createNewSession(project.basePath, "Gopher-Glide", command, true, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
