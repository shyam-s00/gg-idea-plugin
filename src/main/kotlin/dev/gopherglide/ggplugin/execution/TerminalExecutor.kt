package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.terminal.ui.TerminalWidget
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

object TerminalExecutor {
    private val activeWidgets: MutableMap<Project, TerminalWidget> = mutableMapOf()

    fun execute(project: Project, vararg args: String) {
        val binaryManager = BinaryManager.instance
        val binaryPath = binaryManager.resolveBinaryPath()

        if (binaryPath == null) {
            GopherGlideNotifications.downloadWithProgress(project).thenAccept { downloadedPath ->
                runInTerminal(project, downloadedPath, *args)
            }.exceptionally { ex ->
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Failed to locate or download Gopher-Glide binary. Cannot execute terminal.\nError: ${ex.message}",
                        "Binary Resolution Error"
                    )
                }
                null
            }
        } else {
            runInTerminal(project, binaryPath, *args)
        }
    }

    private fun runInTerminal(project: Project, binaryPath: String, vararg args: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater {
                try {
                    // Explicitly show the terminal tool window so the user sees it
                    val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    val window = toolWindowManager.getToolWindow("Terminal")
                    window?.show()

                    val terminalManager = TerminalToolWindowManager.getInstance(project)
                    val workingDir = project.basePath ?: System.getProperty("user.home")
                    
                    // A live entry in the map is, by construction, a non-terminated widget: its
                    // addTerminationCallback below removes the entry the moment its session ends.
                    var widget: TerminalWidget? = activeWidgets[project]

                    if (widget != null) {
                        try {
                            // Send SIGINT (Ctrl+C) to gracefully kill any previously running TUI
                            widget.sendCommandToExecute("\u0003")
                        } catch (e: Exception) {
                            widget = null
                        }
                    }

                    if (widget == null) {
                        val newWidget = terminalManager.createShellWidget(workingDir, "Gopher-Glide", true, false)
                        activeWidgets[project] = newWidget
                        newWidget.addTerminationCallback({ activeWidgets.remove(project) }, project)
                        widget = newWidget
                    }

                    val commandArgs = args.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
                    val command = "\"$binaryPath\" $commandArgs"
                    val widgetToRunIn = widget

                    // Add a small delay to ensure the widget is ready/cleared before sending the new command
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(300)
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                widgetToRunIn.sendCommandToExecute(command)
                            } catch (e: Exception) {
                                com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to send command to terminal: ${e.message}", "Terminal Error")
                            }
                        }
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(project, "Failed to open terminal widget: ${e.message}\n\nStack: ${e.stackTraceToString()}", "Terminal Error")
                    e.printStackTrace()
                }
            }
        }
    }
}
