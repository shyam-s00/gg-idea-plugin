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

        val hasSiblingYaml = virtualFile.parent?.children?.any { it.name.endsWith(".gg.yaml") } == true

        val action = object : AnAction("Run GG", "Pick one of gg's built-in load profiles and run it against this HTTP file", AllIcons.Actions.Execute) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunGopherGlideHttpAction.showProfilePicker(project, virtualFile, e.dataContext)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = true
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val configAction = object : AnAction(
            "Run GG (Config)",
            "Run this HTTP file's sibling .gg.yaml config directly — no profile or overrides",
            AllIcons.Actions.Execute
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val yamlFile = virtualFile.parent?.children?.firstOrNull { it.name.endsWith(".gg.yaml") } ?: return
                RunGopherGlideConfigHttpAction.runConfig(project, yamlFile)
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabledAndVisible = hasSiblingYaml
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
                GenerateConfigYaml.generate(project, virtualFile)
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
            arrayOf(action, configAction, generateConfigAction),
            { "Run Gopher-Glide " }
        )
    }
}
