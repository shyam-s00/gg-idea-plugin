package dev.gopherglide.ggplugin.snap.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class CopySnapReportMarkdownAction : AnAction(
    "Copy PR Markdown",
    "Copy the selected snapshot's report as Markdown for a pull request description",
    AllIcons.Actions.Copy
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && SnapToolWindowFactory.getSelectedSnaps(project).size == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SnapToolWindowFactory.getSelectedSnaps(project).singleOrNull()?.let { SnapReportActions.copyMarkdown(project, it) }
    }
}
