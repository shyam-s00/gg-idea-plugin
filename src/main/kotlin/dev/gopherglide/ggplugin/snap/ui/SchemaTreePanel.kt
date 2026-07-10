package dev.gopherglide.ggplugin.snap.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.GopherGlideIcons
import dev.gopherglide.ggplugin.snap.FieldSchema
import dev.gopherglide.ggplugin.snap.SchemaSnapshot
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableColumn
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

private const val ROW_HEIGHT = 22

/**
 * Renders an inferred [SchemaSnapshot] as a Field tree (splitting paths like
 * "orders[].orderId" into nested nodes) alongside a real, resizable table for
 * Type / Presence % / Stability. The two are synced row-for-row by index and
 * share one scroll position, so only the Field column shifts with nesting
 * depth — the other columns always line up, and columns are user-resizable.
 */
class SchemaTreePanel(schema: SchemaSnapshot?) : JPanel(BorderLayout()) {

    private val tree: Tree
    private val sideTableModel = SideTableModel()

    init {
        val root = DefaultMutableTreeNode(SchemaNode("root", null))
        if (schema == null || schema.fields.isEmpty()) {
            root.add(DefaultMutableTreeNode(SchemaNode("(no schema inferred)", null)))
        } else {
            buildTree(root, schema.fields)
        }

        tree = Tree(DefaultTreeModel(root))
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.rowHeight = JBUI.scale(ROW_HEIGHT)
        tree.cellRenderer = FieldNodeRenderer()
        tree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION
        expandAll(tree)

        val sideTable = JBTable(sideTableModel)
        sideTable.rowHeight = JBUI.scale(ROW_HEIGHT)
        sideTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sideTable.columnModel.getColumn(2).cellRenderer = StabilityCellRenderer()
        refreshSideRows()

        tree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent) = refreshSideRows()
            override fun treeCollapsed(event: TreeExpansionEvent) = refreshSideRows()
        })

        // Keep the two lists selection-synced by row index (they always have the same row count/order).
        tree.selectionModel.addTreeSelectionListener {
            val row = tree.selectionRows?.firstOrNull() ?: -1
            if (row >= 0 && sideTable.selectedRow != row) sideTable.setRowSelectionInterval(row, row)
        }
        sideTable.selectionModel.addListSelectionListener {
            val row = sideTable.selectedRow
            if (row >= 0 && tree.selectionRows?.firstOrNull() != row) tree.setSelectionRow(row)
        }

        val treeScroll = ScrollPaneFactory.createScrollPane(tree)
        treeScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        treeScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED

        val sideScroll = ScrollPaneFactory.createScrollPane(sideTable)
        sideScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        // Share one scrollbar model so the two lists always scroll in lockstep.
        sideScroll.verticalScrollBar.model = treeScroll.verticalScrollBar.model

        val treePane = JPanel(BorderLayout())
        treePane.add(fieldColumnHeader(), BorderLayout.NORTH)
        treePane.add(treeScroll, BorderLayout.CENTER)

        val splitter = OnePixelSplitter(false, 0.4f)
        splitter.setHonorComponentsMinimumSize(false)
        splitter.firstComponent = treePane
        splitter.secondComponent = sideScroll

        add(splitter, BorderLayout.CENTER)
        border = JBUI.Borders.customLine(JBColor.border(), 1)
    }

    /**
     * A real [JTableHeader] (not a hand-styled label) so the "Field" header
     * renders pixel-identical to the adjacent Type/Presence/Stability header —
     * a custom label here previously looked like a disconnected element
     * floating beside the real table header rather than part of one row.
     */
    private fun fieldColumnHeader(): JTableHeader {
        val columnModel = DefaultTableColumnModel()
        columnModel.addColumn(TableColumn(0).apply { headerValue = "Field" })
        return JTableHeader(columnModel)
    }

    private fun refreshSideRows() {
        val rows = (0 until tree.rowCount).map { i ->
            (tree.getPathForRow(i)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? SchemaNode
        }
        sideTableModel.setRows(rows)
    }

    private fun buildTree(parent: DefaultMutableTreeNode, fields: Map<String, FieldSchema>) {
        // Group field paths (dot/bracket separated) into a tree by shared prefixes.
        // A path like "metadata" (the object itself, own type/presence/stability)
        // and "metadata.source" (a child) share the head "metadata" — those must
        // become one expandable node, not a leaf plus a separate group node for
        // the same name.
        val childGroups = linkedMapOf<String, MutableMap<String, FieldSchema>>()
        val ownInfo = linkedMapOf<String, FieldSchema>()

        fields.toSortedMap().forEach { (path, info) ->
            val segments = path.replace("[]", ".[]").split(".").filter { it.isNotEmpty() }
            if (segments.size <= 1) {
                ownInfo[path] = info
            } else {
                val head = segments.first()
                val rest = segments.drop(1).joinToString(".")
                childGroups.getOrPut(head) { linkedMapOf() }[rest] = info
            }
        }

        (ownInfo.keys + childGroups.keys).toSortedSet().forEach { name ->
            val node = DefaultMutableTreeNode(SchemaNode(name, ownInfo[name]))
            parent.add(node)
            childGroups[name]?.let { nested -> buildTree(node, nested) }
        }
    }

    private fun expandAll(tree: JTree) {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    companion object {
        fun stabilityColor(stability: String): JBColor = when (stability.lowercase()) {
            "stable" -> JBColor(0x2E7D32, 0x66BB6A)
            "unstable" -> JBColor(0xB8860B, 0xFFB74D)
            else -> JBColor.GRAY
        }
    }
}

private data class SchemaNode(val name: String, val field: FieldSchema?) {
    override fun toString(): String = name
}

/**
 * Field-column tree renderer: name plus a real platform icon keyed to the
 * JSON type. `AllIcons.Json.Object`/`Array` are core-platform icons (ship
 * with every IDE). String/number use bundled expui icons matching the
 * Database plugin's style (that plugin itself is Ultimate-only and
 * unavailable in Community or GoLand — which this plugin explicitly
 * targets — so the icons are shipped as plugin resources instead of
 * referencing that plugin's classes). Boolean/null share
 * `AllIcons.Debugger.Db_primitive`, since there's no equivalent bundled yet.
 */
private class FieldNodeRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = (value as? DefaultMutableTreeNode)?.userObject as? SchemaNode ?: return component
        text = node.name
        icon = iconFor(node, leaf)
        return component
    }

    private fun iconFor(node: SchemaNode, isLeaf: Boolean): Icon? {
        val type = node.field?.type?.lowercase()
        return when (type) {
            "object" -> AllIcons.Json.Object
            "array" -> AllIcons.Json.Array
            "string" -> GopherGlideIcons.SchemaString
            "number" -> GopherGlideIcons.SchemaNumber
            null -> if (!isLeaf) AllIcons.Json.Object else null // grouping node vs. "(no schema inferred)" placeholder
            else -> AllIcons.Debugger.Db_primitive // boolean, null
        }
    }
}

private class SideTableModel : AbstractTableModel() {
    private var rows: List<SchemaNode?> = emptyList()
    private val columns = arrayOf("Type", "Presence %", "Stability")

    fun setRows(newRows: List<SchemaNode?>) {
        rows = newRows
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val field = rows.getOrNull(rowIndex)?.field ?: return ""
        return when (columnIndex) {
            0 -> field.type
            1 -> "%.0f%%".format(field.presence * 100)
            2 -> field.stability
            else -> ""
        }
    }
}

private class StabilityCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (!isSelected) {
            foreground = (value as? String)?.takeIf { it.isNotBlank() }
                ?.let { SchemaTreePanel.stabilityColor(it) }
                ?: table?.foreground
        }
        return component
    }
}
