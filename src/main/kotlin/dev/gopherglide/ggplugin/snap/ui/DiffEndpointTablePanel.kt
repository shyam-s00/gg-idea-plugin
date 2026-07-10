package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ui.table.JBTable
import dev.gopherglide.ggplugin.snap.EndpointSnap
import java.awt.BorderLayout
import javax.swing.JPanel

/** Wraps the diff endpoint [JBTable] with a search box and column chooser (see [buildFilterableColumnTable]). */
class DiffEndpointTablePanel(private val tableModel: DiffEndpointTableModel) : JPanel(BorderLayout()) {

    val table = JBTable(tableModel)

    init {
        table.setDefaultRenderer(DiffCell::class.java, DiffCellRenderer())
        add(
            buildFilterableColumnTable(
                table, tableModel, DEFAULT_VISIBLE_COLUMNS, "Filter endpoints...",
                storageKey = "gg.snapDiff.endpointTable.visibleColumns"
            ),
            BorderLayout.CENTER
        )
    }

    fun getSelectedEndpoints(): Pair<EndpointSnap?, EndpointSnap?>? {
        val row = table.selectedRow
        return if (row >= 0) tableModel.getEndpointsAt(table.convertRowIndexToModel(row)) else null
    }

    companion object {
        // Endpoint, Requests, Error Rate, P95, Payload Max — the rest (P50, P99, Payload Avg)
        // stay one click away via "Columns".
        private val DEFAULT_VISIBLE_COLUMNS = setOf(0, 1, 2, 4, 7)
    }
}
