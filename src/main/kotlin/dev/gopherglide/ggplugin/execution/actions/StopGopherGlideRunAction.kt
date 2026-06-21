package dev.gopherglide.ggplugin.execution.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunToolWindowFactory

/** Toolbar action for the Run tab — replaces the old full-width "Stop" button inside the panel itself. */
class StopGopherGlideRunAction : AnAction("Stop", "Stop the running Gopher Glide process", AllIcons.Actions.Suspend) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val panel = project?.let { GopherGlideRunToolWindowFactory.getPanel(it) }
        e.presentation.isEnabled = panel?.isRunning() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        GopherGlideRunToolWindowFactory.getPanel(project)?.stop()
    }
}
