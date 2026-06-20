package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.SnapModel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class SnapDiffDialog(project: Project, private val snapA: SnapModel, private val snapB: SnapModel) : DialogWrapper(project) {

    init {
        title = "Diff: ${SnapViewDialog.displayTag(snapA)} → ${SnapViewDialog.displayTag(snapB)}"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(900, 520)

        val header = FormBuilder.createFormBuilder()
            .addLabeledComponent("Baseline (A):", JBLabel("${SnapViewDialog.displayTag(snapA)} — ${snapA.meta?.startTime ?: ""}"))
            .addLabeledComponent("Compare (B):", JBLabel("${SnapViewDialog.displayTag(snapB)} — ${snapB.meta?.startTime ?: ""}"))
            .panel
        header.border = JBUI.Borders.empty(4, 4, 8, 4)
        panel.add(header, BorderLayout.NORTH)

        val table = JBTable(DiffEndpointTableModel(snapA, snapB))
        table.setDefaultRenderer(DiffCell::class.java, DiffCellRenderer())
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        val legend = JBLabel(
            "<html><font color='#C62828'>Regression</font>&nbsp;&nbsp;" +
                "<font color='#B8860B'>Payload warning</font>&nbsp;&nbsp;" +
                "<font color='#2E7D32'>Improvement</font>&nbsp;&nbsp;" +
                "<font color='#1565C0'>Added</font>&nbsp;&nbsp;" +
                "<font color='#777777'>Removed</font></html>"
        )
        legend.border = JBUI.Borders.empty(6, 4)
        panel.add(legend, BorderLayout.SOUTH)

        return panel
    }

    companion object {
        fun show(project: Project, snapA: SnapModel, snapB: SnapModel) {
            SnapDiffDialog(project, snapA, snapB).show()
        }
    }
}
