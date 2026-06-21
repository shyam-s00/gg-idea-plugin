package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ide.ActivityTracker
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import java.awt.BorderLayout
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
class GopherGlideRunPanel : JPanel(BorderLayout()) {
    private val statusLabel = JBLabel("Idle")
    private val elapsedLabel = JBLabel("00:00")
    private val targetRpsCard = MetricCard("TARGET RPS")
    private val actualRpsCard = MetricCard("ACTUAL RPS")
    private val errorRateCard = MetricCard("ERROR RATE")
    private val totalReqsCard = MetricCard("TOTAL REQUESTS")
    private val latencyCard = MetricCard("P50 / P95 / P99 (ms)")
    private val rpsChart = RpsChartComponent()
    private val stageTimeline = StageTimelineComponent()
    private var runStartMs = 0L
    private var reachedTerminalState = false
    private var currentProfile: String? = null
    private var stopCallback: (() -> Unit)? = null
    private var running = false

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, statusLabel.font.size + 1f)
        elapsedLabel.font = elapsedLabel.font.deriveFont(Font.BOLD, elapsedLabel.font.size + 1f)
        elapsedLabel.foreground = JBColor.GRAY

        val statusRow = JPanel(BorderLayout())
        statusRow.add(statusLabel, BorderLayout.CENTER)
        statusRow.add(elapsedLabel, BorderLayout.EAST)
        statusRow.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        val cardsRow = JPanel(GridLayout(1, 5, 6, 0))
        cardsRow.add(targetRpsCard)
        cardsRow.add(actualRpsCard)
        cardsRow.add(errorRateCard)
        cardsRow.add(totalReqsCard)
        cardsRow.add(latencyCard)

        val header = JPanel(BorderLayout())
        header.add(statusRow, BorderLayout.NORTH)
        header.add(cardsRow, BorderLayout.CENTER)
        add(header, BorderLayout.NORTH)

        val center = JPanel(BorderLayout())
        center.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        center.add(rpsChart, BorderLayout.CENTER)
        center.add(stageTimeline, BorderLayout.SOUTH)
        add(center, BorderLayout.CENTER)
    }

    /** Call before starting a new process — clears prior run's state. */
    fun reset() {
        runStartMs = System.currentTimeMillis()
        reachedTerminalState = false
        currentProfile = null
        setRunning(false)
        statusLabel.text = "Starting…"
        elapsedLabel.text = "00:00"
        targetRpsCard.value = "—"
        actualRpsCard.value = "—"
        errorRateCard.value = "—"
        errorRateCard.setValueColor(null)
        totalReqsCard.value = "—"
        latencyCard.value = "—"
        rpsChart.clear()
        stageTimeline.clear()
    }

    /** True while a process is running and Stop should be enabled. Read by [StopGopherGlideRunAction]. */
    fun isRunning(): Boolean = running

    /** Invokes the callback registered by [onProcessStarted], if any. Called by [StopGopherGlideRunAction]. */
    fun stop() {
        stopCallback?.invoke()
    }

    fun onProcessStarted(onStop: () -> Unit) {
        stopCallback = onStop
        setRunning(true)
    }

    /**
     * Toolbar actions like [StopGopherGlideRunAction] only get their `update()` re-invoked when
     * [ActivityTracker]'s counter changes — IntelliJ no longer polls toolbars on a fixed timer.
     * Without this, the Stop icon stays enabled forever after a run ends.
     */
    private fun setRunning(value: Boolean) {
        running = value
        ActivityTracker.getInstance().inc()
    }

    /** Must be called on the EDT. */
    fun update(payload: HeartbeatPayload) {
        val message = rebrand(payload.message)
        when (payload.event) {
            "started" -> {
                currentProfile = payload.profile?.takeIf { it.isNotBlank() }
                stageTimeline.setStages(payload.stages)
                rpsChart.setKnownPeakRps(payload.stages?.maxOfOrNull { it.targetRps })
                rpsChart.setTotalDuration(payload.stages?.sumOf { it.durationSeconds })
                statusLabel.text = "● RUNNING — ${message.ifBlank { "Traffic simulation started" }}"
            }
            "heartbeat" -> {
                val elapsedSec = (System.currentTimeMillis() - runStartMs) / 1000
                val profileSuffix = currentProfile?.let { " — profile: $it" } ?: ""
                statusLabel.text = "● RUNNING — stage ${payload.stage}/${payload.totalStages}$profileSuffix"
                elapsedLabel.text = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                targetRpsCard.value = "${payload.targetRps} rps"
                actualRpsCard.value = "%.1f rps".format(payload.actualRps)
                errorRateCard.value = "%.2f%%".format(payload.errorRate * 100)
                errorRateCard.setValueColor(if (payload.errorRate > 0.0) JBColor.RED else null)
                totalReqsCard.value = payload.totalRequests.toString()
                latencyCard.value = "%.1f / %.1f / %.1f".format(payload.p50Ms, payload.p95Ms, payload.p99Ms)
                rpsChart.addDataPoint(elapsedSec.toDouble(), payload.actualRps, payload.targetRps)
                stageTimeline.update(payload.stage, payload.totalStages, elapsedSec.toDouble())
            }
            "finished" -> {
                statusLabel.text = "✓ FINISHED — ${message.ifBlank { "Traffic simulation completed" }}"
                setRunning(false)
                reachedTerminalState = true
            }
            "interrupted" -> {
                statusLabel.text = "■ STOPPED — ${message.ifBlank { "Run interrupted" }}"
                setRunning(false)
                reachedTerminalState = true
            }
            "snap" -> statusLabel.text = "● RUNNING — $message"
            "error" -> {
                statusLabel.text = "✗ ERROR — $message"
                setRunning(false)
                reachedTerminalState = true
            }
        }
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
    }
}
