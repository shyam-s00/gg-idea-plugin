package dev.gopherglide.ggplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.gopherglide.ggplugin.GopherGlideIcons
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunToolWindowFactory
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class GopherGlideToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(GopherGlideIcons.SidebarIcon)
        val contentFactory = ContentFactory.getInstance()

        val runPanel = GopherGlideRunToolWindowFactory.buildPanel(project)
        toolWindow.contentManager.addContent(contentFactory.createContent(runPanel, "Run", false))

        val snapsPanel = SnapToolWindowFactory.buildPanel(project)
        toolWindow.contentManager.addContent(contentFactory.createContent(snapsPanel, "Snaps", false))
    }

    companion object {
        const val TOOL_WINDOW_ID = "Gopher Glide"
    }
}
