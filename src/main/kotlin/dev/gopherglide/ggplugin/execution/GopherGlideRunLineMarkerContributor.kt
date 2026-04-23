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

        val action = object : AnAction("Run GG", "Execute the Gopher-Glide", AllIcons.Actions.Execute) {
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

        val snapAction = object : AnAction("Run && Record Snapshot...", "Execute GG and record a snapshot", AllIcons.Actions.Dump) {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val tag = com.intellij.openapi.ui.Messages.showInputDialog(
                    project,
                    "Enter Snapshot Tag (leave blank for default):",
                    "Record Snapshot",
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
                )
                if (tag != null) {
                    RunAndRecordSnapAction.executeTest(project, virtualFile, tag)
                }
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
            arrayOf(action, snapAction),
            { "Run GG" }
        )
    }
}
