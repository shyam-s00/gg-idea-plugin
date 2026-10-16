package dev.gopherglide.ggplugin.snap.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.ChangeTiles
import dev.gopherglide.ggplugin.snap.RunTimeline
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/** A "Manual changes" header that collapses and expands the event list and its figures; the choice is remembered. */
class ManualChangesSection(
    timeline: RunTimeline,
    private val isCollapsed: () -> Boolean = { PropertiesComponent.getInstance().getBoolean(COLLAPSED_KEY, false) },
    private val setCollapsed: (Boolean) -> Unit = { PropertiesComponent.getInstance().setValue(COLLAPSED_KEY, it, false) }
) : JPanel(BorderLayout()) {
    private val title = JBLabel("MANUAL CHANGES").apply { font = font.deriveFont(Font.BOLD, font.size - 1f) }
    private val summary = JBLabel(timeline.summary()).apply { foreground = JBColor.GRAY }
    private val body = buildBody(timeline)

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

    private fun buildBody(timeline: RunTimeline): JPanel {
        val table = ManualChangesTable(timeline.events)
        val visibleRows = timeline.events.size.coerceIn(MIN_ROWS, MAX_ROWS)
        val list = JBScrollPane(table).apply {
            border = JBUI.Borders.customLine(LINE)
            preferredSize = Dimension(0, table.rowHeight * visibleRows + JBUI.scale(2))
        }
        return JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
            add(list, BorderLayout.CENTER)
            timeline.stats()?.let {
                val tiles = ChangeTilesPanel(ChangeTiles.from(it, timeline.durationSeconds))
                tiles.preferredSize = Dimension(JBUI.scale(TILES_WIDTH), tiles.preferredSize.height)
                add(tiles, BorderLayout.EAST)
            }
        }
    }

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
        const val MIN_ROWS = 3
        const val MAX_ROWS = 6
        const val TILES_WIDTH = 300
        val LINE = JBColor(0xD3D3D3, 0x393B40)
    }
}
