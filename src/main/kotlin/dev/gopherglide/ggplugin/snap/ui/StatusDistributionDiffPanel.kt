package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.EndpointSnap
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridLayout
import javax.swing.JPanel

/** Baseline and target status distributions side by side for the selected endpoint. */
class StatusDistributionDiffPanel(endpointA: EndpointSnap?, endpointB: EndpointSnap?) : JPanel(GridLayout(1, 2, JBUI.scale(8), 0)) {

    init {
        add(side("Baseline", endpointA))
        add(side("Target", endpointB))
    }

    private fun side(label: String, endpoint: EndpointSnap?): JPanel {
        val panel = JPanel(BorderLayout())
        val title = JBLabel(label).apply {
            font = font.deriveFont(Font.BOLD, font.size2D - 1f)
            foreground = JBColor.GRAY
            border = JBUI.Borders.emptyBottom(2)
        }
        panel.add(title, BorderLayout.NORTH)
        panel.add(
            if (endpoint != null) {
                StatusDistributionPanel(endpoint.statusDist, endpoint.errorRate)
            } else {
                JBLabel("Endpoint not present in this snapshot.").apply { foreground = JBColor.GRAY }
            },
            BorderLayout.CENTER
        )
        return panel
    }
}
