package dev.gopherglide.ggplugin.snap.actions

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.snap.PruneReport
import dev.gopherglide.ggplugin.snap.SnapCliRunner
import dev.gopherglide.ggplugin.snap.SnapDataManager
import dev.gopherglide.ggplugin.snap.ui.SnapPruneOptionsDialog
import dev.gopherglide.ggplugin.snap.ui.SnapPruneResultDialog
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory

class PruneSnapsAction : AnAction("Prune...", "Delete snapshots by ID, keep-last, older-than, or tag filter", com.intellij.icons.AllIcons.Actions.GC) {
    private val gson = Gson()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val preselectedIds = SnapToolWindowFactory.getSelectedSnaps(project).map { it.internalIndex }
        val optionsDialog = SnapPruneOptionsDialog(project, preselectedIds)
        if (!optionsDialog.showAndGet()) return

        val args = mutableListOf(
            "snap", "prune",
            "--snap-dir", SnapDataManager.getSnapshotsDir().absolutePath,
            "--reporter", "json"
        )
        if (optionsDialog.ids.isNotBlank()) args.addAll(listOf("--ids", optionsDialog.ids))
        if (optionsDialog.keepLast > 0) args.addAll(listOf("--keep-last", optionsDialog.keepLast.toString()))
        if (optionsDialog.olderThan.isNotBlank()) args.addAll(listOf("--older-than", optionsDialog.olderThan))
        if (optionsDialog.tag.isNotBlank()) args.addAll(listOf("--tag", optionsDialog.tag))
        if (optionsDialog.dryRun) args.add("--dry-run")

        SnapCliRunner.run(project, args) { output ->
            val report = try {
                gson.fromJson(output.stdout, PruneReport::class.java)
            } catch (ex: Exception) {
                null
            }

            if (report != null) {
                SnapPruneResultDialog.show(project, report)
                if (!report.dryRun && report.deleted > 0) {
                    SnapToolWindowFactory.refreshTable(project)
                }
            } else {
                Messages.showErrorDialog(
                    project,
                    (output.stderr.ifBlank { output.stdout }).ifBlank { "gg snap prune exited with code ${output.exitCode} and produced no output." },
                    "Snap Prune Failed to Run"
                )
            }
        }
    }
}
