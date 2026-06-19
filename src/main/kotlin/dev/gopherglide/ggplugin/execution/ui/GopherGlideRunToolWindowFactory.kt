package dev.gopherglide.ggplugin.execution.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import dev.gopherglide.ggplugin.GopherGlideIcons

class GopherGlideRunToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(GopherGlideIcons.SidebarIcon)

        val panel = GopherGlideRunPanel()
        panels[project] = panel

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        private const val ID = "Gopher Glide Run"
        private val panels = mutableMapOf<Project, GopherGlideRunPanel>()

        /** Shows the tool window and returns its panel, or null if content hasn't been created yet. */
        fun showAndGetPanel(project: Project): GopherGlideRunPanel? {
            ToolWindowManager.getInstance(project).getToolWindow(ID)?.show()
            return panels[project]
        }
    }
}
