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
import java.awt.font.TextAttribute
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableColumn
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

private const val ROW_HEIGHT = 22
private const val PRESENCE_DELTA_THRESHOLD = 0.10

/**
 * Baseline/target schema comparison, structured as a diff over the same twin
 * Field-tree + Type/Presence/Stability table from [SchemaTreePanel] (Milestone
 * 2) rather than a literal line-numbered JSON text diff — reusing the tree's
 * alignment/resize handling and the same [DiffCell]/[DiffSeverity] coloring
 * already used by the endpoint diff table, so there's one color language
 * across the whole dialog instead of a second one just for schemas.
 */
class SchemaDiffPanel(schemaA: SchemaSnapshot?, schemaB: SchemaSnapshot?) : JPanel(BorderLayout()) {

    private val tree: Tree
    private val sideTableModel = DiffSideTableModel()

    init {
        val root = DefaultMutableTreeNode(SchemaDiffNode("root", null, null))
        val fieldsA = schemaA?.fields ?: emptyMap()
        val fieldsB = schemaB?.fields ?: emptyMap()
        if (fieldsA.isEmpty() && fieldsB.isEmpty()) {
            root.add(DefaultMutableTreeNode(SchemaDiffNode("(no schema inferred)", null, null)))
        } else {
            buildDiffTree(root, fieldsA, fieldsB)
        }

        tree = Tree(DefaultTreeModel(root))
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.rowHeight = JBUI.scale(ROW_HEIGHT)
        tree.cellRenderer = FieldDiffRenderer()
        tree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION
        expandAll(tree)

        val sideTable = JBTable(sideTableModel)
        sideTable.rowHeight = JBUI.scale(ROW_HEIGHT)
        sideTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        sideTable.setDefaultRenderer(DiffCell::class.java, DiffCellRenderer())
        refreshSideRows()

        tree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent) = refreshSideRows()
            override fun treeCollapsed(event: TreeExpansionEvent) = refreshSideRows()
        })

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

    private fun fieldColumnHeader(): JTableHeader {
        val columnModel = DefaultTableColumnModel()
        columnModel.addColumn(TableColumn(0).apply { headerValue = "Field" })
        return JTableHeader(columnModel)
    }

    private fun refreshSideRows() {
        val rows = (0 until tree.rowCount).map { i ->
            (tree.getPathForRow(i)?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? SchemaDiffNode
        }
        sideTableModel.setRows(rows)
    }

    private fun buildDiffTree(parent: DefaultMutableTreeNode, fieldsA: Map<String, FieldSchema>, fieldsB: Map<String, FieldSchema>) {
        val (ownA, childrenA) = splitFields(fieldsA)
        val (ownB, childrenB) = splitFields(fieldsB)
        val names = (ownA.keys + ownB.keys + childrenA.keys + childrenB.keys).toSortedSet()

        names.forEach { name ->
            val node = DefaultMutableTreeNode(SchemaDiffNode(name, ownA[name], ownB[name]))
            parent.add(node)
            val nestedA = childrenA[name] ?: emptyMap()
            val nestedB = childrenB[name] ?: emptyMap()
            if (nestedA.isNotEmpty() || nestedB.isNotEmpty()) buildDiffTree(node, nestedA, nestedB)
        }
    }

    private fun splitFields(fields: Map<String, FieldSchema>): Pair<Map<String, FieldSchema>, Map<String, Map<String, FieldSchema>>> {
        val own = linkedMapOf<String, FieldSchema>()
        val children = linkedMapOf<String, MutableMap<String, FieldSchema>>()
        fields.toSortedMap().forEach { (path, info) ->
            val segments = path.replace("[]", ".[]").split(".").filter { it.isNotEmpty() }
            if (segments.size <= 1) {
                own[path] = info
            } else {
                val head = segments.first()
                val rest = segments.drop(1).joinToString(".")
                children.getOrPut(head) { linkedMapOf() }[rest] = info
            }
        }
        return own to children
    }

    private fun expandAll(tree: JTree) {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    companion object {
        private fun pct(p: Double) = "%.0f%%".format(p * 100)

        private fun stabilityRank(stability: String): Int = when (stability.lowercase()) {
            "stable" -> 2
            "rare" -> 1
            "unstable" -> 0
            else -> 1
        }

        fun typeCell(before: FieldSchema?, after: FieldSchema?): DiffCell = when {
            before == null && after == null -> DiffCell("")
            before == null -> DiffCell("${after!!.type}  NEW", DiffSeverity.ADDED)
            after == null -> DiffCell("${before.type}  GONE", DiffSeverity.REMOVED)
            before.type != after.type -> DiffCell("${after.type}  CHANGED", DiffSeverity.WARNING)
            else -> DiffCell(after.type, DiffSeverity.NEUTRAL)
        }

        fun presenceCell(before: FieldSchema?, after: FieldSchema?): DiffCell = when {
            before == null && after == null -> DiffCell("")
            before == null -> DiffCell("${pct(after!!.presence)}  NEW", DiffSeverity.ADDED)
            after == null -> DiffCell("${pct(before.presence)}  GONE", DiffSeverity.REMOVED)
            else -> {
                val delta = after.presence - before.presence
                val severity = when {
                    delta <= -PRESENCE_DELTA_THRESHOLD -> DiffSeverity.REGRESSION
                    delta >= PRESENCE_DELTA_THRESHOLD -> DiffSeverity.IMPROVEMENT
                    else -> DiffSeverity.NEUTRAL
                }
                DiffCell(pct(after.presence) + DiffFormat.deltaSuffix(delta, "pp"), severity)
            }
        }

        fun stabilityCell(before: FieldSchema?, after: FieldSchema?): DiffCell = when {
            before == null && after == null -> DiffCell("")
            before == null -> DiffCell("${after!!.stability}  NEW", DiffSeverity.ADDED)
            after == null -> DiffCell("${before.stability}  GONE", DiffSeverity.REMOVED)
            before.stability == after.stability -> DiffCell(after.stability, DiffSeverity.NEUTRAL)
            before.type != after.type -> DiffCell("${after.stability}  CHANGED", DiffSeverity.WARNING)
            else -> {
                val severity = when {
                    stabilityRank(after.stability) < stabilityRank(before.stability) -> DiffSeverity.REGRESSION
                    stabilityRank(after.stability) > stabilityRank(before.stability) -> DiffSeverity.IMPROVEMENT
                    else -> DiffSeverity.WARNING
                }
                DiffCell("${after.stability}  CHANGED", severity)
            }
        }
    }
}

private data class SchemaDiffNode(val name: String, val before: FieldSchema?, val after: FieldSchema?) {
    val isAdded: Boolean get() = before == null && after != null
    val isRemoved: Boolean get() = after == null && before != null
    override fun toString(): String = name
}

/** Field-column tree renderer: name (blue if added, gray + struck-through if removed) plus a type icon. */
private class FieldDiffRenderer : DefaultTreeCellRenderer() {
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
        val node = (value as? DefaultMutableTreeNode)?.userObject as? SchemaDiffNode ?: return component
        icon = iconFor(node, leaf)
        when {
            node.isAdded -> {
                text = "${node.name}  NEW"
                foreground = ADDED_COLOR
            }
            node.isRemoved -> {
                text = "${node.name}  GONE"
                foreground = REMOVED_COLOR
                font = font.deriveFont(mapOf(TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON))
            }
            else -> text = node.name
        }
        return component
    }

    private fun iconFor(node: SchemaDiffNode, isLeaf: Boolean): Icon? {
        val type = (node.after ?: node.before)?.type?.lowercase()
        return when (type) {
            "object" -> AllIcons.Json.Object
            "array" -> AllIcons.Json.Array
            "string" -> GopherGlideIcons.SchemaString
            "number" -> GopherGlideIcons.SchemaNumber
            null -> if (!isLeaf) AllIcons.Json.Object else null
            else -> AllIcons.Debugger.Db_primitive
        }
    }

    companion object {
        val ADDED_COLOR: JBColor = JBColor(0x1565C0, 0x64B5F6)
        val REMOVED_COLOR: JBColor = JBColor.GRAY
    }
}

private class DiffSideTableModel : AbstractTableModel() {
    private var rows: List<SchemaDiffNode?> = emptyList()
    private val columns = arrayOf("Type", "Presence %", "Stability")

    fun setRows(newRows: List<SchemaDiffNode?>) {
        rows = newRows
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    override fun getColumnClass(columnIndex: Int): Class<*> = DiffCell::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val node = rows.getOrNull(rowIndex) ?: return DiffCell("")
        return when (columnIndex) {
            0 -> SchemaDiffPanel.typeCell(node.before, node.after)
            1 -> SchemaDiffPanel.presenceCell(node.before, node.after)
            2 -> SchemaDiffPanel.stabilityCell(node.before, node.after)
            else -> DiffCell("")
        }
    }
}
