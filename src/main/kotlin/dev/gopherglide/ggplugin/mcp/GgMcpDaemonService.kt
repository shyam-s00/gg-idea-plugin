package dev.gopherglide.ggplugin.mcp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import dev.gopherglide.ggplugin.execution.OutputLineBuffer
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.BinaryStatus
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs one `gg mcp` daemon per project, on demand, with the project folder as its working directory so
 * `gg mcp --attach` finds it. The daemon stops when its stdin closes, so it cannot outlive the IDE.
 */
@Service(Service.Level.PROJECT)
class GgMcpDaemonService(private val project: Project) : Disposable {
    enum class State { STOPPED, STARTING, RUNNING, FAILED }

    fun interface Listener {
        fun onStateChanged(state: State, message: String?)
    }

    private val log = Logger.getInstance(GgMcpDaemonService::class.java)
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val lock = Any()

    @Volatile var state: State = State.STOPPED
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var info: DaemonInfo? = null
        private set
    @Volatile var bridge: PluginBridge? = null
        private set

    /** Lets tests run the daemon in a real folder and show runs somewhere other than the tool window. */
    internal var workDirectory: String? = null
    internal var bridgeHost: BridgeHost? = null

    internal var launcher: dev.gopherglide.ggplugin.ai.LauncherFiles = dev.gopherglide.ggplugin.ai.LauncherFiles(dev.gopherglide.ggplugin.ai.LauncherFiles.defaultDir())

    private var handler: KillableProcessHandler? = null
    private var starting: CompletableFuture<DaemonInfo>? = null
    private var signature: String = ""
    private var stopRequested = false

    fun addListener(listener: Listener): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /**
     * Starts the daemon unless it is already up with the same settings. When the settings changed and no
     * run is active it restarts; with a run active it keeps the running daemon, since stopping it ends the run.
     */
    fun ensureRunning(): CompletableFuture<DaemonInfo> {
        val future = CompletableFuture<DaemonInfo>()
        synchronized(lock) {
            starting?.takeIf { !it.isDone }?.let { return it }
            starting = future
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { workDir()?.let(launcher::markDaemonPending) }
            try {
                future.complete(ensureBlocking())
            } catch (e: Exception) {
                log.warn("Could not start gg mcp", e)
                fail(e.message ?: e.javaClass.simpleName)
                future.completeExceptionally(e)
            } finally {
                runCatching { workDir()?.let(launcher::clearDaemonPending) }
            }
        }
        return future
    }

    private fun ensureBlocking(): DaemonInfo {
        val running = synchronized(lock) { info.takeIf { state == State.RUNNING } }
        if (running != null) {
            if (bridge?.hasActiveRuns() == true || signatureOf(resolveOptions()) == signature) return running
            stopAndWait()
        }
        setState(State.STARTING, null)
        return startBlocking()
    }

    private fun stopAndWait() {
        val current = synchronized(lock) { handler.also { stopLocked() } } ?: return
        current.waitFor(STOP_GRACE_SECONDS * 2 * 1000)
        if (!current.isProcessTerminated) current.destroyProcess()
        current.waitFor(2_000)
    }

    /** Stops the daemon, ending any run it has. Safe to call when it is not running. */
    fun stop() = synchronized(lock) { stopLocked() }

    override fun dispose() {
        synchronized(lock) {
            stopRequested = true
            unpublishFromLauncher()
            handler?.let {
                runCatching { it.processInput.close() }
                it.destroyProcess()
            }
            bridge?.close()
            bridge = null
        }
    }

