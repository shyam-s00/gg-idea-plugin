package dev.gopherglide.ggplugin.snap.actions

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import dev.gopherglide.ggplugin.snap.AssertResult
import dev.gopherglide.ggplugin.snap.SnapCliRunner
import dev.gopherglide.ggplugin.snap.SnapDataManager
import dev.gopherglide.ggplugin.snap.SnapModel
import dev.gopherglide.ggplugin.snap.ui.SnapAssertOptionsDialog
import dev.gopherglide.ggplugin.snap.ui.SnapAssertResultDialog
import dev.gopherglide.ggplugin.snap.ui.SnapToolWindowFactory
import java.io.File

class AssertSnapsAction : AnAction("Assert...", "Run gg snap assert between two snapshots", com.intellij.icons.AllIcons.RunConfigurations.TestState.Run) {
    private val gson = Gson()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        e.presentation.isEnabled = SnapToolWindowFactory.getSelectedSnaps(project).size == 2
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedSnaps = SnapToolWindowFactory.getSelectedSnaps(project)
        if (selectedSnaps.size != 2) return

        val (baseline, current) = selectedSnaps.sortedBy { it.meta?.startTime ?: "" }

        val optionsDialog = SnapAssertOptionsDialog(project)
        if (!optionsDialog.showAndGet()) return

        val args = mutableListOf(
            "snap", "assert",
            "--baseline", snapFilePath(baseline),
            "--current", snapFilePath(current),
            "--latency-regression", optionsDialog.latencyRegressionPct.toString(),
            "--error-rate-delta", (optionsDialog.errorRateDeltaPct / 100).toString(),
            "--payload-size-delta", optionsDialog.payloadSizeDeltaPct.toString(),
            "--reporter", "json"
        )
        if (optionsDialog.denyRemovedFields) args.add("--deny-removed-fields")
        if (optionsDialog.failOnWarn) args.add("--fail-on-warn")

        SnapCliRunner.run(project, args) { output ->
            val result = try {
                gson.fromJson(output.stdout, AssertResult::class.java)
            } catch (ex: Exception) {
                null
            }

            if (result != null) {
                SnapAssertResultDialog.show(project, result)
            } else {
                Messages.showErrorDialog(
                    project,
                    (output.stderr.ifBlank { output.stdout }).ifBlank { "gg snap assert exited with code ${output.exitCode} and produced no output." },
                    "Snap Assert Failed to Run"
                )
            }
        }
    }

    private fun snapFilePath(snap: SnapModel): String =
        File(SnapDataManager.getSnapshotsDir(), "${snap.id}.snap").absolutePath
}
