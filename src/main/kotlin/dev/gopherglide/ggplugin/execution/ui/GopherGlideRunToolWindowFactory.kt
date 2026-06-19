package dev.gopherglide.ggplugin.execution.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.gopherglide.ggplugin.ui.GopherGlideToolWindowFactory

object GopherGlideRunToolWindowFactory {
    private val panels = mutableMapOf<Project, GopherGlideRunPanel>()

    fun buildPanel(project: Project): GopherGlideRunPanel {
        val panel = GopherGlideRunPanel()
        panels[project] = panel
        return panel
    }

    /** Shows the tool window, selects the Run tab, and returns the panel — or null if content hasn't been created yet. */
    fun showAndGetPanel(project: Project): GopherGlideRunPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GopherGlideToolWindowFactory.TOOL_WINDOW_ID)
        toolWindow?.show()
        val panel = panels[project] ?: return null
        toolWindow?.contentManager?.contents?.firstOrNull { it.component == panel }?.let {
            toolWindow.contentManager.setSelectedContent(it)
        }
        return panel
    }
}