    private fun stopLocked() {
        val current = handler ?: return
        stopRequested = true
        unpublishFromLauncher()
        runCatching { current.processInput.close() }
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            if (!current.isProcessTerminated) current.destroyProcess()
        }, STOP_GRACE_SECONDS, TimeUnit.SECONDS)
    }

    private fun startBlocking(): DaemonInfo {
        val binary = BinaryManager.instance.resolveBinaryPath() ?: error("The gg binary was not found")
        val version = BinaryStatus.instance.load(binary).installed?.version
        if (version != null && !version.supportsAutopilotAndMcp) {
            error("The shared daemon needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP}+ (installed: $version)")
        }
        val basePath = workDirectory ?: project.basePath ?: error("This project has no folder to run the daemon in")

        val options = resolveOptions()
        val token = DaemonCommand.newToken()
        val commandLine = GeneralCommandLine(binary)
            .withParameters(DaemonCommand.args(options))
            .withWorkDirectory(basePath)
            .withEnvironment(DaemonCommand.environment(token, options))
        val process = KillableProcessHandler(commandLine)

        val ready = CompletableFuture<DaemonInfo>()
        val lines = OutputLineBuffer()
        val stderrTail = StringBuilder()
        process.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                if (outputType === ProcessOutputTypes.STDOUT) {
                    lines.append(event.text).forEach { line -> DaemonCommand.parseListening(line, token)?.let(ready::complete) }
                } else {
                    stderrTail.append(event.text)
                    if (stderrTail.length > STDERR_KEPT) stderrTail.delete(0, stderrTail.length - STDERR_KEPT)
                }
            }

            override fun processTerminated(event: ProcessEvent) = onExited(process, event.exitCode, stderrTail.toString(), ready)
        })
        synchronized(lock) {
            handler = process
            stopRequested = false
        }
        process.startNotify()

        val started = try {
            ready.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            process.destroyProcess()
            error("gg mcp did not report an address within ${START_TIMEOUT_SECONDS}s")
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
        synchronized(lock) {
            info = started
            signature = signatureOf(options)
            bridge = PluginBridge(started, bridgeHost ?: IdeBridgeHost(project), forceStopDaemon = { stop() }).also { it.start() }
            setState(State.RUNNING, null)
        }
        publishToLauncher()
        return started
    }

    /** Lets the JetBrains ACP launcher find this daemon, if the launcher is installed. */
    fun publishToLauncher() {
        val running = info ?: return
        runCatching { workDir()?.let { launcher.publishDaemon(it, running) } }.onFailure { log.warn("Could not publish the daemon to the launcher", it) }
    }

    private fun unpublishFromLauncher() {
        runCatching { workDir()?.let(launcher::unpublishDaemon) }
    }

    private fun workDir(): String? = workDirectory ?: project.basePath

    private fun onExited(process: KillableProcessHandler, exitCode: Int, stderr: String, ready: CompletableFuture<DaemonInfo>) {
        val detail = stderr.trim().lineSequence().lastOrNull { it.isNotBlank() }.orEmpty()
        val message = "gg mcp exited (code $exitCode)" + if (detail.isNotEmpty()) ": $detail" else ""
        synchronized(lock) {
            if (handler !== process) return
            handler = null
            info = null
            unpublishFromLauncher()
            bridge?.close()
            bridge = null
            if (stopRequested) setState(State.STOPPED, null) else setState(State.FAILED, message)
        }
        ready.completeExceptionally(IllegalStateException(message))
    }

    private fun fail(message: String) {
        synchronized(lock) {
            if (state == State.RUNNING) stopLocked()
            setState(State.FAILED, message)
        }
    }

    private fun setState(newState: State, message: String?) {
        state = newState
        lastError = message
        listeners.forEach { it.onStateChanged(newState, message) }
    }

    private fun resolveOptions(): DaemonOptions {
        val settings = GopherGlideSettings.instance
        val custom = DaemonCommand.parseHosts(settings.daemonAllowHosts)
        val hosts = custom.ifEmpty { scanProjectHosts().allowList() }
        return DaemonOptions(
            maxRps = settings.daemonMaxRps.coerceAtLeast(1),
            maxDurationMinutes = settings.daemonMaxDurationMinutes.coerceAtLeast(1),
            maxAgentSessions = settings.daemonMaxAgentSessions.coerceAtLeast(1),
            allowHosts = hosts,
            snapDir = settings.customSnapshotsDir
        )
    }

    private fun signatureOf(options: DaemonOptions) = DaemonCommand.args(options).joinToString(" ") + "|" + options.snapDir

    private fun scanProjectHosts(): HostScan = ReadAction.nonBlocking<HostScan> {
        FilenameIndex.getAllFilesByExt(project, "http", GlobalSearchScope.projectScope(project))
            .map { file ->
                if (file.length > MAX_HTTP_FILE_BYTES) HostScan(emptySet(), true) else HttpHosts.scan(VfsUtilCore.loadText(file))
            }
            .fold(HostScan.NONE) { all, next -> all + next }
    }.inSmartMode(project).executeSynchronously()

    companion object {
        private const val START_TIMEOUT_SECONDS = 15L
        private const val STOP_GRACE_SECONDS = 3L
        private const val STDERR_KEPT = 2_000
        private const val MAX_HTTP_FILE_BYTES = 1_000_000L

        fun getInstance(project: Project): GgMcpDaemonService = project.service()
    }
}
