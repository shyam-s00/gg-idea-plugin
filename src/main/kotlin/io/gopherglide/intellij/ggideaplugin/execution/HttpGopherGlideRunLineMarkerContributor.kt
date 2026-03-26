package io.gopherglide.intellij.ggideaplugin.execution

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

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            arrayOf(action),
            { "Run Gopher-Glide " }
        )
    }
}
