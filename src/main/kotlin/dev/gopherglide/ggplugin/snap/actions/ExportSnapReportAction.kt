package dev.gopherglide.ggplugin.snap.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class ExportSnapReportAction : AnAction(
    "Export Mission Report…",
    "Save a shareable report of the selected snapshot as Markdown or HTML",
    AllIcons.ToolbarDecorator.Export
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && SnapToolWindowFactory.getSelectedSnaps(project).size == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SnapToolWindowFactory.getSelectedSnaps(project).singleOrNull()?.let { SnapReportActions.export(project, it) }
    }
}
