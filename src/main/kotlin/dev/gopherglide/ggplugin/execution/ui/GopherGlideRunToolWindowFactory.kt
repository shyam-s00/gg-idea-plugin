package dev.gopherglide.ggplugin.execution.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.gopherglide.ggplugin.execution.actions.StopGopherGlideRunAction
import dev.gopherglide.ggplugin.ui.GopherGlideToolWindowFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

object GopherGlideRunToolWindowFactory {
    private val panels = mutableMapOf<Project, GopherGlideRunPanel>()
    private val wrappers = mutableMapOf<Project, JComponent>()

    /** Builds the Run tab content: a Stop toolbar above the dashboard panel, instead of a full-width button. */
    fun buildPanel(project: Project): JComponent {
        val panel = GopherGlideRunPanel()
        panels[project] = panel

        val actionGroup = DefaultActionGroup().apply { add(StopGopherGlideRunAction()) }
        val toolbar = ActionManager.getInstance().createActionToolbar("GopherGlideRunToolbar", actionGroup, true)
        toolbar.targetComponent = panel

        val wrapper = JPanel(BorderLayout())
        wrapper.add(toolbar.component, BorderLayout.NORTH)
        wrapper.add(panel, BorderLayout.CENTER)
        wrappers[project] = wrapper
        return wrapper
    }

    /** Read by [StopGopherGlideRunAction] to find the panel for the action's target project. */
    fun getPanel(project: Project): GopherGlideRunPanel? = panels[project]

    /** Shows the tool window, selects the Run tab, and returns the panel — or null if content hasn't been created yet. */
    fun showAndGetPanel(project: Project): GopherGlideRunPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GopherGlideToolWindowFactory.TOOL_WINDOW_ID)
        toolWindow?.show()
        val panel = panels[project] ?: return null
        val wrapper = wrappers[project]
        toolWindow?.contentManager?.contents?.firstOrNull { it.component == wrapper }?.let {
            toolWindow.contentManager.setSelectedContent(it)
        }
        return panel
    }
}
