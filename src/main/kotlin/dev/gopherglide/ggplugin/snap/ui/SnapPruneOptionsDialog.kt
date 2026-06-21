package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class SnapPruneOptionsDialog(project: Project, preselectedIds: List<String> = emptyList()) : DialogWrapper(project) {
    private val idsField = JBTextField(preselectedIds.joinToString(","))
    private val keepLastField = JBTextField()
    private val olderThanField = JBTextField()
    private val tagField = JBTextField()
    private val dryRunCheckBox = JBCheckBox("Preview only (dry run) — don't delete anything", true)

    val ids: String get() = idsField.text.trim()
    val keepLast: Int get() = keepLastField.text.trim().toIntOrNull() ?: 0
    val olderThan: String get() = olderThanField.text.trim()
    val tag: String get() = tagField.text.trim()
    val dryRun: Boolean get() = dryRunCheckBox.isSelected

    init {
        title = "Prune Snapshots"
        init()
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Delete by ID(s) (comma-separated):", idsField)
            .addLabeledComponent("Keep last N snapshots:", keepLastField)
            .addLabeledComponent("Delete older than (e.g. 30d, 720h):", olderThanField)
            .addLabeledComponent("Delete by tag:", tagField)
            .addVerticalGap(8)
            .addComponent(dryRunCheckBox)
            .panel

    override fun doValidate(): ValidationInfo? {
        if (ids.isBlank() && keepLast <= 0 && olderThan.isBlank() && tag.isBlank()) {
            return ValidationInfo("Set at least one filter: ID(s), keep-last, older-than, or tag.", idsField)
        }
        return null
    }
}
