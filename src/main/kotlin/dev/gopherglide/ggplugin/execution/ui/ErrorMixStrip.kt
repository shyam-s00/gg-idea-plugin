package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.execution.ErrorEndpoint
import dev.gopherglide.ggplugin.execution.ErrorMixPayload
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JPanel

/** Wording for what failed recently. */
object ErrorMixText {
    private const val MAX_SHOWN_ENDPOINTS = 3

    /** "5xx 14 · 4xx 3": only the classes that occurred. */
    fun counts(mix: ErrorMixPayload): String =
        listOf("5xx" to mix.http5xx, "4xx" to mix.http4xx, "transport" to mix.transport, "extract" to mix.extract)
            .filter { it.second > 0 }
            .joinToString(" · ") { "${it.first} ${it.second}" }

    /** "POST /api/checkout (14× 503) · GET /users (3× 429)"; null when gg named no endpoint. */
    fun topFailing(mix: ErrorMixPayload, limit: Int = MAX_SHOWN_ENDPOINTS): String? =
        mix.topEndpoints.take(limit).takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Top failing: ") { endpoint(it) }

    fun endpoint(entry: ErrorEndpoint): String {
        val what = if (entry.code > 0) entry.code.toString() else entry.kind.removePrefix("http_")
        return "${entry.endpoint} (${entry.n}× $what)"
    }

    fun tooltip(mix: ErrorMixPayload): String {
        val lines = mutableListOf("Last ${mix.windowSeconds}s: ${mix.sampled} failed request(s) sampled")
        mix.topCodes.takeIf { it.isNotEmpty() }?.let { codes -> lines += "Status codes: " + codes.joinToString(" · ") { "${it.code} (${it.n}×)" } }
        mix.topEndpoints.forEach { lines += endpoint(it) }
        if (mix.capped) lines += "Sampling was capped; counts are a floor."
        return lines.joinToString(prefix = "<html>", separator = "<br>", postfix = "</html>") { it.replace("&", "&amp;").replace("<", "&lt;") }
    }
}

/**
 * One line under the metric cards listing what failed in gg's recent window. Hidden until a heartbeat carries
 * window health (so older binaries never show it), then always the same height so the layout does not jump.
 */
class ErrorMixStrip : JPanel(BorderLayout(JBUI.scale(12), 0)) {
    private val headline = JBLabel(BLANK).apply { font = font.deriveFont(Font.BOLD) }
    private val detail = JBLabel(BLANK).apply { foreground = JBColor.GRAY }

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xD3D3D3, 0x4A4D54)),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        )
        add(headline, BorderLayout.WEST)
        add(detail, BorderLayout.CENTER)
        isVisible = false
    }

    fun update(payload: HeartbeatPayload) {
        val mix = payload.errorMix
        if (mix == null && payload.window == null) return
        isVisible = true
        if (mix != null && mix.sampled > 0) {
            val counts = ErrorMixText.counts(mix)
            headline.text = "▲ Recent errors (${mix.windowSeconds}s)" + if (counts.isEmpty()) "" else ": $counts"
            headline.foreground = JBColor(0xB26A00, 0xE8B341)
            detail.text = ErrorMixText.topFailing(mix) ?: BLANK
            toolTipText = ErrorMixText.tooltip(mix)
        } else {
            headline.text = "✓ No errors in the last ${mix?.windowSeconds ?: DEFAULT_WINDOW_SECONDS}s"
            headline.foreground = JBColor.GRAY
            detail.text = BLANK
            toolTipText = null
        }
    }

    fun clear() {
        isVisible = false
        headline.text = BLANK
        detail.text = BLANK
        toolTipText = null
    }

    private companion object {
        const val BLANK = " "
        const val DEFAULT_WINDOW_SECONDS = 10
    }
}
