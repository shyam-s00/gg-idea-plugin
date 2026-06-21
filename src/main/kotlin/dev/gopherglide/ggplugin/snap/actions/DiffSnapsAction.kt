package dev.gopherglide.ggplugin.snap.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.snap.ui.SnapDiffDialog
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
            val (baseline, compare) = selectedSnaps.sortedBy { it.meta?.startTime ?: "" }
            SnapDiffDialog.show(project, baseline, compare)
        }
    }
}
