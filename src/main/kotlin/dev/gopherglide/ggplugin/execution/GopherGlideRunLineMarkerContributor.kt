package dev.gopherglide.ggplugin.execution

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

class GopherGlideRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element !is YAMLKeyValue) return null

        val file = element.containingFile as? YAMLFile ?: return null
        val virtualFile = file.virtualFile ?: return null
        if (!file.name.endsWith(".gg.yaml")) return null

        val parentMapping = element.parent as? org.jetbrains.yaml.psi.YAMLMapping ?: return null
        val isRoot = parentMapping.parent is org.jetbrains.yaml.psi.YAMLDocument
        if (!isRoot) return null

        if (parentMapping.keyValues.firstOrNull() != element) return null

        val action = object : AnAction("Run Load Test", "Execute the Gopher-Glide load test", AllIcons.Actions.Execute) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                RunGopherGlideAction.executeTest(project, virtualFile)
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
            arrayOf(action),
            { "Run Load Test" }
        )
    }
}
