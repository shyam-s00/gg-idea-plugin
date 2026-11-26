package dev.gopherglide.ggplugin.snap.actions

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.notification.NotificationAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.snap.SnapCliRunner
import dev.gopherglide.ggplugin.snap.SnapDataManager
import dev.gopherglide.ggplugin.snap.SnapModel
import dev.gopherglide.ggplugin.snap.SnapReport
import dev.gopherglide.ggplugin.services.BinaryFeatureGate
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.io.File

/** Saves a snapshot's mission report to a file, or puts it on the clipboard as Markdown for a pull request. */
object SnapReportActions {
    fun export(project: Project, snap: SnapModel, parent: Component? = null, format: SnapReport.Format = SnapReport.Format.MARKDOWN) {
        BinaryFeatureGate.ensure(project, BinaryFeatureGate.REPORTS, parent) {
            val target = chooseFile(project, snap, parent, format) ?: return@ensure
            SnapCliRunner.run(project, SnapReport.exportArgs(snapPath(snap), target.absolutePath), parent) { output ->
                if (output.exitCode == 0) {
                    GopherGlideNotifications.info(project, "Report saved to ${target.name}", openAction(project, target))
                } else {
                    showFailure(project, parent, output.stderr.ifBlank { output.stdout }, output.exitCode)
                }
            }
        }
    }

    fun copyMarkdown(project: Project, snap: SnapModel, parent: Component? = null) {
        BinaryFeatureGate.ensure(project, BinaryFeatureGate.REPORTS, parent) {
            SnapCliRunner.run(project, SnapReport.markdownArgs(snapPath(snap)), parent) { output ->
                if (output.exitCode == 0 && output.stdout.isNotBlank()) {
                    CopyPasteManager.getInstance().setContents(StringSelection(output.stdout))
                    GopherGlideNotifications.info(project, "Report copied as Markdown. Paste it into the pull request.")
                } else {
                    showFailure(project, parent, output.stderr.ifBlank { output.stdout }, output.exitCode)
                }
            }
        }
    }

    private fun chooseFile(project: Project, snap: SnapModel, parent: Component?, format: SnapReport.Format): File? {
        val descriptor = FileSaverDescriptor("Export Mission Report", "Save as ${format.label} (.${format.flag})", format.flag)
        val factory = FileChooserFactory.getInstance()
        val dialog = if (parent != null) factory.createSaveFileDialog(descriptor, parent) else factory.createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, SnapReport.defaultFileName(snap, format)) ?: return null
        val file = wrapper.file
        return if (file.extension.lowercase() in setOf("md", "html", "htm")) file else File(file.path + "." + format.flag)
    }

    private fun openAction(project: Project, file: File) = NotificationAction.createSimple("Open") {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.let {
            OpenFileDescriptor(project, it).navigate(true)
        }
    }

    private fun showFailure(project: Project, parent: Component?, detail: String, exitCode: Int) {
        val message = detail.ifBlank { "gg snap report exited with code $exitCode and produced no output." }
        if (parent != null) Messages.showErrorDialog(parent, message, "Mission Report Failed")
        else Messages.showErrorDialog(project, message, "Mission Report Failed")
    }

    private fun snapPath(snap: SnapModel): String = File(SnapDataManager.getSnapshotsDir(), "${snap.id}.snap").absolutePath
}
