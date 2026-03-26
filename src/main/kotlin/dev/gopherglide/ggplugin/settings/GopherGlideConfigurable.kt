package dev.gopherglide.ggplugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class GopherGlideConfigurable : Configurable {
    private var component: JComponent? = null
    private var binaryPathField: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "Gopher Glide"

    override fun createComponent(): JComponent {
        val bField = TextFieldWithBrowseButton()
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle("Select Gopher Glide Binary")
            .withDescription("Select the gopher-glide executable file.")

        bField.addBrowseFolderListener(null, descriptor)
        binaryPathField = bField

        component = panel {
            row("Custom binary path:") {
                cell(bField).align(AlignX.FILL)
            }
            row {
                label("Leave empty to use system PATH or auto-download.")
            }
        }
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = GopherGlideSettings.instance
        return binaryPathField?.text != settings.customBinaryPath
    }

    override fun apply() {
        val settings = GopherGlideSettings.instance
        settings.customBinaryPath = binaryPathField?.text ?: ""
    }

    override fun reset() {
        val settings = GopherGlideSettings.instance
        binaryPathField?.text = settings.customBinaryPath
    }

    override fun disposeUIResources() {
        component = null
        binaryPathField = null
    }
}
