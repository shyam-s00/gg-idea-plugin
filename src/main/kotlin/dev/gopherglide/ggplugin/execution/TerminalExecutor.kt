package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.gopherglide.ggplugin.services.BinaryManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

import java.lang.ref.WeakReference
import com.intellij.openapi.util.Disposer

object TerminalExecutor {
    private val activeWidgetRefs: MutableMap<Project, WeakReference<Any>> = mutableMapOf()

    fun execute(project: Project, vararg args: String) {
        val binaryManager = BinaryManager.instance
        val binaryPath = binaryManager.resolveBinaryPath()

        if (binaryPath == null) {
            binaryManager.downloadLatestRelease().thenAccept { downloadedPath ->
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
                    
                    var widget: Any? = activeWidgetRefs[project]?.get()
                    if (widget != null) {
                        var isDisposed = false
                        if (widget is com.intellij.openapi.Disposable) {
                            isDisposed = Disposer.isDisposed(widget)
                        } else {
                            try {
                                val m = widget.javaClass.getMethod("isDisposed")
                                isDisposed = m.invoke(widget) as Boolean
                            } catch (e: Exception) {}
                        }
                        if (isDisposed) {
                            widget = null
                        }
                    }

                    if (widget != null) {
                        try {
                            // Send SIGINT (Ctrl+C) to gracefully kill any previously running TUI
                            val sendMethod = widget.javaClass.getMethod("sendCommandToExecute", String::class.java)
                            sendMethod.isAccessible = true
                            sendMethod.invoke(widget, "\u0003")
                        } catch (e: Exception) {
                            widget = null
                        }
                    }

                    if (widget == null) {
                        widget = terminalManager.createShellWidget(workingDir, "Gopher-Glide", true, false)
                        activeWidgetRefs[project] = WeakReference(widget)
                        Disposer.register(project) { activeWidgetRefs.remove(project) }
                    }

                    val commandArgs = args.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
                    val command = "\"$binaryPath\" $commandArgs"
                    
                    // Add a small delay to ensure the widget is ready/cleared before sending the new command
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(300)
                        ApplicationManager.getApplication().invokeLater {
                            try {
                                if (widget != null) {
                                    val sendMethod = widget.javaClass.getMethod("sendCommandToExecute", String::class.java)
                                    sendMethod.isAccessible = true
                                    sendMethod.invoke(widget, command)
                                }
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
