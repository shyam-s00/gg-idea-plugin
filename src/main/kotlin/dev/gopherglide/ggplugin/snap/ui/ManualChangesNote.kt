package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

/** The one-line stand-in for [ManualChangesSection] when a snapshot has no marks or bias changes to show. */
class ManualChangesNote(text: String) : JPanel(BorderLayout()) {
    val label = JBLabel(text).apply { foreground = JBColor.GRAY }

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0xD3D3D3, 0x3C3F41), 0, 0, 1, 0),
            JBUI.Borders.empty(4, 10, 6, 10)
        )
        add(label, BorderLayout.CENTER)
    }
}
