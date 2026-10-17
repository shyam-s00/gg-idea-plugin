package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.EndpointSnap
import dev.gopherglide.ggplugin.snap.SnapMeta
import dev.gopherglide.ggplugin.snap.SnapModel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.BoxLayout
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
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(960, 600)

        val top = JPanel(BorderLayout())
        val banners = listOfNotNull(MismatchBanner.forSnaps(snapA, snapB), ManualChangesBanner.forSnaps(snapA, snapB))
        if (banners.isNotEmpty()) {
            val stack = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
            banners.forEach { stack.add(it) }
            top.add(stack, BorderLayout.NORTH)
        }
        top.add(comparativeHeader(snapA, snapB), BorderLayout.CENTER)
        root.add(top, BorderLayout.NORTH)

        val tableModel = DiffEndpointTableModel(snapA, snapB)
        val tablePanel = DiffEndpointTablePanel(tableModel)

        val detail = DetailPane()
        tablePanel.table.selectionModel.addListSelectionListener {
            val endpoints = tablePanel.getSelectedEndpoints()
            detail.showEndpoints(endpoints?.first, endpoints?.second)
        }

        val splitter = OnePixelSplitter(false, 0.55f)
        splitter.setHonorComponentsMinimumSize(false)
        splitter.firstComponent = tablePanel
        splitter.secondComponent = detail
        root.add(splitter, BorderLayout.CENTER)

        val legend = JBLabel(
            "<html><font color='#C62828'>Regression</font>&nbsp;&nbsp;" +
                "<font color='#B8860B'>Payload warning</font>&nbsp;&nbsp;" +
                "<font color='#2E7D32'>Improvement</font>&nbsp;&nbsp;" +
                "<font color='#1565C0'>Added</font>&nbsp;&nbsp;" +
                "<font color='#777777'>Removed</font></html>"
        )
        legend.border = JBUI.Borders.empty(6, 8)
        root.add(legend, BorderLayout.SOUTH)

        return root
    }

    /** Right-hand detail pane: schema diff and status distribution diff for the selected endpoint. */
    private class DetailPane : JPanel(BorderLayout()) {
        private val placeholder = JBLabel("Select an endpoint to compare its schema and status distribution.").apply {
            border = JBUI.Borders.empty(12)
            foreground = JBColor.GRAY
        }

        init {
            showEndpoints(null, null)
        }

        fun showEndpoints(endpointA: EndpointSnap?, endpointB: EndpointSnap?) {
            removeAll()
            if (endpointA == null && endpointB == null) {
                add(placeholder, BorderLayout.CENTER)
            } else {
                val content = JPanel(BorderLayout())

                val schemaSection = titledSection(
                    "Inferred JSON Schema (diff)",
                    SchemaDiffPanel(endpointA?.schema, endpointB?.schema)
                )
                val statusSection = titledSection(
                    "Status Code Distribution",
                    StatusDistributionDiffPanel(endpointA, endpointB)
                )

                val splitter = OnePixelSplitter(true, 0.65f)
                splitter.setHonorComponentsMinimumSize(false)
                splitter.firstComponent = schemaSection
                splitter.secondComponent = statusSection

                content.add(splitter, BorderLayout.CENTER)
                add(content, BorderLayout.CENTER)
            }
            revalidate()
            repaint()
        }

        private fun titledSection(title: String, content: JComponent): JPanel {
            val panel = JPanel(BorderLayout())
            val titleLabel = JBLabel(title).apply {
                border = JBUI.Borders.empty(6, 8)
                font = font.deriveFont(font.style or java.awt.Font.BOLD)
            }
            panel.add(titleLabel, BorderLayout.NORTH)
            panel.add(content, BorderLayout.CENTER)
            return panel
        }
    }

    companion object {
        private fun profileOrHash(meta: SnapMeta?): String =
            meta?.profile?.ifBlank { null } ?: meta?.configHash?.ifBlank { null }?.let { "config: ${it.take(12)}…" } ?: "—"

        private fun comparativeHeader(snapA: SnapModel, snapB: SnapModel): JComponent {
            val metaA = snapA.meta
            val metaB = snapB.meta
            return ComparativeMetricHeaderPanel(
                listOf(
                    ComparativeMetric("Tag", SnapViewDialog.displayTag(snapA), SnapViewDialog.displayTag(snapB)),
                    ComparativeMetric("Date / Time", metaA?.startTime ?: "—", metaB?.startTime ?: "—"),
                    ComparativeMetric(
                        "Total Requests",
                        (metaA?.totalRequests ?: 0).toString(),
                        (metaB?.totalRequests ?: 0).toString()
                    ),
                    ComparativeMetric(
                        "Peak RPS",
                        "%.1f r/s".format(metaA?.peakRps ?: 0.0),
                        "%.1f r/s".format(metaB?.peakRps ?: 0.0)
                    ),
                    ComparativeMetric("Profile", profileOrHash(metaA), profileOrHash(metaB))
                )
            )
        }

        fun show(project: Project, snapA: SnapModel, snapB: SnapModel) {
            SnapDiffDialog(project, snapA, snapB).show()
        }
    }
}
