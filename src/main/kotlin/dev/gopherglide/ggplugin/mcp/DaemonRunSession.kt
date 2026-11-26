package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.control.ControlCommand
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.execution.control.StopPhase
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.services.BinaryStatus
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One run on the shared daemon, shown in the Run panel: either started here ([startRun]) or started by an
 * agent and adopted ([adopt]). Events arrive from the bridge; commands go out over HTTP with the session id.
 * Every method that touches the panel must run on the EDT.
 */
class DaemonRunSession(
    private val panel: GopherGlideRunPanel,
    private val http: PluginHttp,
    val origin: AnnotationSource,
    private val onEnded: (DaemonRunSession) -> Unit,
    private val forceStopDaemon: () -> Unit,
    private val executor: Executor = AppExecutorUtil.getAppExecutorService(),
    private val edt: (() -> Unit) -> Unit = { action -> ApplicationManager.getApplication().invokeLater { action() } },
    private val stopWatchdogSeconds: Long = STOP_WATCHDOG_SECONDS,
    private val shareable: Boolean = true
) : RunControls {
    @Volatile var sessionId: String? = null
        private set
    @Volatile var ended = false
        private set

    private val sentIds = ConcurrentHashMap.newKeySet<String>()
    private val ids = AtomicInteger()
    private var startedSeen = false
    private var stopRequested = false
    private var forced = false

    override val agentSteerSupported: Boolean get() = origin == AnnotationSource.USER && shareable

    override fun ownsCommand(id: String): Boolean = id in sentIds

    /** Shows this run in the panel; call before the first event. */
    fun begin() {
        panel.reset()
        panel.onProcessStarted(this)
        if (origin == AnnotationSource.AGENT) panel.showOrigin(AnnotationSource.AGENT)
    }

    /** Takes over an agent's run that the daemon has already started. */
    fun adopt(id: String) {
        sessionId = id
        begin()
    }

    /** Asks the daemon to start the run; a refusal ends the session with the daemon's own message. */
    fun startRun(body: JsonObject) {
        executor.execute {
            val outcome = try {
                val result = http.post("/plugin/start", body.toString())
                parseStart(result)
            } catch (e: IOException) {
                StartOutcome(null, "Could not reach the shared gg daemon: ${e.message}")
            }
            edt {
                if (outcome.error != null) fail(outcome.error) else if (sessionId == null) sessionId = outcome.sessionId
            }
        }
    }

    private class StartOutcome(val sessionId: String?, val error: String?)

    private fun parseStart(result: HttpResult): StartOutcome {
        val json = runCatching { com.google.gson.JsonParser.parseString(result.body).asJsonObject }.getOrNull()
            ?: return StartOutcome(null, "The daemon answered with something unexpected (HTTP ${result.status})")
        if (json["event"]?.asString == "error") {
            return StartOutcome(null, json["message"]?.asString ?: "The daemon refused to start the run")
        }
        return StartOutcome(json["session_id"]?.asString, null)
    }

    fun onEvent(event: PluginEvent) {
        if (ended) return
        val payload = event.payload ?: return
        if (sessionId == null) sessionId = event.sessionId
        if (payload.event == "started") {
            if (startedSeen) return
            startedSeen = true
        }
        panel.update(payload)
        if (payload.isTerminal) finish()
    }

    /** The daemon went away or refused: end the run in the panel with [message]. */
    fun fail(message: String) {
        if (ended) return
        ended = true
        panel.onProcessTerminated(1, message)
        onEnded(this)
    }

    private fun finish() {
        if (ended) return
        ended = true
        panel.onProcessTerminated(0, "")
        onEnded(this)
    }

    override fun requestStop() {
        if (ended) return
        if (stopRequested) {
            forceStop()
            return
        }
        stopRequested = true
        panel.showStopPhase(StopPhase.AWAITING_ACK)
        send(ControlCommand.Stop(nextId("s"))) { reply ->
            if (reply is ControlReply.Ack) panel.showStopPhase(StopPhase.FINALIZING) else forceStop()
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            edt {
                if (!ended) {
                    panel.showStopPhase(StopPhase.NO_ACK_INTERRUPTING)
                    forceStop()
                }
            }
        }, stopWatchdogSeconds, TimeUnit.SECONDS)
    }

    /** Ending the daemon ends every run on it, so this is the last resort. */
    private fun forceStop() {
        if (forced || ended) return
        forced = true
        panel.showStopPhase(StopPhase.INTERRUPTING)
        forceStopDaemon()
    }

    override fun sendBias(amount: Int, onReply: (ControlReply) -> Unit) = send(ControlCommand.Bias(nextId("b"), amount), onReply)

    override fun sendMark(label: String, elapsedSeconds: Double?, onReply: (ControlReply) -> Unit) =
        send(ControlCommand.Mark(nextId("m"), label, elapsedSeconds), onReply)

    override fun sendHold(rps: Int, onReply: (ControlReply) -> Unit) = send(ControlCommand.Hold(nextId("h"), rps), onReply)

    override fun sendRelease(onReply: (ControlReply) -> Unit) = send(ControlCommand.Release(nextId("h")), onReply)

    override fun startAutopilot(mode: String, maxP99Ms: Int?, maxErrorRate: Double?, onReply: (ControlReply) -> Unit) =
        send(ControlCommand.Autopilot(nextId("a"), ControlCommand.Autopilot.Action.START, mode, maxP99Ms, maxErrorRate), onReply)

    override fun stopAutopilot(onReply: (ControlReply) -> Unit) =
        send(ControlCommand.Autopilot(nextId("a"), ControlCommand.Autopilot.Action.STOP), onReply)

    override fun setAgentSteer(allow: Boolean, onReply: (ControlReply) -> Unit) = send(ControlCommand.AgentSteer(nextId("g"), allow), onReply)

    override fun loadBinaryInfo(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {
        BinaryStatus.instance.loadAsync { onLoaded(it.installed, it.latest) }
    }

    private fun nextId(prefix: String) = "$prefix${ids.incrementAndGet()}"

    private fun send(command: ControlCommand, onReply: (ControlReply) -> Unit) {
        val refusal = when {
            ended -> "the run has ended"
            stopRequested && command !is ControlCommand.Stop -> "stop already requested"
            else -> null
        }
        if (refusal != null) return onReply(ControlReply.Failed(refusal))
        val session = sessionId ?: return onReply(ControlReply.Failed("the run has not started yet"))
        sentIds += command.id
        val body = ControlWire.body(command, session)
        executor.execute {
            val reply = try {
                val result = http.post("/plugin/control", body)
                ControlWire.reply(result.status, result.body)
            } catch (e: IOException) {
                ControlReply.Failed(e.message ?: "could not reach the shared gg daemon")
            }
            edt { onReply(reply) }
        }
    }

    private companion object {
        const val STOP_WATCHDOG_SECONDS = 20L
    }
}
