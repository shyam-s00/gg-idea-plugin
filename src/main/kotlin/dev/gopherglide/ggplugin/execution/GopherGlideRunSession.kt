package dev.gopherglide.ggplugin.execution

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.ControlSession
import dev.gopherglide.ggplugin.execution.control.ProcessKiller
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.execution.control.Scheduler
import dev.gopherglide.ggplugin.execution.control.StopLadder
import dev.gopherglide.ggplugin.execution.control.StopPhase
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.TimeUnit

/**
 * One `gg --headless --reporter json` process: parses its output into the [panel], carries commands back over
 * stdin, and owns the stop escalation. Created per run; [detach] retires it when a newer run replaces it.
 */
class GopherGlideRunSession(
    private val project: Project,
    private val binaryPath: String,
    private val args: List<String>,
    private val panel: GopherGlideRunPanel,
    private val onEnded: (GopherGlideRunSession) -> Unit = {}
) : RunControls {
    private val gson = Gson()
    private val disposable = Disposer.newDisposable("Gopher Glide run")
    private val stdoutLines = OutputLineBuffer()
    private val stderrTail = StringBuilder()

    @Volatile private var detached = false

    private lateinit var handler: KillableProcessHandler
    private lateinit var control: ControlSession
    private lateinit var ladder: StopLadder

    init {
        Disposer.register(project, disposable)
    }

    /** Throws if the process cannot be started. */
    fun start() {
        try {
            launch()
        } catch (e: Exception) {
            Disposer.dispose(disposable)
            throw e
        }
    }

    private fun launch() {
        val heartbeatArgs = GopherGlideSettings.instance.heartbeatIntervalSeconds
            .takeIf { it > 0 }
            ?.let { listOf("--heartbeat-interval", "${it}s") }
            ?: emptyList()
        val commandLine = GeneralCommandLine(binaryPath)
            .withParameters(args + listOf("--headless", "--reporter", "json") + heartbeatArgs)
        handler = KillableProcessHandler(commandLine)

        control = ControlSession(
            handler.processInput,
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Gopher Glide control", 1)
        )
        ladder = StopLadder(
            channel = control,
            killer = object : ProcessKiller {
                override fun softKill() = handler.destroyProcess()
                override fun hardKill() = handler.killProcess()
            },
            scheduler = object : Scheduler {
                override fun schedule(delayMs: Long, task: Runnable): () -> Unit {
                    val future = AppExecutorUtil.getAppScheduledExecutorService().schedule(task, delayMs, TimeUnit.MILLISECONDS)
                    return { future.cancel(false) }
                }
            },
            clock = { System.nanoTime() / 1_000_000 },
            onPhase = { phase -> onEdt { panel.showStopPhase(phase) } }
        )
        Disposer.register(disposable) { if (!handler.isProcessTerminated) handler.destroyProcess() }

        onEdt {
            panel.reset()
            panel.onProcessStarted(this)
        }

        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType === ProcessOutputTypes.STDOUT) {
                    stdoutLines.append(event.text).forEach(::dispatch)
                    return
                }
                val line = event.text.trim()
                if (line.isNotEmpty()) stderrTail.append(line).append('\n')
            }

            override fun processTerminated(event: ProcessEvent) {
                stdoutLines.flush()?.let(::dispatch)
                ladder.onTerminated()
                control.close()
                onEnded(this@GopherGlideRunSession)
                onEdt { panel.onProcessTerminated(event.exitCode, stderrTail.toString().trim()) }
                Disposer.dispose(disposable)
            }
        })

        handler.startNotify()
    }

    /** Stop button: clean stop first, soft kill if gg does not answer, hard kill on a repeated request. */
    override fun requestStop() = ladder.requestStop()

    override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) {
        control.sendBias(amount) { reply -> onEdt { onReply(reply) } }
    }

    override fun sendMark(label: String, onReply: (ControlReply) -> Unit) {
        control.sendMark(label) { reply -> onEdt { onReply(reply) } }
    }

    override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val manager = BinaryManager.instance
            val installed = manager.installedBinary(binaryPath)
            val latest = try {
                GopherGlideVersion.parse(manager.getLatestRemoteVersion().get(15, TimeUnit.SECONDS))
            } catch (e: Exception) {
                null
            }
            onEdt { onLoaded(installed, latest) }
        }
    }

    override fun upgradeBinary(onDone: (Throwable?) -> Unit) {
        GopherGlideNotifications.downloadWithProgress(project).whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater { onDone(error) }
        }
    }

    /** Interrupts the process and stops reporting to the panel, for a run that a newer one replaces. */
    fun detach() {
        detached = true
        if (!handler.isProcessTerminated) handler.destroyProcess()
    }

    private fun dispatch(line: String) {
        val payload = try {
            gson.fromJson(line, HeartbeatPayload::class.java)
        } catch (e: Exception) {
            null // non-JSON noise on stdout — ignore rather than fail the run
        } ?: return
        control.onEvent(payload)
        if (payload.event == "started") ladder.onStarted(payload.capabilities)
        onEdt { panel.update(payload) }
    }

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater { if (!detached) action() }
    }
}
