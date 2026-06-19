package dev.gopherglide.ggplugin.execution

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement

class HttpGopherGlideRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val file = element.containingFile ?: return null
        val virtualFile = file.virtualFile ?: return null
        if (!file.name.endsWith(".http")) return null

        // Only attach to leaf elements (the actual text tokens)
        if (element !is LeafPsiElement) return null

        // Attach the icon to HTTP methods so it shows up right next to the URL
        val text = element.text.uppercase()
        val httpMethods = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        if (text !in httpMethods) return null

        // Make sure it's at the beginning of a line (rough check to avoid matching random text)
        val prevSibling = element.prevSibling
        if (prevSibling != null && !prevSibling.text.contains("\n")) return null

        val action = object : AnAction("Run Gopher-Glide", "Execute GG binary for this HTTP file", AllIcons.RunConfigurations.TestState.Run) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunGopherGlideHttpAction.executeTest(project, virtualFile)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val snapAction = object : AnAction("Run && Record Snapshot...", "Execute GG and record a snapshot for this HTTP file", AllIcons.Actions.Dump) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunAndRecordSnapHttpAction.executeTest(project, virtualFile)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val profileAction = object : AnAction(
            "Run with Profile...",
            "Pick one of gg's built-in load profiles and run it against this HTTP file",
            AllIcons.Actions.ListFiles
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunWithProfileHttpAction.showProfilePicker(project, virtualFile, e.dataContext)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val interactiveAction = object : AnAction(
            "Run in Terminal (Interactive)",
            "Run with gg's interactive TUI in a terminal — enables live ↑/↓ RPS-bias control, costs more CPU than the default panel",
            AllIcons.Actions.MoveTo2
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunGopherGlideHttpAction.executeTestInteractive(project, virtualFile)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val generateConfigAction = object : AnAction(
            "Generate config.yaml...",
            "Scaffold a .gg.yaml config for this HTTP file without running anything",
            AllIcons.FileTypes.Yaml
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunGopherGlideHttpAction.generateConfigYaml(project, virtualFile)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            arrayOf(action, snapAction, profileAction, interactiveAction, generateConfigAction),
            { "Run Gopher-Glide " }
        )
    }
}
