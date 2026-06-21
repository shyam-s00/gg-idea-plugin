package dev.gopherglide.ggplugin.ci

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class GenerateCiWorkflowAction : AnAction(
    "Generate CI Workflow...",
    "Scaffold a .github/workflows/gg.yml that runs gg snap + assert and posts the result as a PR comment",
    AllIcons.Vcs.Vendors.Github
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        val configPath = findConfigPath(projectRoot) ?: "path/to/your-config.gg.yaml"

        WriteCommandAction.runWriteCommandAction(project) {
            val githubDir = projectRoot.findChild(".github") ?: projectRoot.createChildDirectory(this, ".github")
            val workflowsDir = githubDir.findChild("workflows") ?: githubDir.createChildDirectory(this, "workflows")

            val existing = workflowsDir.findChild("gg.yml")
            if (existing != null) {
                val overwrite = Messages.showYesNoDialog(
                    project,
                    "A .github/workflows/gg.yml already exists. Overwrite it?",
                    "Generate CI Workflow",
                    Messages.getQuestionIcon()
                )
                if (overwrite != Messages.YES) return@runWriteCommandAction
            }

            val file = existing ?: workflowsDir.createChildData(this, "gg.yml")
            file.setBinaryContent(CiWorkflowTemplate.render(configPath).toByteArray(Charsets.UTF_8))

            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(project).openFile(file, true)
            }
        }
    }

    /** Finds the first `*.gg.yaml` config under the project root, skipping VCS/build directories. */
    private fun findConfigPath(projectRoot: VirtualFile): String? {
        var found: VirtualFile? = null
        VfsUtilCore.visitChildrenRecursively(projectRoot, object : com.intellij.openapi.vfs.VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    return file.name !in SKIPPED_DIRS
                }
                if (found == null && file.name.endsWith(".gg.yaml")) {
                    found = file
                }
                return found == null
            }
        })
        return found?.let { VfsUtilCore.getRelativePath(it, projectRoot) }
    }

    companion object {
        private val SKIPPED_DIRS = setOf(".git", ".idea", ".gradle", "build", "out", "node_modules")
    }
}
