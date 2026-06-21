package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * Single dialog for the profile-driven "Run GG" flow: lets the user override a profile's
 * `--peak-rps`/`--duration` (pre-filled with the profile's own defaults — leaving a field
 * unchanged means no override flag is added), and optionally record a snapshot (shared
 * [SnapOptionsPanel]).
 */
class TrafficPopup(project: Project, private val profile: GgProfile) : DialogWrapper(project) {
    private val peakRpsField = JBTextField(profile.defaultPeakRps.toString())
    private val durationField = JBTextField(profile.defaultDuration)
    private val snapOptions = SnapOptionsPanel()

    val peakRpsOverride: Int?
        get() = peakRpsField.text.trim()
            .takeIf { it.isNotBlank() && it != profile.defaultPeakRps.toString() }
            ?.toIntOrNull()

    val durationOverride: String?
        get() = durationField.text.trim()
            .takeIf { it.isNotBlank() && it != profile.defaultDuration }

    val snapEnabled: Boolean get() = snapOptions.snapEnabled
    val snapTag: String? get() = snapOptions.snapTag

    init {
        title = "Run \"${profile.name}\" Profile"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val descriptionLabel = JBLabel("<html><div width='320'>${profile.description}</div></html>")
        descriptionLabel.foreground = JBColor.GRAY

        val formBuilder = FormBuilder.createFormBuilder()
            .addComponent(descriptionLabel)
            .addVerticalGap(8)
            .addLabeledComponent("Peak RPS (--peak-rps):", peakRpsField)
            .addLabeledComponent("Duration (--duration):", durationField)
            .addVerticalGap(8)

        snapOptions.addTo(formBuilder)

        return formBuilder.panel
    }
}
