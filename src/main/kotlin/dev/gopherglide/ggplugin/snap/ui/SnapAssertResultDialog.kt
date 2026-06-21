package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.AssertResult
import dev.gopherglide.ggplugin.snap.AssertViolation
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class SnapAssertResultDialog(project: Project, private val result: AssertResult) : DialogWrapper(project) {

    init {
        title = if (result.passed) "Snap Assert: PASSED" else "Snap Assert: FAILED"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(680, 420)

        val banner = JBLabel(
            if (result.passed) "✅ PASSED — no threshold violations" else "❌ FAILED — ${result.violations.size} violation(s)"
        )
        banner.foreground = if (result.passed) JBColor(0x2E7D32, 0x66BB6A) else JBColor(0xC62828, 0xE57373)
        val originalFont = banner.font
        banner.font = originalFont.deriveFont(originalFont.style or Font.BOLD, originalFont.size + 2f)
        banner.border = JBUI.Borders.empty(4, 4, 8, 4)
        panel.add(banner, BorderLayout.NORTH)

        if (result.violations.isNotEmpty()) {
            val table = JBTable(ViolationTableModel(result.violations))
            panel.add(JBScrollPane(table), BorderLayout.CENTER)
        }

        return panel
    }

    private class ViolationTableModel(private val violations: List<AssertViolation>) : AbstractTableModel() {
        private val columns = arrayOf("Verdict", "Endpoint", "Detail")
        override fun getRowCount(): Int = violations.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val violation = violations[rowIndex]
            return when (columnIndex) {
                0 -> violation.verdict
                1 -> violation.endpointId
                2 -> violation.message
                else -> ""
            }
        }
    }

    companion object {
        fun show(project: Project, result: AssertResult) {
            SnapAssertResultDialog(project, result).show()
        }
    }
}
