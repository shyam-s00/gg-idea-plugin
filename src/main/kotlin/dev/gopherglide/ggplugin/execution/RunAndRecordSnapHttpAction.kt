package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class RunAndRecordSnapHttpAction : AnAction("Run && Record Snapshot...", "Execute a traffic simulation and record a snapshot for this HTTP file", com.intellij.icons.AllIcons.Actions.Execute) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.name.endsWith(".http")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val httpFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        executeTest(project, httpFile)
    }

    companion object {
        fun executeTest(project: Project, httpFile: VirtualFile, runInTerminal: Boolean = false) {
            val parentDir = httpFile.parent ?: return
            val existingYaml = parentDir.children.firstOrNull { it.name.endsWith(".gg.yaml") }

            val tag = Messages.showInputDialog(
                project,
                "Enter Snapshot Tag (leave blank for default):",
                "Record Snapshot",
                Messages.getQuestionIcon()
            ) ?: return

            val args = if (existingYaml != null) {
                mutableListOf(existingYaml.path)
            } else {
                mutableListOf("--profile", ProfileCatalog.DEFAULT_ZERO_CONFIG_PROFILE, "--http-file", httpFile.path)
            }
            args.add("--snap")
            if (tag.isNotBlank()) {
                args.add("--snap-tag")
                args.add(tag.trim())
            }

            if (runInTerminal) {
                TerminalExecutor.execute(project, *args.toTypedArray())
            } else {
                GopherGlideExecutor.execute(project, *args.toTypedArray())
            }
        }

        /**
         * Explicit opt-in to the interactive TUI in a terminal.
         * TODO: pass a capped-fps flag to gg here once it exists, so this path no longer risks the CPU/crash regression.
         */
        fun executeTestInteractive(project: Project, httpFile: VirtualFile) =
            executeTest(project, httpFile, runInTerminal = true)
    }
}
