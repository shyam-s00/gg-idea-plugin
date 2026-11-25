package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ide.ActivityTracker
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.WindowHealthPayload
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.autopilot.AutopilotOutcomes
import dev.gopherglide.ggplugin.execution.control.BinaryActions
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.execution.control.StopPhase
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Color
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel

/** Small bordered card showing one bold value over a small caption — used for the metric row. */
private class MetricCard(caption: String) : JPanel() {
    private val valueLabel = JBLabel("—")
    private val defaultValueColor: Color

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xD3D3D3, 0x3C3F41)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        )
        valueLabel.font = valueLabel.font.deriveFont(Font.BOLD, valueLabel.font.size + 3f)
        valueLabel.alignmentX = CENTER_ALIGNMENT
        defaultValueColor = valueLabel.foreground

        val captionLabel = JBLabel(caption)
        captionLabel.font = captionLabel.font.deriveFont(Font.PLAIN, captionLabel.font.size - 2f)
        captionLabel.foreground = JBColor.GRAY
        captionLabel.alignmentX = CENTER_ALIGNMENT

        add(valueLabel)
        add(captionLabel)
    }

    var value: String
        get() = valueLabel.text
        set(v) { valueLabel.text = v }

    /** Pass null to reset to the default (theme) text color. */
    fun setValueColor(color: Color?) {
        valueLabel.foreground = color ?: defaultValueColor
    }
}

/**
 * Run dashboard: status header + a horizontal row of metric cards + a scaled RPS chart + a stage
 * timeline, updated once per heartbeat (~5s by default).
 */
