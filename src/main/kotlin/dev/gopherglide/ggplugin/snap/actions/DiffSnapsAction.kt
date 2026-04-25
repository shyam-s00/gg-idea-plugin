package dev.gopherglide.ggplugin.snap.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.execution.TerminalExecutor
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class DiffSnapsAction : AnAction("Compare Selected (Diff)", "Compare two snapshots", com.intellij.icons.AllIcons.Actions.Diff) {

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
        e.presentation.isEnabled = selectedSnaps.size == 2
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedSnaps = SnapToolWindowFactory.getSelectedSnaps(project)
        if (selectedSnaps.size == 2) {
            val id1 = selectedSnaps[0].internalIndex
            val id2 = selectedSnaps[1].internalIndex
            if (id1.isBlank() || id2.isBlank()) {
                com.intellij.openapi.ui.Messages.showWarningDialog(project, "Internal index is missing. Please click 'Refresh Snaps' on the toolbar to reload the data.", "Refresh Required")
                return
            }
            TerminalExecutor.execute(project, "snap", "diff", id1, id2)
        }
    }
}
