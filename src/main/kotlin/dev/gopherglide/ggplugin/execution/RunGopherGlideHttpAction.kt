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

class RunGopherGlideHttpAction : AnAction("Run Gopher-Glide", "Execute a zero-config profile run for this HTTP file", com.intellij.icons.AllIcons.Actions.Execute) {

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
        /**
         * Builds the args for a run: the existing `.gg.yaml` if present, otherwise a zero-config
         * `--profile <default> --http-file <file>` run — no config.yaml required.
         */
        private fun buildRunArgs(httpFile: VirtualFile, existingYaml: VirtualFile?): Array<String> =
            if (existingYaml != null) {
                arrayOf(existingYaml.path)
            } else {
                arrayOf("--profile", ProfileCatalog.DEFAULT_ZERO_CONFIG_PROFILE, "--http-file", httpFile.path)
            }

        fun executeTest(project: Project, httpFile: VirtualFile, runInTerminal: Boolean = false) {
            val parentDir = httpFile.parent ?: return
            val existingYaml = parentDir.children.firstOrNull { it.name.endsWith(".gg.yaml") }
            val args = buildRunArgs(httpFile, existingYaml)

            if (runInTerminal) {
                TerminalExecutor.execute(project, *args)
            } else {
                GopherGlideExecutor.execute(project, *args)
            }
        }

        /**
         * Explicit opt-in to the interactive TUI in a terminal.
         * TODO: pass a capped-fps flag to gg here once it exists, so this path no longer risks the CPU/crash regression.
         */
        fun executeTestInteractive(project: Project, httpFile: VirtualFile) =
            executeTest(project, httpFile, runInTerminal = true)

        /**
         * Explicit secondary action (used to be the silent fallback on the default Run click):
         * scaffolds a `.gg.yaml` config for this HTTP file and opens it for editing without running anything.
         */
        fun generateConfigYaml(project: Project, httpFile: VirtualFile) {
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
}
