package dev.gopherglide.ggplugin.snap.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableColumn
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Wraps [table] with a search box (filters across all columns) and a
 * column-chooser popup, and returns the assembled panel (toolbar + scrollable
 * table) to place directly in a container. Shared by [EndpointTablePanel] and
 * the diff endpoint table — both have too many columns to show comfortably
 * at once.
 *
 * Column visibility is persisted (application-level, via [PropertiesComponent])
 * under [storageKey], keyed by column *name* rather than index so a future
 * reordering/insertion of columns doesn't silently restore the wrong ones.
 */
fun buildFilterableColumnTable(
    table: JBTable,
    model: TableModel,
    defaultVisibleColumns: Set<Int>,
    searchPlaceholder: String,
    storageKey: String
): JComponent {
    table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    val sorter = TableRowSorter(model)
    table.rowSorter = sorter

    val allColumns: List<TableColumn> = java.util.Collections.list(table.columnModel.columns)
    val properties = PropertiesComponent.getInstance()
    val savedNames = properties.getList(storageKey)
    val visible = BooleanArray(model.columnCount) { i ->
        savedNames?.let { model.getColumnName(i) in it } ?: (i in defaultVisibleColumns)
    }

    fun rebuildColumns() {
        while (table.columnModel.columnCount > 0) {
            table.columnModel.removeColumn(table.columnModel.getColumn(0))
        }
        allColumns.forEachIndexed { index, column ->
            if (visible[index]) table.columnModel.addColumn(column)
        }
    }

    fun saveVisibility() {
        val visibleNames = (0 until model.columnCount).filter { visible[it] }.map { model.getColumnName(it) }
        properties.setList(storageKey, visibleNames)
    }

    val searchField = SearchTextField()
    searchField.textEditor.emptyText.text = searchPlaceholder
    searchField.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = applyFilter(sorter, searchField.text)
        override fun removeUpdate(e: DocumentEvent) = applyFilter(sorter, searchField.text)
        override fun changedUpdate(e: DocumentEvent) = applyFilter(sorter, searchField.text)
    })

    val columnsButton = JButton("Columns ▾")
    columnsButton.addActionListener {
        val menu = JPopupMenu()
        for (i in 0 until model.columnCount) {
            val item = JCheckBoxMenuItem(model.getColumnName(i), visible[i])
            item.addActionListener {
                visible[i] = item.isSelected
                rebuildColumns()
                saveVisibility()
            }
            menu.add(item)
        }
        menu.show(columnsButton, 0, columnsButton.height)
    }

    val toolbar = JPanel(BorderLayout(JBUI.scale(8), 0))
    toolbar.border = JBUI.Borders.empty(4, 4, 4, 4)
    toolbar.add(searchField, BorderLayout.CENTER)
    toolbar.add(columnsButton, BorderLayout.EAST)

    val panel = JPanel(BorderLayout())
    panel.add(toolbar, BorderLayout.NORTH)
    panel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)

    rebuildColumns()
    return panel
}

private fun applyFilter(sorter: TableRowSorter<out TableModel>, text: String) {
    sorter.rowFilter = if (text.isBlank()) null else RowFilter.regexFilter("(?i)" + Pattern.quote(text))
}
