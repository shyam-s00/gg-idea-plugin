package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.ChangeTile
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel

/** Four figures worked out from the bias changes, in a 2 x 2 grid. */
class ChangeTilesPanel(tiles: List<ChangeTile>) : JPanel(GridLayout(2, 2, JBUI.scale(8), JBUI.scale(8))) {
    init {
        isOpaque = false
        tiles.forEach { add(tile(it)) }
    }

    private fun tile(tile: ChangeTile) = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE), JBUI.Borders.empty(5, 10))
        add(JBLabel(tile.label).apply { font = font.deriveFont(font.size2D - 2f); foreground = JBColor.GRAY })
        add(JBLabel(tile.value).apply { font = font.deriveFont(Font.BOLD, font.size2D + 3f) })
        add(JBLabel(tile.detail).apply { font = font.deriveFont(font.size2D - 1f); foreground = JBColor.GRAY })
    }

    private companion object {
        val LINE = JBColor(0xD3D3D3, 0x393B40)
    }
}
