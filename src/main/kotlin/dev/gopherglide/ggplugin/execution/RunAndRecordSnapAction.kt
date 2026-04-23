package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class RunAndRecordSnapAction : AnAction("Run && Record Snapshot...", "Execute GG and record a snapshot", com.intellij.icons.AllIcons.Actions.Execute) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.name.endsWith(".gg.yaml")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val tag = Messages.showInputDialog(
            project,
            "Enter Snapshot Tag (leave blank for default):",
            "Record Snapshot",
            Messages.getQuestionIcon()
        )

        if (tag != null) {
            executeTest(project, file, tag)
        }
    }

    companion object {
        private val log = Logger.getInstance(RunAndRecordSnapAction::class.java)

        fun executeTest(project: Project, file: VirtualFile, tag: String) {
            log.info("Starting Gopher-Glide snap run for: ${file.path}")
            val args = mutableListOf(file.path, "--snap")
            if (tag.isNotBlank()) {
                args.add("--snap-tag")
                args.add(tag.trim())
            }
            TerminalExecutor.execute(project, *args.toTypedArray())
        }
    }
}
