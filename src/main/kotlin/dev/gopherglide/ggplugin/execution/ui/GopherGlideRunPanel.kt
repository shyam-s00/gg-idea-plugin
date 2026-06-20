package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Run dashboard: status header + metric labels + a scaled RPS chart + a stage timeline,
 * updated once per ~5s heartbeat.
 */
class GopherGlideRunPanel : JPanel(BorderLayout()) {
    private val statusLabel = JBLabel("Idle")
    private val targetRpsValue = JBLabel("—")
    private val actualRpsValue = JBLabel("—")
    private val errorRateValue = JBLabel("—")
    private val totalReqsValue = JBLabel("—")
    private val latencyValue = JBLabel("—")
    private val rpsChart = RpsChartComponent()
    private val stageTimeline = StageTimelineComponent()
    private val stopButton = JButton("Stop")
    private var runStartMs = 0L
    private var reachedTerminalState = false
    private var currentProfile: String? = null

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, statusLabel.font.size + 1f)

        val header = panel {
            row { cell(statusLabel) }
            row("Target RPS:") { cell(targetRpsValue) }
            row("Actual RPS:") { cell(actualRpsValue) }
            row("Error Rate:") { cell(errorRateValue) }
            row("Total Requests:") { cell(totalReqsValue) }
            row("p50 / p95 / p99:") { cell(latencyValue) }
        }
        add(header, BorderLayout.NORTH)

        val center = JPanel(BorderLayout())
        center.border = BorderFactory.createEmptyBorder(8, 0, 8, 0)
        center.add(rpsChart, BorderLayout.CENTER)
        center.add(stageTimeline, BorderLayout.SOUTH)
        add(center, BorderLayout.CENTER)

        stopButton.isEnabled = false
        add(stopButton, BorderLayout.SOUTH)
    }

    /** Call before starting a new process — clears prior run's state. */
    fun reset() {
        runStartMs = System.currentTimeMillis()
        reachedTerminalState = false
        currentProfile = null
        statusLabel.text = "Starting…"
        targetRpsValue.text = "—"
        actualRpsValue.text = "—"
        errorRateValue.text = "—"
        totalReqsValue.text = "—"
        latencyValue.text = "—"
        rpsChart.clear()
        stageTimeline.clear()
    }

    fun onProcessStarted(onStop: () -> Unit) {
        for (l in stopButton.actionListeners) stopButton.removeActionListener(l)
        stopButton.addActionListener { onStop() }
        stopButton.isEnabled = true
    }

    /** Must be called on the EDT. */
    fun update(payload: HeartbeatPayload) {
        val message = rebrand(payload.message)
        when (payload.event) {
            "started" -> {
                currentProfile = payload.profile?.takeIf { it.isNotBlank() }
                stageTimeline.setStages(payload.stages)
                rpsChart.setKnownPeakRps(payload.stages?.maxOfOrNull { it.targetRps })
                statusLabel.text = "● RUNNING — ${message.ifBlank { "Traffic simulation started" }}"
            }
            "heartbeat" -> {
                val elapsedSec = (System.currentTimeMillis() - runStartMs) / 1000
                val profileSuffix = currentProfile?.let { " — profile: $it" } ?: ""
                statusLabel.text = "● RUNNING — stage ${payload.stage}/${payload.totalStages}$profileSuffix — ${elapsedSec}s"
                targetRpsValue.text = "${payload.targetRps} rps"
                actualRpsValue.text = "%.1f rps".format(payload.actualRps)
                errorRateValue.text = "%.2f%%".format(payload.errorRate * 100)
                totalReqsValue.text = payload.totalRequests.toString()
                latencyValue.text = "%.1f / %.1f / %.1f ms".format(payload.p50Ms, payload.p95Ms, payload.p99Ms)
                rpsChart.addDataPoint(payload.actualRps, payload.targetRps)
                stageTimeline.update(payload.stage, payload.totalStages, elapsedSec.toDouble())
            }
            "finished" -> {
                statusLabel.text = "✓ FINISHED — ${message.ifBlank { "Traffic simulation completed" }}"
                stopButton.isEnabled = false
                reachedTerminalState = true
            }
            "interrupted" -> {
                statusLabel.text = "■ STOPPED — ${message.ifBlank { "Run interrupted" }}"
                stopButton.isEnabled = false
                reachedTerminalState = true
            }
            "snap" -> statusLabel.text = "● RUNNING — $message"
            "error" -> {
                statusLabel.text = "✗ ERROR — $message"
                stopButton.isEnabled = false
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
        stopButton.isEnabled = false
        if (reachedTerminalState) return

        statusLabel.text = if (exitCode == 0) "✓ FINISHED" else "✗ EXITED (code $exitCode)"
        if (exitCode != 0 && stderrTail.isNotBlank()) {
            statusLabel.toolTipText = stderrTail
            statusLabel.text += " — ${stderrTail.lineSequence().first()}"
        }
        reachedTerminalState = true
    }
}
