package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.gopherglide.ggplugin.snap.SnapDataManager
import dev.gopherglide.ggplugin.snap.SnapModel
import dev.gopherglide.ggplugin.snap.actions.DiffSnapsAction
import dev.gopherglide.ggplugin.snap.actions.RefreshSnapsAction
import dev.gopherglide.ggplugin.snap.actions.ViewSnapAction
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

object SnapToolWindowFactory {

    fun buildPanel(project: Project): JPanel {
        val panel = JPanel(BorderLayout())
        val tableModel = SnapTableModel()
        val table = JBTable(tableModel)

        tables[project] = table

        // Load initial data
        refreshTable(project)

        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Actions
        val actionGroup = DefaultActionGroup()
        actionGroup.add(RefreshSnapsAction())
        actionGroup.add(ViewSnapAction())
        actionGroup.add(DiffSnapsAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("GopherGlideSnapToolbar", actionGroup, true)
        toolbar.targetComponent = table
        panel.add(toolbar.component, BorderLayout.NORTH)

        // Double click to view
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && table.selectedRowCount == 1) {
                    val snap = getSelectedSnaps(project).firstOrNull()
                    if (snap != null) {
                        SnapViewDialog.show(project, snap)
                    }
                }
            }
        })

        return panel
    }

    private val tables = mutableMapOf<Project, JBTable>()

    fun refreshTable(project: Project) {
        val table = tables[project] ?: return
        val model = table.model as? SnapTableModel ?: return
        val snaps = SnapDataManager.loadSnaps()
        model.setSnaps(snaps)
    }

    fun getSelectedSnaps(project: Project): List<SnapModel> {
        val table = tables[project] ?: return emptyList()
        val model = table.model as? SnapTableModel ?: return emptyList()
        val selectedRows = table.selectedRows
        return selectedRows.toList().mapNotNull { row ->
            model.getSnapAt(table.convertRowIndexToModel(row))
        }
    }
}
