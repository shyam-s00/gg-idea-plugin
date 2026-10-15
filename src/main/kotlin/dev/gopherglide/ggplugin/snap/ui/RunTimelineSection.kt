package dev.gopherglide.ggplugin.snap.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.RunTimeline
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/** A "Run timeline" header that collapses and expands a [RunTimelineStrip]; the choice is remembered. */
class RunTimelineSection(
    timeline: RunTimeline,
    private val isCollapsed: () -> Boolean = { PropertiesComponent.getInstance().getBoolean(COLLAPSED_KEY, false) },
    private val setCollapsed: (Boolean) -> Unit = { PropertiesComponent.getInstance().setValue(COLLAPSED_KEY, it, false) }
) : JPanel(BorderLayout()) {
    private val title = JBLabel("RUN TIMELINE").apply { font = font.deriveFont(Font.BOLD, font.size - 1f) }
    private val summary = JBLabel(timeline.summary()).apply { foreground = JBColor.GRAY }
    private val body = RunTimelineStrip(timeline)

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0xD3D3D3, 0x3C3F41), 0, 0, 1, 0),
            JBUI.Borders.empty(4, 10, 6, 10)
        )
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(title, BorderLayout.WEST)
            add(summary, BorderLayout.EAST)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = toggle()
            })
        }
        add(header, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        apply(isCollapsed())
    }

    val isExpanded: Boolean get() = body.isVisible

    fun toggle() {
        val collapse = body.isVisible
        setCollapsed(collapse)
        apply(collapse)
        revalidate()
        repaint()
    }

    private fun apply(collapsed: Boolean) {
        body.isVisible = !collapsed
        title.icon = if (collapsed) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown
    }

    private companion object {
        const val COLLAPSED_KEY = "gopherglide.snap.runTimeline.collapsed"
    }
}
