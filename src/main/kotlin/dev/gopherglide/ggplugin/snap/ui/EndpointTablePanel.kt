package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.table.JBTable
import dev.gopherglide.ggplugin.snap.EndpointSnap
import java.awt.BorderLayout
import javax.swing.JPanel

/** Wraps the endpoint [JBTable] with a search box and column chooser (see [buildFilterableColumnTable]). */
class EndpointTablePanel(private val tableModel: EndpointTableModel) : JPanel(BorderLayout()) {

    val table = JBTable(tableModel)

    init {
        add(
            buildFilterableColumnTable(
                table, tableModel, DEFAULT_VISIBLE_COLUMNS, "Filter endpoints...",
                storageKey = "gg.snapView.endpointTable.visibleColumns"
            ),
            BorderLayout.CENTER
        )
    }

    fun getSelectedEndpoint(): EndpointSnap? {
        val row = table.selectedRow
        return if (row >= 0) tableModel.getEndpointAt(table.convertRowIndexToModel(row)) else null
    }

    companion object {
        // Endpoint, Requests, Error Rate, P95, Payload Avg — the rest (P50, P99,
        // Max, Payload P95, Payload Max) stay one click away via "Columns".
        private val DEFAULT_VISIBLE_COLUMNS = setOf(0, 1, 2, 4, 7)
    }
}
