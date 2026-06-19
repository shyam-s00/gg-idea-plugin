package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RunGopherGlideAction : AnAction("Run GG", "Execute the Gopher-Glide traffic simulation", com.intellij.icons.AllIcons.Actions.Execute) {

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
        executeTest(project, file)
    }

    companion object {
        private val log = Logger.getInstance(RunGopherGlideAction::class.java)

        fun executeTest(project: Project, file: VirtualFile, runInTerminal: Boolean = false) {
            log.info("Starting Gopher-Glide run for: ${file.path}")
            if (runInTerminal) {
                TerminalExecutor.execute(project, file.path)
            } else {
                GopherGlideExecutor.execute(project, file.path)
            }
        }

        /**
         * Explicit opt-in to the interactive TUI in a terminal.
         * TODO: pass a capped-fps flag to gg here once it exists, so this path no longer risks the CPU/crash regression.
         */
        fun executeTestInteractive(project: Project, file: VirtualFile) =
            executeTest(project, file, runInTerminal = true)
    }
}
