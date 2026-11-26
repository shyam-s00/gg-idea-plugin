package dev.gopherglide.ggplugin.snap.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.snap.ManualChangeRow
import javax.swing.JLabel

class ManualChangesTableTest : BasePlatformTestCase() {
    private val events = listOf(
        RunAnnotation.Mark("cache-warm", 18.0),
        RunAnnotation.BiasChange(20, 20, 25.0, AnnotationSource.AGENT),
        RunAnnotation.HoldChange(280, 34.0, AnnotationSource.AUTOPILOT),
        RunAnnotation.HoldChange(null, 49.0)
    )

    private fun table() = ManualChangesTable(events)

    private fun rendered(table: ManualChangesTable, row: Int, column: Int): String {
        val component = table.prepareRenderer(table.getCellRenderer(row, column), row, column)
        return (component as? JLabel)?.text.orEmpty()
    }

    fun testItHasATypeAndASourceColumnAndOneRowPerEvent() {
        val table = table()
        assertEquals(5, table.columnCount)
        assertEquals(listOf("Time", "Type", "Source", "Event", "Bias"), (0 until 5).map { table.getColumnName(it) })
        assertEquals(4, table.rowCount)
    }

    fun testEachRowNamesWhoMadeTheChange() {
        val table = table()
        assertEquals(listOf("👤 User", "🤖 Agent", "⚡ Autopilot", "👤 User"), (0 until 4).map { rendered(table, it, 2) })
    }

    fun testHoldAndReleaseHaveTheirOwnTypes() {
        val table = table()
        assertEquals(
            listOf(ManualChangeRow.Kind.MARK, ManualChangeRow.Kind.BIAS, ManualChangeRow.Kind.HOLD, ManualChangeRow.Kind.RELEASE),
            (0 until 4).map { table.getValueAt(it, 1) }
        )
    }

    fun testHoldAndReleaseSayWhatHappenedToTheRate() {
        val table = table()
        assertEquals("Pinned at 280 RPS", rendered(table, 2, 3))
        assertEquals("hold 280", rendered(table, 2, 4))
        assertEquals("Hold released", rendered(table, 3, 3))
        assertEquals("schedule", rendered(table, 3, 4))
    }
}
