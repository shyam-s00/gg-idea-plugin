package io.gopherglide.intellij.ggideaplugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class GopherGlideConfigurable : Configurable {
    private var component: JComponent? = null
    private var binaryPathField: TextFieldWithBrowseButton? = null
    private var releaseUrlField: JBTextField? = null

    override fun getDisplayName(): String = "Gopher Glide"

    override fun createComponent(): JComponent {
        val bField = TextFieldWithBrowseButton()
        bField.addBrowseFolderListener(
            "Select Gopher Glide Binary",
            "Select the gopher-glide executable file.",
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        binaryPathField = bField
        
        val rField = JBTextField()
        releaseUrlField = rField

        component = panel {
            row("Custom binary path:") {
                cell(bField).align(AlignX.FILL)
            }
            row {
                label("Leave empty to use system PATH or auto-download.")
            }
            row("Release URL:") {
                cell(rField).align(AlignX.FILL)
            }
            row {
                label("URL directory where OS/arch specific binaries are located.")
            }
        }
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = GopherGlideSettings.instance
        return binaryPathField?.text != settings.customBinaryPath ||
               releaseUrlField?.text != settings.releaseUrl
    }

    override fun apply() {
        val settings = GopherGlideSettings.instance
        settings.customBinaryPath = binaryPathField?.text ?: ""
        settings.releaseUrl = releaseUrlField?.text ?: "https://github.com/shyam/gopher-glide/releases/latest/download"
    }

    override fun reset() {
        val settings = GopherGlideSettings.instance
        binaryPathField?.text = settings.customBinaryPath
        releaseUrlField?.text = settings.releaseUrl
    }

    override fun disposeUIResources() {
        component = null
        binaryPathField = null
        releaseUrlField = null
    }
}
