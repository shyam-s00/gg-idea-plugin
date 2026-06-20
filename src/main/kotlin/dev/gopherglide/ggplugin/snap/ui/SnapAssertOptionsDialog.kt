package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class SnapAssertOptionsDialog(project: Project) : DialogWrapper(project) {
    private val latencyRegressionField = JBTextField("20")
    private val errorRateDeltaField = JBTextField("5")
    private val payloadSizeDeltaField = JBTextField("50")
    private val denyRemovedFieldsCheckBox = JBCheckBox("Treat removed schema fields as a regression")
    private val failOnWarnCheckBox = JBCheckBox("Fail on warnings too (not just regressions)")

    val latencyRegressionPct: Double get() = latencyRegressionField.text.trim().toDoubleOrNull() ?: 20.0
    val errorRateDeltaPct: Double get() = errorRateDeltaField.text.trim().toDoubleOrNull() ?: 5.0
    val payloadSizeDeltaPct: Double get() = payloadSizeDeltaField.text.trim().toDoubleOrNull() ?: 50.0
    val denyRemovedFields: Boolean get() = denyRemovedFieldsCheckBox.isSelected
    val failOnWarn: Boolean get() = failOnWarnCheckBox.isSelected

    init {
        title = "Assert Thresholds"
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("P99 latency regression (%):", latencyRegressionField)
            .addLabeledComponent("Error rate increase (percentage points):", errorRateDeltaField)
            .addLabeledComponent("Avg payload size increase (%, warn):", payloadSizeDeltaField)
            .addVerticalGap(8)
            .addComponent(denyRemovedFieldsCheckBox)
            .addComponent(failOnWarnCheckBox)
            .panel
}
