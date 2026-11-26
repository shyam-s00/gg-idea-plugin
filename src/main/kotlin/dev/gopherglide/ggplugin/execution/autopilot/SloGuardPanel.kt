package dev.gopherglide.ggplugin.execution.autopilot

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.gopherglide.ggplugin.services.BinaryFeatureGate
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import javax.swing.JComponent

/**
 * "Protect run with Autopilot SLO Guard" checkbox with its two limits, shared by the profile and config
 * launch dialogs. The limits only apply while the box is checked.
 */
class SloGuardPanel {
    private val checkBox = JBCheckBox("🛡️ Protect run with Autopilot SLO Guard")
    private val p99Field = JBTextField(DEFAULT_P99)
    private val errorField = JBTextField(DEFAULT_ERROR_PERCENT)

    val enabled: Boolean get() = checkBox.isSelected

    /** The parsed limits, or null while the box is unchecked or a field is invalid. */
    val limits: SloLimits?
        get() {
            if (!enabled) return null
            val p99 = AutopilotArgs.parseLatencyMillis(p99Field.text) ?: return null
            val error = AutopilotArgs.parseErrorFraction(errorField.text) ?: return null
            return SloLimits(p99, error)
        }

    /** The flags for the chosen guard, or empty when the box is unchecked. */
    val args: List<String> get() = limits?.let(AutopilotArgs::sloGuard).orEmpty()

    init {
        setFieldsEnabled(false)
        checkBox.addActionListener { setFieldsEnabled(checkBox.isSelected) }
        if (BinaryFeatureGate.knownTooOld()) {
            checkBox.isEnabled = false
            checkBox.toolTipText = "Needs gg ${GopherGlideVersion.MIN_AUTOPILOT_AND_MCP} or newer"
        }
    }

    fun validate(): ValidationInfo? {
        if (!enabled) return null
        val problem = AutopilotArgs.checkLimits(p99Field.text, errorField.text) ?: return null
        return ValidationInfo(problem.message, if (problem.field == AutopilotArgs.Field.P99) p99Field else errorField)
    }

    fun addTo(formBuilder: FormBuilder): FormBuilder =
        formBuilder
            .addComponent(checkBox)
            .addLabeledComponent("Max P99 (--max-p99):", p99Field)
            .addLabeledComponent("Max errors % (--max-error-rate):", errorField)

    val component: JComponent
        get() = FormBuilder.createFormBuilder().also { addTo(it) }.panel

    private fun setFieldsEnabled(on: Boolean) {
        p99Field.isEnabled = on
        errorField.isEnabled = on
    }

    companion object {
        const val DEFAULT_P99 = "250ms"
        const val DEFAULT_ERROR_PERCENT = "1.0"
    }
}