class GopherGlideRunPanel(binaryActions: BinaryActions = BinaryActions.None) : JPanel(BorderLayout()) {
    private val statusLabel = JBLabel("Idle")
    private val originBadge = JBLabel().apply {
        font = font.deriveFont(Font.BOLD)
        foreground = JBColor(0x1F5FA8, 0x9ACBFA)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0x8DBBE8, 0x3F6C96)), BorderFactory.createEmptyBorder(0, 6, 0, 6)
        )
        isVisible = false
    }
    private var knownBias = 0
    private val elapsedLabel = JBLabel("00:00")
    private val targetRpsCard = MetricCard("TARGET RPS")
    private val actualRpsCard = MetricCard("ACTUAL RPS")
    private val errorRateCard = MetricCard("ERROR RATE")
    private val totalReqsCard = MetricCard("TOTAL REQUESTS")
    private val latencyCard = MetricCard("P50 / P95 / P99 (ms)")
    private val errorMixStrip = ErrorMixStrip()
    private val rpsChart = RpsChartComponent()
    private val stageTimeline = StageTimelineComponent()
    private var runStartMs = 0L
    private var reachedTerminalState = false
    private var currentProfile: String? = null
    private var controls: RunControls? = null
    private val annotations = mutableListOf<RunAnnotation>()
    private val liveControl = LiveControlPanel(::runElapsedSeconds, ::addAnnotation, binaryActions = binaryActions)
    private var running = false
    private var stopPhase = StopPhase.NONE
    private var stoppedByRequest = false

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, statusLabel.font.size + 1f)
        elapsedLabel.font = elapsedLabel.font.deriveFont(Font.BOLD, elapsedLabel.font.size + 1f)
        elapsedLabel.foreground = JBColor.GRAY

        val statusRow = JPanel(BorderLayout())
        val statusLine = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        statusLine.add(statusLabel)
        statusLine.add(originBadge)
        statusRow.add(statusLine, BorderLayout.CENTER)
        statusRow.add(elapsedLabel, BorderLayout.EAST)
        statusRow.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        val cardsRow = JPanel(GridLayout(1, 5, 6, 0))
        cardsRow.add(targetRpsCard)
        cardsRow.add(actualRpsCard)
        cardsRow.add(errorRateCard)
        cardsRow.add(totalReqsCard)
        cardsRow.add(latencyCard)

        val cardsColumn = JPanel(BorderLayout(0, 6))
        cardsColumn.add(cardsRow, BorderLayout.NORTH)
        cardsColumn.add(errorMixStrip, BorderLayout.CENTER)

        val header = JPanel(BorderLayout())
        header.add(statusRow, BorderLayout.NORTH)
        header.add(cardsColumn, BorderLayout.CENTER)
        header.add(liveControl, BorderLayout.SOUTH)
        add(header, BorderLayout.NORTH)

        val center = JPanel(BorderLayout())
        center.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        center.add(rpsChart, BorderLayout.CENTER)
        center.add(stageTimeline, BorderLayout.SOUTH)
        add(center, BorderLayout.CENTER)
    }

    /** Call before starting a new process — clears prior run's state. */
    fun reset() {
        runStartMs = 0L
        knownBias = 0
        showOrigin(null)
        annotations.clear()
        reachedTerminalState = false
        stoppedByRequest = false
        currentProfile = null
        setRunning(false)
        liveControl.reset()
        statusLabel.text = "Starting…"
        elapsedLabel.text = "00:00"
        targetRpsCard.value = "—"
        actualRpsCard.value = "—"
        errorRateCard.value = "—"
        errorRateCard.setValueColor(null)
        totalReqsCard.value = "—"
        latencyCard.value = "—"
        errorRateCard.toolTipText = null
        latencyCard.toolTipText = null
        errorMixStrip.clear()
        rpsChart.clear()
        stageTimeline.clear()
    }

    /** True while a process is running and Stop should be enabled. Read by [StopGopherGlideRunAction]. */
    fun isRunning(): Boolean = running

    /** True once a stop was requested and the run has not ended yet: the Stop action then reads Force Stop. */
    fun isStopping(): Boolean = running && stopPhase != StopPhase.NONE

    /** Asks the process registered by [onProcessStarted] to stop. Called by [StopGopherGlideRunAction]. */
    fun stop() {
        controls?.requestStop()
    }

    /** Marks the run as started by someone other than the user; null clears the badge. */
    fun showOrigin(source: AnnotationSource?) {
        originBadge.text = if (source == AnnotationSource.AGENT) "🤖 STARTED BY AI AGENT" else ""
        originBadge.isVisible = source == AnnotationSource.AGENT
    }

    fun onProcessStarted(controls: RunControls) {
        this.controls = controls
        liveControl.bind(controls)
        setRunning(true)
    }

    private fun runElapsedSeconds(): Double =
        if (runStartMs == 0L) 0.0 else (System.currentTimeMillis() - runStartMs) / 1000.0

    private fun addAnnotation(annotation: RunAnnotation) {
        annotations.add(annotation)
        annotations.sortBy { it.elapsedSeconds }
        rpsChart.setAnnotations(annotations)
    }

    /**
     * Toolbar actions like [StopGopherGlideRunAction] only get their `update()` re-invoked when
     * [ActivityTracker]'s counter changes — IntelliJ no longer polls toolbars on a fixed timer.
     * Without this, the Stop icon stays enabled forever after a run ends.
     */
    private fun setRunning(value: Boolean) {
        running = value
        if (!value) {
            stopPhase = StopPhase.NONE
            liveControl.onEnded()
        }
        ActivityTracker.getInstance().inc()
    }

    /** Must be called on the EDT. Reflects how far a requested stop has got. */
    fun showStopPhase(phase: StopPhase) {
        if (!running) return
        stopPhase = phase
        liveControl.onStopRequested()
        statusLabel.text = when (phase) {
            StopPhase.AWAITING_ACK -> "■ Stopping…"
            StopPhase.FINALIZING -> "■ Stopping — saving snapshot…"
            StopPhase.INTERRUPTING -> "■ Stopping — interrupting…"
            StopPhase.NO_ACK_INTERRUPTING -> "■ Stopping — gg did not respond, interrupting…"
            StopPhase.NONE -> statusLabel.text
        }
        ActivityTracker.getInstance().inc()
    }

    /** Must be called on the EDT. */
    fun update(payload: HeartbeatPayload) {
        if (payload.isCommandError) return // a rejected command never ends the run
        val message = rebrand(payload.message)
        when (payload.event) {
            "started" -> {
                runStartMs = System.currentTimeMillis()
                liveControl.onStarted(payload.capabilities)
                currentProfile = payload.profile?.takeIf { it.isNotBlank() }
                stageTimeline.setStages(payload.stages)
                rpsChart.setKnownPeakRps(payload.stages?.maxOfOrNull { it.targetRps })
                rpsChart.setTotalDuration(payload.stages?.sumOf { it.durationSeconds })
                statusLabel.text = "● RUNNING — ${message.ifBlank { "Traffic simulation started" }}"
            }
            "heartbeat" -> {
                payload.elapsedSeconds?.let { runStartMs = System.currentTimeMillis() - (it * 1000).toLong() }
                val elapsedSec = runElapsedSeconds().toLong()
                val profileSuffix = currentProfile?.let { " — profile: $it" } ?: ""
                if (stopPhase == StopPhase.NONE) {
                    statusLabel.text = "● RUNNING — stage ${payload.stage}/${payload.totalStages}$profileSuffix"
                }
                elapsedLabel.text = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                targetRpsCard.value = "${payload.targetRps} rps"
                actualRpsCard.value = "%.1f rps".format(payload.actualRps)
                errorRateCard.value = "%.2f%%".format(payload.errorRate * 100)
                errorRateCard.setValueColor(if (payload.errorRate > 0.0) JBColor.RED else null)
                totalReqsCard.value = payload.totalRequests.toString()
                latencyCard.value = "%.1f / %.1f / %.1f".format(payload.p50Ms, payload.p95Ms, payload.p99Ms)
                payload.window?.let { showWindowHealth(payload, it) }
                errorMixStrip.update(payload)
                rpsChart.addDataPoint(elapsedSec.toDouble(), payload.actualRps, payload.targetRps)
                stageTimeline.update(payload.stage, payload.totalStages, elapsedSec.toDouble())
                payload.bias?.let {
                    knownBias = it
                    liveControl.onHeartbeat(it, payload.holdRps, payload.autopilot, payload.targetRps)
                }
            }
            "autopilot" -> liveControl.onAutopilotEvent(payload.mode, payload.state)
            "mark" -> if (!isOurs(payload)) {
                val at = payload.elapsedSeconds ?: runElapsedSeconds()
                liveControl.onUnsolicitedMark(payload.label.orEmpty().ifBlank { "mark" }, at, AnnotationSource.fromWire(payload.source))
            }
            "ack" -> onAck(payload)
            "finished" -> {
                setRunning(false)
                reachedTerminalState = true
                if (!stoppedByRequest) {
                    val outcome = AutopilotOutcomes.describe(payload.mode, payload.outcome, payload.ceilingRps)?.let { "$it · " }.orEmpty()
                    statusLabel.text = "✓ FINISHED — $outcome${message.ifBlank { "Traffic simulation completed" }}"
                    GopherGlideNotifications.onSuccessfulAction()
                }
            }
            "interrupted" -> {
                statusLabel.text = "■ STOPPED — ${message.ifBlank { "Run interrupted" }}"
                setRunning(false)
                reachedTerminalState = true
                stoppedByRequest = true
            }
            "stopped" -> {
                // The generic completion text would read oddly next to STOPPED, so only a snapshot line is shown.
                val detail = message.takeUnless { it.startsWith("Traffic simulation completed") }?.takeIf { it.isNotBlank() }
                statusLabel.text = "■ STOPPED" + (detail?.let { " — $it" } ?: "")
                setRunning(false)
                reachedTerminalState = true
                stoppedByRequest = true
            }
            "snap" -> statusLabel.text = "● RUNNING — $message"
            "error" -> {
                statusLabel.text = "✗ ERROR — $message"
                setRunning(false)
                reachedTerminalState = true
            }
        }
    }

    private fun isOurs(payload: HeartbeatPayload): Boolean = payload.id?.let { controls?.ownsCommand(it) } == true

    /** An ack of ours only keeps the bias current; one from an agent or another writer also lands on the chart. */
    private fun onAck(payload: HeartbeatPayload) {
        val bias = payload.bias
        val ours = isOurs(payload)
        if (payload.command == "bias" && bias != null) {
            val amount = bias - knownBias
            knownBias = bias
            if (!ours && amount != 0) {
                addAnnotation(RunAnnotation.BiasChange(amount, bias, atOf(payload), AnnotationSource.fromWire(payload.source)))
            }
        }
        if (ours) return
        val source = AnnotationSource.fromWire(payload.source)
        when (payload.command) {
            "hold" -> addAnnotation(RunAnnotation.HoldChange(payload.holdRps, atOf(payload), source))
            "release" -> addAnnotation(RunAnnotation.HoldChange(null, atOf(payload), source))
        }
    }

    private fun atOf(payload: HeartbeatPayload) = payload.elapsedSeconds ?: runElapsedSeconds()

    private fun showWindowHealth(payload: HeartbeatPayload, window: WindowHealthPayload) {
        errorRateCard.toolTipText = "Cumulative: %.2f%% · Last %ds: %.2f%% (%d samples)"
            .format(payload.errorRate * 100, window.seconds, window.errorRate * 100, window.samples)
        latencyCard.toolTipText = "Last %ds P99: %.1f ms (%d samples)".format(window.seconds, window.p99Ms, window.samples)
    }

    private fun rebrand(text: String): String =
        Regex("load test", RegexOption.IGNORE_CASE).replace(text, "traffic simulation")
            .replaceFirstChar { if (text.isNotEmpty() && text[0].isUpperCase()) it.uppercaseChar() else it }

    /**
     * Must be called on the EDT. Backstop for processes that exit without a final "finished"/"interrupted"/"error"
     * heartbeat — e.g. gg failing before it ever writes a heartbeat. Without this, the panel would otherwise sit
     * on "Starting…" forever with no indication anything went wrong.
     */
    fun onProcessTerminated(exitCode: Int, stderrTail: String = "") {
        setRunning(false)
        if (reachedTerminalState) return

        statusLabel.text = if (exitCode == 0) "✓ FINISHED" else "✗ EXITED (code $exitCode)"
        if (exitCode != 0 && stderrTail.isNotBlank()) {
            statusLabel.toolTipText = stderrTail
            statusLabel.text += " — ${stderrTail.lineSequence().first()}"
        }
        reachedTerminalState = true
        if (exitCode == 0) GopherGlideNotifications.onSuccessfulAction()
    }
}
