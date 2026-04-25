package dev.gopherglide.ggplugin.snap.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class RefreshSnapsAction : AnAction("Refresh", "Reload snapshots from disk", com.intellij.icons.AllIcons.Actions.Refresh) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SnapToolWindowFactory.refreshTable(project)
    }
}
