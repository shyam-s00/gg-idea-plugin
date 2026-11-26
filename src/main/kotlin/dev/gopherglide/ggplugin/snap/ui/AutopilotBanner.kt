package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.AutopilotSummary
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel

/** The autopilot's outcome above a snapshot's timeline. */
class AutopilotBanner(summary: AutopilotSummary) : JPanel(BorderLayout(JBUI.scale(12), 0)) {
    val headline = JBLabel(summary.headline).apply {
        font = font.deriveFont(Font.BOLD)
        foreground = when (summary.tone) {
            AutopilotSummary.Tone.GOOD -> JBColor(0x1B7A3A, 0x7FD39B)
            AutopilotSummary.Tone.WARN -> JBColor(0xB26A00, 0xE8B341)
            AutopilotSummary.Tone.NEUTRAL -> JBColor(0x1F5FA8, 0x9ACBFA)
        }
    }
    val detail = JBLabel(summary.detail).apply { foreground = JBColor.GRAY }

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0xD3D3D3, 0x3C3F41), 0, 0, 1, 0),
            JBUI.Borders.empty(6, 10)
        )
        add(headline, BorderLayout.WEST)
        add(detail, BorderLayout.CENTER)
    }
}
