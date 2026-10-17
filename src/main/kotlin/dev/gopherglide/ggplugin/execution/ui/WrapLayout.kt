package dev.gopherglide.ggplugin.execution.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout

/** A [FlowLayout] whose preferred height grows when its row has to wrap onto more lines. */
internal class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)

    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, preferred = false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension = synchronized(target.treeLock) {
        val available = target.width.takeIf { it > 0 } ?: target.parent?.width?.takeIf { it > 0 } ?: Int.MAX_VALUE
        val insets = target.insets
        val maxRow = available - insets.left - insets.right - hgap * 2
        var width = 0
        var height = 0
        var rowWidth = 0
        var rowHeight = 0
        fun endRow() {
            width = maxOf(width, rowWidth)
            if (height > 0) height += vgap
            height += rowHeight
            rowWidth = 0
            rowHeight = 0
        }
        for (child in target.components.filter { it.isVisible }) {
            val size = if (preferred) child.preferredSize else child.minimumSize
            if (rowWidth > 0 && rowWidth + hgap + size.width > maxRow) endRow()
            rowWidth += if (rowWidth > 0) hgap + size.width else size.width
            rowHeight = maxOf(rowHeight, size.height)
        }
        endRow()
        Dimension(width + insets.left + insets.right + hgap * 2, height + insets.top + insets.bottom + vgap * 2)
    }
}
