package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RunGopherGlideHttpAction : AnAction("Run Gopher-Glide", "Execute or create load test for this HTTP file", com.intellij.icons.AllIcons.Actions.Execute) {
    
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
        fun executeTest(project: Project, httpFile: VirtualFile) {
            val parentDir = httpFile.parent ?: return
            val existingYaml = parentDir.children.firstOrNull { it.name.endsWith(".gg.yaml") }

            if (existingYaml != null) {
                TerminalExecutor.execute(project, existingYaml.path)
            } else {
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        val newYaml = parentDir.createChildData(this, "load-test.gg.yaml")
                        val content = """
                            config:
                              httpFile: "${httpFile.name}"
                              prometheus: false
                              breaker_threshold_pct: 20.0
                              jitter: 0.1
                              time_scale: 1.0
                            
                            stages:
                              - duration: 10s
                                target_rps: 50
                        """.trimIndent()
                        newYaml.setBinaryContent(content.toByteArray(Charsets.UTF_8))

                        ApplicationManager.getApplication().invokeLater {
                            FileEditorManager.getInstance(project).openFile(newYaml, true)
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }
}
