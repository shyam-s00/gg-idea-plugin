package dev.gopherglide.ggplugin.execution.autopilot

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.Component
import javax.swing.JComponent

/** Asks for the limits of an SLO Guard started while a run is going. */
class SloGuardDialog(parent: Component) : DialogWrapper(parent, true) {
    private val p99Field = JBTextField(SloGuardPanel.DEFAULT_P99)
    private val errorField = JBTextField(SloGuardPanel.DEFAULT_ERROR_PERCENT)

    var limits: SloLimits? = null
        private set

    init {
        title = "Guard SLO"
        setOKButtonText("Start Guard")
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Clamps the rate when health breaches the limits, then releases it in steps."))
            .addVerticalGap(8)
            .addLabeledComponent("Max P99:", p99Field)
            .addLabeledComponent("Max errors %:", errorField)
            .panel

    override fun doValidate(): ValidationInfo? {
        val problem = AutopilotArgs.checkLimits(p99Field.text, errorField.text) ?: return null
        return ValidationInfo(problem.message, if (problem.field == AutopilotArgs.Field.P99) p99Field else errorField)
    }

    override fun doOKAction() {
        val p99 = AutopilotArgs.parseLatencyMillis(p99Field.text) ?: return
        val error = AutopilotArgs.parseErrorFraction(errorField.text) ?: return
        limits = SloLimits(p99, error)
        super.doOKAction()
    }
}
