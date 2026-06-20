package dev.gopherglide.ggplugin.execution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Scaffolds a `.gg.yaml` config for an `.http` file and opens it for editing without running
 * anything — the explicit secondary action backing "Generate config.yaml...".
 */
object GenerateConfigYaml {
    fun generate(project: Project, httpFile: VirtualFile) {
        val parentDir = httpFile.parent ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val newYaml = parentDir.createChildData(this, "traffic-sim.gg.yaml")
                val content = """
                    config:
                      httpFile: "${httpFile.name}"
                      prometheus: false
                      breaker_threshold_pct: 20.0
                      jitter: 0.1
                      time_scale: 1.0

                      # Optional overrides — omit to use app defaults (sample_rate: 0.05, max_samples: 200, max_body_kb: 0)
                      # snap:
                      #   sample_rate: 0.05
                      #   max_samples: 200
                      #   max_body_kb: 0

                    stages:
                      - name: "Ramp-up"
                        duration: 10s
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

/**
 * Registered counterpart to the gutter's "Generate config.yaml..." entry, so it's also reachable
 * from the right-click "Gopher Glide (GG)" context menu — the gutter and context menu are separate
 * action surfaces and don't share entries automatically.
 */
class GenerateConfigYamlHttpAction : AnAction(
    "Generate config.yaml...",
    "Scaffold a .gg.yaml config for this HTTP file without running anything",
    AllIcons.FileTypes.Yaml
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.name.endsWith(".http")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val httpFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        GenerateConfigYaml.generate(project, httpFile)
    }
}
