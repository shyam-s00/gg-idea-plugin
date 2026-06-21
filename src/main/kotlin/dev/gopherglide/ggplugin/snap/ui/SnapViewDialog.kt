package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.EndpointSnap
import dev.gopherglide.ggplugin.snap.SnapModel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel

class SnapViewDialog(project: Project, private val snap: SnapModel) : DialogWrapper(project) {

    init {
        title = "Snap: ${displayTag(snap)}"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(760, 500)

        val meta = snap.meta
        val header = FormBuilder.createFormBuilder()
            .addLabeledComponent("Tag:", JBLabel(displayTag(snap)))
            .addLabeledComponent("Start:", JBLabel(meta?.startTime ?: ""))
            .addLabeledComponent("End:", JBLabel(meta?.endTime ?: ""))
            .addLabeledComponent("Peak RPS:", JBLabel("%.1f".format(meta?.peakRps ?: 0.0)))
            .addLabeledComponent("Total Requests:", JBLabel((meta?.totalRequests ?: 0).toString()))
            .panel
        header.border = JBUI.Borders.empty(4, 4, 8, 4)
        panel.add(header, BorderLayout.NORTH)

        val tableModel = EndpointTableModel(snap.endpoints)
        val table = JBTable(tableModel)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val detailArea = JBTextArea()
        detailArea.isEditable = false
        detailArea.text = if (snap.endpoints.isEmpty()) {
            "This snapshot has no endpoint data."
        } else {
            "Select an endpoint above to view its status distribution and inferred schema."
        }

        table.selectionModel.addListSelectionListener {
            val row = table.selectedRow
            val endpoint = if (row >= 0) tableModel.getEndpointAt(table.convertRowIndexToModel(row)) else null
            if (endpoint != null) detailArea.text = formatDetail(endpoint)
        }

        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(table), JBScrollPane(detailArea))
        split.resizeWeight = 0.6
        panel.add(split, BorderLayout.CENTER)

        return panel
    }

    private fun formatDetail(endpoint: EndpointSnap): String {
        val sb = StringBuilder()
        sb.append("Status distribution:\n")
        if (endpoint.statusDist.isEmpty()) {
            sb.append("  (none)\n")
        } else {
            endpoint.statusDist.entries.sortedBy { it.key }.forEach { (status, fraction) ->
                sb.append("  $status: %.1f%%\n".format(fraction * 100))
            }
        }

        val schema = endpoint.schema
        if (schema != null) {
            sb.append("\nInferred schema (from ${schema.sampleCount} sampled bodies):\n")
            schema.fields.entries.sortedBy { it.key }.forEach { (field, info) ->
                sb.append("  $field: ${info.type} — present %.0f%% (${info.stability})\n".format(info.presence * 100))
            }
        } else {
            sb.append("\nNo schema inferred (no JSON body samples stored for this endpoint).")
        }
        return sb.toString()
    }

    companion object {
        fun displayTag(snap: SnapModel): String = snap.meta?.tag?.ifBlank { null } ?: "(untagged)"

        fun show(project: Project, snap: SnapModel) {
            SnapViewDialog(project, snap).show()
        }
    }
}
