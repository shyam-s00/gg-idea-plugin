package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.PruneCandidate
import dev.gopherglide.ggplugin.snap.PruneReport
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class SnapPruneResultDialog(project: Project, private val report: PruneReport) : DialogWrapper(project) {

    init {
        title = if (report.dryRun) "Prune Preview (Dry Run)" else "Prune Result"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(680, 420)

        val bannerText = when {
            report.candidates.isEmpty() -> "No snapshots match the given filters — nothing to prune."
            report.dryRun -> "${report.candidates.size} snapshot(s) would be deleted from ${report.snapDir}"
            else -> "Deleted ${report.deleted} of ${report.candidates.size} snapshot(s) from ${report.snapDir}"
        }
        val banner = JBLabel(bannerText)
        banner.foreground = if (report.errors.isEmpty()) JBColor(0x2E7D32, 0x66BB6A) else JBColor(0xC62828, 0xE57373)
        val originalFont = banner.font
        banner.font = originalFont.deriveFont(originalFont.style or Font.BOLD, originalFont.size + 1f)
        banner.border = JBUI.Borders.empty(4, 4, 8, 4)
        panel.add(banner, BorderLayout.NORTH)

        if (report.candidates.isNotEmpty()) {
            val table = JBTable(CandidateTableModel(report.candidates))
            panel.add(JBScrollPane(table), BorderLayout.CENTER)
        }

        if (report.errors.isNotEmpty()) {
            val errorsLabel = JBLabel("<html>" + report.errors.joinToString("<br>") { "⚠ $it" } + "</html>")
            errorsLabel.foreground = JBColor(0xC62828, 0xE57373)
            errorsLabel.border = JBUI.Borders.empty(8, 4, 4, 4)
            panel.add(errorsLabel, BorderLayout.SOUTH)
        }

        return panel
    }

    private class CandidateTableModel(private val candidates: List<PruneCandidate>) : AbstractTableModel() {
        private val columns = arrayOf("ID", "Tag", "Date", "File", "Reason")
        override fun getRowCount(): Int = candidates.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val candidate = candidates[rowIndex]
            return when (columnIndex) {
                0 -> candidate.id
                1 -> candidate.tag.ifBlank { "(untagged)" }
                2 -> candidate.date
                3 -> candidate.file
                4 -> candidate.reason
                else -> ""
            }
        }
    }

    companion object {
        fun show(project: Project, report: PruneReport) {
            SnapPruneResultDialog(project, report).show()
        }
    }
}
