package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.Collections
import java.util.concurrent.Executor
import java.util.concurrent.ConcurrentHashMap
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunToolWindowFactory
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

/** What the bridge needs from the IDE: the Run panel when it is free, and word that a run ended. */
interface BridgeHost {
    /** The Run panel to show a run in, or null while another run is live there. */
    fun panelIfFree(): GopherGlideRunPanel?

    fun runEnded()

    fun agentRunQueued()

    /** Whether AI agents are connected to this project, which decides if `Allow AI Steer` is offered. */
    fun agentsLinked(): Boolean = true

    /** Runs [action] on the EDT, in order. */
    fun edt(action: () -> Unit)
}

/**
 * Follows the daemon's event stream and hands events to the run they belong to. The user's run is the one
 * started here (the daemon allows one at a time); an agent's run is adopted into the Run panel when it is
 * free, or queued until the current run ends.
 */
class PluginBridge(
    private val info: DaemonInfo,
    private val host: BridgeHost,
    private val http: PluginHttp = PluginHttp(info),
    private val executor: Executor = AppExecutorUtil.getAppExecutorService(),
    private val forceStopDaemon: () -> Unit = {}
) : PluginBridgeSseClient.Listener {
    private val log = Logger.getInstance(PluginBridge::class.java)
    private val client = PluginBridgeSseClient(info, this)

    @Volatile private var userRun: DaemonRunSession? = null
    private val adopted = ConcurrentHashMap<String, DaemonRunSession>()
    private val queued = Collections.synchronizedMap(LinkedHashMap<String, MutableList<PluginEvent>>())
    private val seen = HashSet<String>()

    fun start() = client.start()

    fun close() {
        client.close()
        host.edt { failAll("The shared gg daemon stopped") }
    }

    fun http(): PluginHttp = http

    /** True while a run started here or by an agent is still going, so the daemon should not be restarted. */
    fun hasActiveRuns(): Boolean = userRun?.ended == false || adopted.values.any { !it.ended } || queued.isNotEmpty()

    /** Starts a run for the user in the Run panel; the body is the daemon's start request. */
    fun startUserRun(body: JsonObject, panel: GopherGlideRunPanel): DaemonRunSession {
        val session = DaemonRunSession(panel, http, AnnotationSource.USER, ::sessionEnded, forceStopDaemon, executor, host::edt, shareable = host.agentsLinked())
        userRun = session
        session.begin()
        session.startRun(body)
        return session
    }

    fun userRunActive(): Boolean = userRun?.ended == false

    /** Tells the bridge the Run panel is free (a direct run ended), so a queued agent run can take it. */
    fun panelFreed() = host.edt { adoptNextQueued() }

    override fun onConnectionChanged(connected: Boolean) {
        if (!connected) log.info("Plugin bridge disconnected; reconnecting")
    }

    override fun onRejected() = host.edt { failAll("The shared gg daemon refused the plugin's token") }

    override fun onEvent(event: PluginEvent) = host.edt { route(event) }

    private fun route(event: PluginEvent) {
        val sid = event.sessionId ?: return
        if (isReplay(sid, event)) return
        when (event.origin) {
            "user" -> userRun?.onEvent(event)
            "agent" -> routeAgent(sid, event)
        }
        if (event.isTerminal) host.runEnded()
    }

    private fun routeAgent(sid: String, event: PluginEvent) {
        val payload = event.payload ?: return
        adopted[sid]?.let {
            it.onEvent(event)
            return
        }
        if (payload.event == "started") {
            val panel = host.panelIfFree()
            if (panel == null) {
                queued[sid] = mutableListOf(event)
                host.agentRunQueued()
            } else {
                adoptNow(sid, panel, listOf(event))
            }
            return
        }
        queued[sid]?.let { buffer ->
            if (event.isTerminal) queued.remove(sid) else if (buffer.size < MAX_QUEUED_EVENTS) buffer += event
        }
    }

    /** An event that carries a command id was already shown if its (session, id, kind) came before. */
    private fun isReplay(sid: String, event: PluginEvent): Boolean {
        val id = event.payload?.id ?: return false
        return !seen.add("$sid/${event.name}/$id")
    }

    private fun adoptNow(sid: String, panel: GopherGlideRunPanel, events: List<PluginEvent>) {
        val session = DaemonRunSession(panel, http, AnnotationSource.AGENT, ::sessionEnded, forceStopDaemon, executor, host::edt)
        adopted[sid] = session
        session.adopt(sid)
        events.forEach(session::onEvent)
    }

    private fun sessionEnded(session: DaemonRunSession) {
        if (userRun === session) userRun = null
        session.sessionId?.let { sid ->
            adopted.remove(sid)
            seen.removeIf { it.startsWith("$sid/") }
        }
        host.runEnded()
        adoptNextQueued()
    }

    private fun adoptNextQueued() {
        val next = synchronized(queued) { queued.entries.firstOrNull() } ?: return
        val panel = host.panelIfFree() ?: return
        queued.remove(next.key)
        adoptNow(next.key, panel, next.value)
    }

    private fun failAll(message: String) {
        userRun?.fail(message)
        adopted.values.toList().forEach { it.fail(message) }
        queued.clear()
    }

    private companion object {
        const val MAX_QUEUED_EVENTS = 300
    }
}

/** The bridge's view of the IDE for one project. */
class IdeBridgeHost(private val project: Project) : BridgeHost {
    override fun panelIfFree(): GopherGlideRunPanel? {
        val panel = GopherGlideRunToolWindowFactory.getPanel(project)
        if (panel?.isRunning() == true) return null
        return GopherGlideRunToolWindowFactory.showAndGetPanel(project)
    }

    override fun runEnded() = SnapToolWindowFactory.refreshTable(project)

    override fun agentsLinked(): Boolean = GopherGlideProjectSettings.getInstance(project).aiLinked

    override fun agentRunQueued() {
        GopherGlideNotifications.info(project, "An AI agent started a run. It will show in the Run tab when the current run ends.")
    }

    override fun edt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater { action() }
    }
}
