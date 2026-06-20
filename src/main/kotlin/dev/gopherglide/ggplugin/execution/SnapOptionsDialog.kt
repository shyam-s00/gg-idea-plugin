package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

/**
 * Standalone dialog for config-driven runs ("Run GG (Config)" and its in-terminal sibling) — just
 * the shared [SnapOptionsPanel], since everything else about the run comes from the `.gg.yaml` itself.
 */
class SnapOptionsDialog(project: Project) : DialogWrapper(project) {
    private val snapOptions = SnapOptionsPanel()

    val snapEnabled: Boolean get() = snapOptions.snapEnabled
    val snapTag: String? get() = snapOptions.snapTag

    init {
        title = "Run Gopher-Glide"
        init()
    }

    override fun createCenterPanel(): JComponent = snapOptions.component
}
