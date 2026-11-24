package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import dev.gopherglide.ggplugin.execution.autopilot.SloGuardPanel
import javax.swing.JComponent

/**
 * Standalone dialog for config-driven runs ("Run GG (Config)" and its in-terminal sibling): the shared
 * [SnapOptionsPanel] and an optional SLO guard, since everything else comes from the `.gg.yaml` itself.
 */
class SnapOptionsDialog(project: Project) : DialogWrapper(project) {
    private val snapOptions = SnapOptionsPanel()
    private val sloGuard = SloGuardPanel()

    val snapEnabled: Boolean get() = snapOptions.snapEnabled
    val snapTag: String? get() = snapOptions.snapTag

    /** The `--autopilot slo-guard` flags when the guard is on, else empty. */
    val sloGuardArgs: List<String> get() = sloGuard.args

    init {
        title = "Run Gopher-Glide"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        snapOptions.addTo(builder)
        builder.addVerticalGap(8)
        sloGuard.addTo(builder)
        return builder.panel
    }

    override fun doValidate(): ValidationInfo? = sloGuard.validate()
}
