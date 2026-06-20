package dev.gopherglide.ggplugin.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Config-driven counterpart to [RunGopherGlideHttpAction]: visible only when the `.http` file has
 * a sibling `.gg.yaml`. No profile/rps/duration fields — the config is the single source of truth
 * for everything except whether to snapshot, which is a CLI-only flag with no `config.yaml` field
 * to carry it (hence the shared [SnapOptionsDialog]).
 */
private fun siblingYaml(httpFile: VirtualFile): VirtualFile? =
    httpFile.parent?.children?.firstOrNull { it.name.endsWith(".gg.yaml") }

private fun isEnabled(e: AnActionEvent): Boolean {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    return file.name.endsWith(".http") && siblingYaml(file) != null
}

private fun promptAndRun(project: Project, yamlFile: VirtualFile) {
    val dialog = SnapOptionsDialog(project)
    if (!dialog.showAndGet()) return

    val args = mutableListOf(yamlFile.path)
    if (dialog.snapEnabled) {
        args.add("--snap")
        dialog.snapTag?.let {
            args.add("--snap-tag")
            args.add(it)
        }
    }

    GopherGlideExecutor.execute(project, *args.toTypedArray())
}

class RunGopherGlideConfigHttpAction : AnAction(
    "Run GG (Config)",
    "Run this HTTP file's sibling .gg.yaml config directly — no profile or overrides",
    AllIcons.Actions.Execute
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEnabled(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val httpFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val yamlFile = siblingYaml(httpFile) ?: return
        runConfig(project, yamlFile)
    }

    companion object {
        fun runConfig(project: Project, yamlFile: VirtualFile) = promptAndRun(project, yamlFile)
    }
}
