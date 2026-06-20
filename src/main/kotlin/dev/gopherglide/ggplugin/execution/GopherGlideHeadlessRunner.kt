package dev.gopherglide.ggplugin.execution

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunToolWindowFactory
import dev.gopherglide.ggplugin.settings.GopherGlideSettings

/**
 * Runs `gg --headless --reporter json` via [OSProcessHandler] (not the terminal widget) and feeds parsed
 * heartbeats to the Gopher Glide Run panel. This is the default run path: the interactive TUI's per-frame
 * ANSI redraw inside the IDE terminal widget is what pins the CPU, so this avoids it entirely.
 */
object GopherGlideHeadlessRunner {
    private val log = Logger.getInstance(GopherGlideHeadlessRunner::class.java)
    private val gson = Gson()
    private val activeHandlers = mutableMapOf<Project, OSProcessHandler>()

    fun run(project: Project, binaryPath: String, args: List<String>) {
        val panel = GopherGlideRunToolWindowFactory.showAndGetPanel(project) ?: return

        // Only one run per project at a time — stop whatever was previously running.
        activeHandlers.remove(project)?.destroyProcess()

        try {
            val heartbeatArgs = GopherGlideSettings.instance.heartbeatIntervalSeconds
                .takeIf { it > 0 }
                ?.let { listOf("--heartbeat-interval", "${it}s") }
                ?: emptyList()
            val commandLine = GeneralCommandLine(binaryPath)
                .withParameters(args + listOf("--headless", "--reporter", "json") + heartbeatArgs)
            val handler = OSProcessHandler(commandLine)
            activeHandlers[project] = handler
            val stderrTail = StringBuilder()

            ApplicationManager.getApplication().invokeLater {
                panel.reset()
                panel.onProcessStarted { handler.destroyProcess() }
            }

            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val line = event.text.trim()
                    if (line.isEmpty()) return

                    if (outputType !== ProcessOutputTypes.STDOUT) {
                        stderrTail.append(line).append('\n')
                        return
                    }

                    val payload = try {
                        gson.fromJson(line, HeartbeatPayload::class.java)
                    } catch (e: Exception) {
                        null // non-JSON noise on stdout — ignore rather than fail the run
                    } ?: return
                    ApplicationManager.getApplication().invokeLater { panel.update(payload) }
                }

                override fun processTerminated(event: ProcessEvent) {
                    activeHandlers.remove(project)
                    ApplicationManager.getApplication().invokeLater {
                        panel.onProcessTerminated(event.exitCode, stderrTail.toString().trim())
                    }
                }
            })

            handler.startNotify()
        } catch (e: Exception) {
            log.warn("Failed to start gg --headless process", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, "Failed to start Gopher-Glide: ${e.message}", "Gopher Glide Error")
            }
        }
    }
}
