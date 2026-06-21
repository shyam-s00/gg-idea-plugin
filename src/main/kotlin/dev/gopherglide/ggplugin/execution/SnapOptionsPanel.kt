package dev.gopherglide.ggplugin.execution

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * Snap checkbox + Tag field, shared by the profile flow's [TrafficPopup] and the config flow's
 * standalone [SnapOptionsDialog] — the Tag field is only enabled while the checkbox is checked.
 */
class SnapOptionsPanel {
    private val snapCheckBox = JBCheckBox("Record snapshot (--snap)")
    private val tagField = JBTextField()

    val snapEnabled: Boolean
        get() = snapCheckBox.isSelected

    val snapTag: String?
        get() = tagField.text.trim().takeIf { it.isNotBlank() }

    init {
        tagField.isEnabled = false
        snapCheckBox.addActionListener { tagField.isEnabled = snapCheckBox.isSelected }
    }

    fun addTo(formBuilder: FormBuilder): FormBuilder =
        formBuilder
            .addComponent(snapCheckBox)
            .addLabeledComponent("Snapshot tag (--snap-tag):", tagField)

    val component: JComponent
        get() = FormBuilder.createFormBuilder().also { addTo(it) }.panel
}
