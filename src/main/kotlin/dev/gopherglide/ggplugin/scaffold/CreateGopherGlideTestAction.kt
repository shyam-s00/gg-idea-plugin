package dev.gopherglide.ggplugin.scaffold

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.gopherglide.ggplugin.execution.ProfileCatalog
import dev.gopherglide.ggplugin.execution.ProfileCategory

class CreateGopherGlideTestAction : AnAction(
    "Gopher-Glide Test",
    "Create a new .http file pre-filled with a sample request and the gg profile cheat sheet",
    AllIcons.Actions.AddFile
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveTargetDirectory(e, project) ?: return

        val fileName = Messages.showInputDialog(
            project,
            "File name:",
            "New Gopher-Glide Test",
            null,
            DEFAULT_FILE_NAME,
            null
        ) ?: return
        val normalizedName = if (fileName.endsWith(".http")) fileName else "$fileName.http"

        WriteCommandAction.runWriteCommandAction(project) {
            val file = directory.findChild(normalizedName) ?: directory.createChildData(this, normalizedName)
            file.setBinaryContent(TEMPLATE_CONTENT.toByteArray(Charsets.UTF_8))

            ApplicationManager.getApplication().invokeLater {
                val editor = FileEditorManager.getInstance(project)
                    .openTextEditor(OpenFileDescriptor(project, file), true)
                selectUrlPlaceholder(editor)
            }
        }
    }

    private fun resolveTargetDirectory(e: AnActionEvent, project: Project): VirtualFile? {
        e.getData(LangDataKeys.IDE_VIEW)?.getOrChooseDirectory()?.virtualFile?.let { return it }
        e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { return if (it.isDirectory) it else it.parent }
        return project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    }

    private fun selectUrlPlaceholder(editor: Editor?) {
        editor ?: return
        val start = editor.document.text.indexOf(URL_PLACEHOLDER)
        if (start < 0) return
        editor.caretModel.moveToOffset(start)
        editor.selectionModel.setSelection(start, start + URL_PLACEHOLDER.length)
    }

    companion object {
        private const val DEFAULT_FILE_NAME = "sample.http"
        private const val URL_PLACEHOLDER = "https://example.com/"

        private val TEMPLATE_CONTENT: String = buildString {
            appendLine("### Gopher-Glide Test")
            appendLine("#")
            appendLine("# Built-in gg profiles you can run with \"Run with Profile...\":")
            for (category in ProfileCategory.entries) {
                val names = ProfileCatalog.byCategory[category]?.joinToString(", ") { it.name } ?: continue
                appendLine("#   ${category.displayName}: $names")
            }
            appendLine("#")
            appendLine("# Replace the URL below, then use the gutter icon to run.")
            appendLine()
            appendLine("GET $URL_PLACEHOLDER")
            appendLine("Accept: application/json")
        }
    }
}
