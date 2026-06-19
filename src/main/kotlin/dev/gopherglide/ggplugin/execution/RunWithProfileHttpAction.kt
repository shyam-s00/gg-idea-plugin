package dev.gopherglide.ggplugin.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RunWithProfileHttpAction : AnAction(
    "Run with Profile...",
    "Pick one of gg's built-in load profiles and run it against this HTTP file",
    AllIcons.Actions.ListFiles
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.name.endsWith(".http")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val httpFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        showProfilePicker(project, httpFile, e.dataContext)
    }

    companion object {
        fun showProfilePicker(
            project: Project,
            httpFile: VirtualFile,
            dataContext: DataContext,
            runInTerminal: Boolean = false
        ) {
            val actionGroup = DefaultActionGroup()
            var currentCategory: ProfileCategory? = null
            for (profile in ProfileCatalog.profiles) {
                if (profile.category != currentCategory) {
                    actionGroup.addSeparator(profile.category.displayName)
                    currentCategory = profile.category
                }
                actionGroup.add(profileAction(profile) { promptOverridesAndRun(project, httpFile, profile, runInTerminal) })
            }

            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "Run with Profile",
                actionGroup,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            popup.showInBestPositionFor(dataContext)
        }

        private fun profileAction(profile: GgProfile, onChosen: () -> Unit): AnAction =
            object : AnAction(profile.name) {
                override fun actionPerformed(e: AnActionEvent) = onChosen()
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            }

        private fun promptOverridesAndRun(project: Project, httpFile: VirtualFile, profile: GgProfile, runInTerminal: Boolean) {
            val dialog = ProfileOverrideDialog(project, profile)
            if (!dialog.showAndGet()) return

            val args = mutableListOf("--profile", profile.name, "--http-file", httpFile.path)
            dialog.peakRpsOverride?.let {
                args.add("--peak-rps")
                args.add(it.toString())
            }
            dialog.durationOverride?.let {
                args.add("--duration")
                args.add(it)
            }

            if (runInTerminal) {
                TerminalExecutor.execute(project, *args.toTypedArray())
            } else {
                GopherGlideExecutor.execute(project, *args.toTypedArray())
            }
        }
    }
}
