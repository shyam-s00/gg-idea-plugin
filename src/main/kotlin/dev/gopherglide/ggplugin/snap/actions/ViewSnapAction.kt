package dev.gopherglide.ggplugin.snap.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.execution.TerminalExecutor
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class ViewSnapAction : AnAction("View Detail", "View snapshot details", com.intellij.icons.AllIcons.Actions.PreviewDetails) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val selectedSnaps = SnapToolWindowFactory.getSelectedSnaps(project)
        e.presentation.isEnabled = selectedSnaps.size == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedSnaps = SnapToolWindowFactory.getSelectedSnaps(project)
        if (selectedSnaps.size == 1) {
            val snapId = selectedSnaps[0].internalIndex
            if (snapId.isBlank()) {
                com.intellij.openapi.ui.Messages.showWarningDialog(project, "Internal index is missing. Please click 'Refresh Snaps' on the toolbar to reload the data.", "Refresh Required")
                return
            }
            TerminalExecutor.execute(project, "snap", "view", snapId)
        }
    }
}
