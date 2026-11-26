package dev.gopherglide.ggplugin.mcp

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel

object DaemonTestSupport {
    /** The events of a real agent-started run, as the daemon streamed them to the plugin. */
    fun agentRun(): List<PluginEvent> {
        val parser = SseParser()
        return checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v150-sse-agent.txt")).bufferedReader().readLines()
            .mapNotNull { parser.feed(it) } + listOfNotNull(parser.feed(""))
    }

    fun event(name: String, sid: String, origin: String, payload: HeartbeatPayload) = PluginEvent(name, sid, origin, payload)

    fun started(sid: String, origin: String) = event(
        "started", sid, origin,
        HeartbeatPayload(
            event = "started", message = "Load test started",
            capabilities = listOf("control.bias", "control.mark", "control.mark.elapsed", "control.stop", "control.hold", "control.autopilot")
        )
    )

    fun heartbeat(sid: String, origin: String, elapsed: Double = 1.0) =
        event("heartbeat", sid, origin, HeartbeatPayload(event = "heartbeat", elapsedSeconds = elapsed, bias = 0, stage = 1, totalStages = 1))

    fun stopped(sid: String, origin: String) = event("stopped", sid, origin, HeartbeatPayload(event = "stopped", message = "done"))

    fun labels(panel: GopherGlideRunPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).map { it.text.orEmpty() }

    /** The chart annotations the panel holds; there is no other way to see them without a screenshot. */
    @Suppress("UNCHECKED_CAST")
    fun annotations(panel: GopherGlideRunPanel): List<RunAnnotation> =
        GopherGlideRunPanel::class.java.getDeclaredField("annotations").apply { isAccessible = true }.get(panel) as List<RunAnnotation>
}
