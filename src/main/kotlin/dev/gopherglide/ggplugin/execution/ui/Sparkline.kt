package dev.gopherglide.ggplugin.execution.ui

import com.intellij.ui.JBColor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/** Minimal line sparkline fed one point per heartbeat (~5s) — repaints are this cheap by construction. */
class Sparkline(private val maxPoints: Int = 40) : JComponent() {
    private val values = mutableListOf<Double>()

    init {
        preferredSize = Dimension(200, 50)
        minimumSize = Dimension(120, 40)
    }

    fun addValue(value: Double) {
        values.add(value)
        if (values.size > maxPoints) values.removeAt(0)
        repaint()
    }

    fun clear() {
        values.clear()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (values.size < 2) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width.toDouble()
        val h = height.toDouble()
        val max = values.max().coerceAtLeast(1.0)
        val stepX = w / (values.size - 1)

        g2.color = JBColor(0x3592C4, 0x589DF6)
        var prevX = 0.0
        var prevY = h - (values[0] / max) * h
        for (i in 1 until values.size) {
            val x = i * stepX
            val y = h - (values[i] / max) * h
            g2.drawLine(prevX.toInt(), prevY.toInt(), x.toInt(), y.toInt())
            prevX = x
            prevY = y
        }
    }
}
