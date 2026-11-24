package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.gopherglide.ggplugin.services.BinaryFeatureGate

/**
 * "Run GG" for `.gg.yaml` files themselves: shows the shared [SnapOptionsDialog] (config owns
 * everything else about the run), then executes directly — no profile picker, since the file
 * already *is* the config.
 */
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
        promptAndRun(project, file)
    }

    companion object {
        private val log = Logger.getInstance(RunGopherGlideAction::class.java)

        fun promptAndRun(project: Project, file: VirtualFile) {
            val dialog = SnapOptionsDialog(project)
            if (!dialog.showAndGet()) return

            val args = mutableListOf(file.path)
            if (dialog.snapEnabled) {
                args.add("--snap")
                dialog.snapTag?.let {
                    args.add("--snap-tag")
                    args.add(it)
                }
            }

            args.addAll(dialog.sloGuardArgs)

            log.info("Starting Gopher-Glide run for: ${file.path}")
            BinaryFeatureGate.runWith(project, dialog.sloGuardArgs.isNotEmpty()) {
                GopherGlideExecutor.execute(project, *args.toTypedArray())
            }
        }
    }
}
