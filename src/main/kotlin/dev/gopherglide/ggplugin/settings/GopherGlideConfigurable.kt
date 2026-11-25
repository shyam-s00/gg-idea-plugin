package dev.gopherglide.ggplugin.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.openapi.project.ProjectManager
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.BinaryStatus
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
    private var snapshotsDirField: TextFieldWithBrowseButton? = null
    private var heartbeatIntervalSpinner: JBIntSpinner? = null
    private var updateCheckBox: JBCheckBox? = null
    private var routeBox: JBCheckBox? = null
    private var startDaemonBox: JBCheckBox? = null
    private var maxRpsSpinner: JBIntSpinner? = null
    private var maxDurationSpinner: JBIntSpinner? = null
    private var maxSessionsSpinner: JBIntSpinner? = null
    private var allowHostsField: JBTextField? = null

    override fun getDisplayName(): String = "Gopher Glide"

    override fun createComponent(): JComponent {
        val bField = TextFieldWithBrowseButton()
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withTitle("Select Gopher Glide Binary")
            .withDescription("Select the gopher-glide executable file.")
        bField.addBrowseFolderListener(TextBrowseFolderListener(descriptor))
        binaryPathField = bField

        val sField = TextFieldWithBrowseButton()
        val sDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Custom Snapshots Directory")
            .withDescription("Select the directory where Gopher-Glide snapshots are stored.")
        sField.addBrowseFolderListener(TextBrowseFolderListener(sDescriptor))
        snapshotsDirField = sField

        val intervalSpinner = JBIntSpinner(0, 0, 60)
        heartbeatIntervalSpinner = intervalSpinner

        val updateBox = JBCheckBox("Check for gg updates on startup")
        updateCheckBox = updateBox

        val routeCheck = JBCheckBox("Run my simulations through the shared daemon")
        val startCheck = JBCheckBox("Start the shared daemon when a project opens")
        val rpsSpinner = JBIntSpinner(1000, 1, 1_000_000)
        val durationSpinner = JBIntSpinner(10, 1, 24 * 60)
        val sessionsSpinner = JBIntSpinner(2, 1, 20)
        val hostsField = JBTextField()
        routeBox = routeCheck
        startDaemonBox = startCheck
        maxRpsSpinner = rpsSpinner
        maxDurationSpinner = durationSpinner
        maxSessionsSpinner = sessionsSpinner
        allowHostsField = hostsField

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

            row("Custom snapshots dir:") {
                cell(sField).align(AlignX.FILL)
            }
            row {
                label("Leave empty to use OS default (~/.config/gg/snapshots, etc).")
            }

            row("Run panel refresh interval:") {
                cell(intervalSpinner)
                label("seconds")
            }
            row {
                label("0 = use gg's default (5s). Takes effect on the next run — gg reads this at startup, not live.")
            }

            row {
                cell(updateBox)
            }
            row {
                label("At most once a day. Shows a notification when a newer gg release is available.")
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
                                    statusLabel?.text = "⚠ ${manager.getLastRemoteVersionError() ?: "Could not reach the update server. Check your connection."}"
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
                                                updateVersionInfo(versionLabel, checkButton)
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

            group("Shared daemon (gg v1.5.0+)") {
                row {
                    label("One gg process per project that the IDE and AI agents share, so an agent's runs appear in the Run tab.")
                }
                row { cell(routeCheck) }
                row {
                    label("Off by default. Takes effect on the next run; if the daemon cannot start, the run goes ahead directly.")
                }
                row { cell(startCheck) }
                row("Agent limits:") {
                    label("max RPS")
                    cell(rpsSpinner)
                    label("max duration")
                    cell(durationSpinner)
                    label("min, runs at once")
                    cell(sessionsSpinner)
                }
                row("Hosts agents may target:") {
                    cell(hostsField).align(AlignX.FILL)
                }
                row {
                    label("Comma-separated. Empty uses the hosts in this project's .http files, or any host if one can't be read. Your own runs are never limited.")
                }
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

        updateVersionInfo(versionLabel, checkButton)

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

    private fun updateVersionInfo(versionLabel: JLabel?, checkButton: JButton?) {
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

                    checkButton?.text = if (localClean == null) "Install Gopher Glide" else "Check for Updates"

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
                            sb.append("<br><font color='orange'>Binary not found. Click 'Install Gopher Glide' to install it.</font>")
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
        return binaryPathField?.text != settings.customBinaryPath ||
               snapshotsDirField?.text != settings.customSnapshotsDir ||
               heartbeatIntervalSpinner?.number != settings.heartbeatIntervalSeconds ||
               updateCheckBox?.isSelected != settings.checkForUpdatesOnStartup ||
               routeBox?.isSelected != settings.routeRunsThroughDaemon ||
               startDaemonBox?.isSelected != settings.startDaemonWithProject ||
               maxRpsSpinner?.number != settings.daemonMaxRps ||
               maxDurationSpinner?.number != settings.daemonMaxDurationMinutes ||
               maxSessionsSpinner?.number != settings.daemonMaxAgentSessions ||
               allowHostsField?.text != settings.daemonAllowHosts
    }

    override fun apply() {
        val settings = GopherGlideSettings.instance
        settings.customBinaryPath = binaryPathField?.text ?: ""
        settings.customSnapshotsDir = snapshotsDirField?.text ?: ""
        settings.heartbeatIntervalSeconds = heartbeatIntervalSpinner?.number ?: 0
        settings.checkForUpdatesOnStartup = updateCheckBox?.isSelected ?: true
        settings.routeRunsThroughDaemon = routeBox?.isSelected ?: false
        settings.startDaemonWithProject = startDaemonBox?.isSelected ?: false
        settings.daemonMaxRps = maxRpsSpinner?.number ?: 1000
        settings.daemonMaxDurationMinutes = maxDurationSpinner?.number ?: 10
        settings.daemonMaxAgentSessions = maxSessionsSpinner?.number ?: 2
        settings.daemonAllowHosts = allowHostsField?.text.orEmpty()
        BinaryStatus.instance.notifyChanged()
        applyDaemonSettings(settings)
    }

    /** A running daemon restarts when its settings changed and it is idle; one is started where the user asked for it. */
    private fun applyDaemonSettings(settings: GopherGlideSettings) {
        ProjectManager.getInstance().openProjects.forEach { project ->
            val daemon = project.getServiceIfCreated(GgMcpDaemonService::class.java)
            when {
                daemon != null && daemon.state == GgMcpDaemonService.State.RUNNING -> daemon.ensureRunning()
                settings.startDaemonWithProject -> GgMcpDaemonService.getInstance(project).ensureRunning()
            }
        }
    }

    override fun reset() {
        val settings = GopherGlideSettings.instance
        binaryPathField?.text = settings.customBinaryPath
        snapshotsDirField?.text = settings.customSnapshotsDir
        heartbeatIntervalSpinner?.number = settings.heartbeatIntervalSeconds
        updateCheckBox?.isSelected = settings.checkForUpdatesOnStartup
        routeBox?.isSelected = settings.routeRunsThroughDaemon
        startDaemonBox?.isSelected = settings.startDaemonWithProject
        maxRpsSpinner?.number = settings.daemonMaxRps
        maxDurationSpinner?.number = settings.daemonMaxDurationMinutes
        maxSessionsSpinner?.number = settings.daemonMaxAgentSessions
        allowHostsField?.text = settings.daemonAllowHosts
    }

    override fun disposeUIResources() {
        component = null
        binaryPathField = null
        snapshotsDirField = null
        heartbeatIntervalSpinner = null
        updateCheckBox = null
        routeBox = null
        startDaemonBox = null
        maxRpsSpinner = null
        maxDurationSpinner = null
        maxSessionsSpinner = null
        allowHostsField = null
    }
}
