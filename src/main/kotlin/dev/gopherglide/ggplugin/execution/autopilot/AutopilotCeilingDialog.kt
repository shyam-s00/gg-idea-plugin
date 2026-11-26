package dev.gopherglide.ggplugin.execution.autopilot

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.gopherglide.ggplugin.execution.SnapOptionsPanel
import javax.swing.JComponent

/** Settings for a find-ceiling run: gg steps the rate up until health breaches, then confirms the rate it backed off to. */
class AutopilotCeilingDialog(project: Project) : DialogWrapper(project) {
    private val startField = JBTextField("20")
    private val maxField = JBTextField("1000")
    private val p99Field = JBTextField(SloGuardPanel.DEFAULT_P99)
    private val errorField = JBTextField(SloGuardPanel.DEFAULT_ERROR_PERCENT)
    private val durationField = JBTextField("5m")
    private val snapOptions = SnapOptionsPanel()

    val snapEnabled: Boolean get() = snapOptions.snapEnabled
    val snapTag: String? get() = snapOptions.snapTag
    val snapArgs: List<String> get() = snapOptions.args

    init {
        title = "Autopilot: Find Ceiling"
        setOKButtonText("Run Find Ceiling")
        init()
    }

    /** The autopilot flags for the entered values; call after the dialog was accepted. */
    fun autopilotArgs(): List<String> {
        val limits = SloLimits(
            AutopilotArgs.parseLatencyMillis(p99Field.text)!!,
            AutopilotArgs.parseErrorFraction(errorField.text)!!
        )
        return AutopilotArgs.findCeiling(startField.text.trim().toInt(), maxField.text.trim().toInt(), limits, durationField.text)
    }

    override fun createCenterPanel(): JComponent {
        val note = JBLabel(
            "<html><div width='340'>Steps the rate up while health holds, marks the breach, confirms the rate " +
                "it backs off to, then stops. No LLM involved.</div></html>"
        ).apply { foreground = JBColor.GRAY }
        val builder = FormBuilder.createFormBuilder()
            .addComponent(note)
            .addVerticalGap(8)
            .addLabeledComponent("Start RPS (--start-rps):", startField)
            .addLabeledComponent("Max RPS (--max-rps):", maxField)
            .addLabeledComponent("Max P99 (--max-p99):", p99Field)
            .addLabeledComponent("Max errors % (--max-error-rate):", errorField)
            .addLabeledComponent("Max duration (--duration):", durationField)
            .addVerticalGap(8)
        snapOptions.addTo(builder)
        return builder.panel
    }

    override fun doValidate(): ValidationInfo? {
        val problem = AutopilotArgs.checkFindCeiling(
            startField.text, maxField.text, p99Field.text, errorField.text, durationField.text
        ) ?: return null
        val field = when (problem.field) {
            AutopilotArgs.Field.START_RPS -> startField
            AutopilotArgs.Field.MAX_RPS -> maxField
            AutopilotArgs.Field.P99 -> p99Field
            AutopilotArgs.Field.ERROR_RATE -> errorField
            AutopilotArgs.Field.DURATION -> durationField
        }
        return ValidationInfo(problem.message, field)
    }
}
