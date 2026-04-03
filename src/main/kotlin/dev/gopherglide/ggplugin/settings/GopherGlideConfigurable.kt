package dev.gopherglide.ggplugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.gopherglide.ggplugin.services.BinaryManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

class GopherGlideConfigurable : Configurable {
    private var component: JComponent? = null
    private var binaryPathField: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "Gopher Glide"

    override fun createComponent(): JComponent {
        val bField = TextFieldWithBrowseButton()
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle("Select Gopher Glide Binary")
            .withDescription("Select the gopher-glide executable file.")

        bField.addBrowseFolderListener(TextBrowseFolderListener(descriptor))
        binaryPathField = bField

        var statusLabel: JLabel? = null
        var versionLabel: JLabel? = null
        var checkButton: JButton? = null
        val manager = BinaryManager.instance

        component = panel {
            row("Custom path:") {
                cell(bField).align(AlignX.FILL)
            }
            row {
                label("Leave empty to use system PATH or auto-download.")
            }

            row {
                versionLabel = label("Checking version...").component
            }

            row {
                button("Check for Updates") {
                    checkButton?.isEnabled = false
                    statusLabel?.text = "Checking…"

                    // Fetch local and remote versions in parallel
                    val localFuture  = manager.getLocalVersion()
                    val remoteFuture = manager.getLatestRemoteVersion()

                    CompletableFuture.allOf(localFuture, remoteFuture).whenComplete { _, _ ->
                        val localVerRaw  = localFuture.getNow(null)
                        val remoteVerRaw = remoteFuture.getNow(null)

                        val localClean  = extractVersionNumber(localVerRaw?.split("\u0001")?.getOrNull(0))
                        val remoteClean = extractVersionNumber(remoteVerRaw)

                        SwingUtilities.invokeLater {
                            when {
                                remoteClean == null -> {
                                    statusLabel?.text = "⚠ Could not reach the update server. Check your connection."
                                    checkButton?.isEnabled = true
                                }

                                localClean == remoteClean -> {
                                    statusLabel?.text = "✓ Already on the latest version (v$remoteClean)."
                                    checkButton?.isEnabled = true
                                }

                                else -> {
                                    // Either an update is available, or the binary is not installed yet
                                    val fromLabel = if (localClean != null) "v$localClean → " else ""
                                    statusLabel?.text = "Downloading ${fromLabel}v$remoteClean…"

                                    binaryPathField?.text = ""
                                    GopherGlideSettings.instance.customBinaryPath = ""

                                    manager.deleteDownloadedBinary()
                                    manager.downloadLatestRelease().whenComplete { _, error ->
                                        SwingUtilities.invokeLater {
                                            if (error != null) {
                                                statusLabel?.text = "✗ Update failed: ${error.message}"
                                            } else {
                                                statusLabel?.text = "✓ Updated to v$remoteClean."
                                                updateVersionInfo(versionLabel)
                                            }
                                            checkButton?.isEnabled = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.also { checkButton = it.component }

                statusLabel = label("").component
            }

            row {
                button("Copy Diagnostics to Clipboard") {
                    manager.collectDiagnostics().whenComplete { text, error ->
                        SwingUtilities.invokeLater {
                            if (error != null || text == null) {
                                Messages.showErrorDialog(
                                    "Failed to collect diagnostics: ${error?.message}",
                                    "Diagnostics Error"
                                )
                                return@invokeLater
                            }
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(text), null)
                            Messages.showInfoMessage(
                                component,
                                "Diagnostics copied to clipboard.\n\nPaste into a bug report or the IDE log to help diagnose version-update issues.",
                                "Diagnostics Copied"
                            )
                        }
                    }
                }
                label("Copies OS, binary paths, and versions — useful for bug reports.")
            }
        }

        updateVersionInfo(versionLabel)

        return component!!
    }

    /**
     * Extracts a clean semantic version (e.g. "0.4.1") from a raw string.
     * Returns null for blank input or when no version pattern is found.
     */
    private fun extractVersionNumber(versionString: String?): String? {
        if (versionString.isNullOrBlank()) return null
        val matcher = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+)").matcher(versionString)
        if (matcher.find()) return matcher.group(1).trim()
        // Fallback: strip common prefixes
        val fallback = versionString.removePrefix("v").removePrefix("gg version").trim()
        return fallback.ifBlank { null }
    }

    private fun updateVersionInfo(versionLabel: JLabel?) {
        val manager = BinaryManager.instance

        manager.getLocalVersion().whenComplete { localVerRaw, _ ->
            manager.getLatestRemoteVersion().whenComplete { remoteVerRaw, _ ->
                SwingUtilities.invokeLater {
                    // localVerRaw format: rawOutput\u0001binaryPath\u0001[plugin|system]
                    val parts           = localVerRaw?.split("\u0001")
                    val localVersionOutput = parts?.getOrNull(0)
                    val localBinaryPath    = parts?.getOrNull(1)
                    val sourceTag          = parts?.getOrNull(2)

                    val localSource = when (sourceTag) {
                        "[plugin]" -> "Plugin Managed"
                        "[system]" -> "System PATH"
                        else       -> null
                    }

                    val localClean  = extractVersionNumber(localVersionOutput)
                    val remoteClean = extractVersionNumber(remoteVerRaw)

                    val localText  = localClean  ?: "Not found"
                    val remoteText = remoteClean ?: "Unknown"

                    val sb = StringBuilder("<html>")
                    sb.append("<b>Local:</b> $localText")
                    if (localSource != null) sb.append(" <i>($localSource)</i>")
                    sb.append(" &nbsp;&nbsp; <b>Latest:</b> $remoteText")

                    // Show the exact binary path — the single most useful fact when
                    // troubleshooting "why is the version not updating?"
                    if (!localBinaryPath.isNullOrBlank()) {
                        sb.append("<br><small><b>Binary:</b> $localBinaryPath</small>")
                    }

                    when {
                        localClean == null ->
                            sb.append("<br><font color='orange'>Binary not found. Click 'Check for Updates' to install it.</font>")
                        remoteClean != null && localClean != remoteClean ->
                            sb.append("<br><font color='red'><b>Update available!</b> Click 'Check for Updates' to upgrade.</font>")
                        localClean == remoteClean ->
                            sb.append("<br><font color='green'>✓ You are up to date.</font>")
                    }

                    sb.append("</html>")
                    versionLabel?.text = sb.toString()
                }
            }
        }
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
