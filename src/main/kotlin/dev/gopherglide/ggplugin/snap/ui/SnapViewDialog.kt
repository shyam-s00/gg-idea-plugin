package dev.gopherglide.ggplugin.snap.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.EndpointSnap
import dev.gopherglide.ggplugin.snap.RunTimeline
import dev.gopherglide.ggplugin.snap.SnapModel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class SnapViewDialog(project: Project, private val snap: SnapModel) : DialogWrapper(project) {

    init {
        title = "Snap: ${displayTag(snap)}"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(900, 560)

        val top = JPanel(BorderLayout())
        top.add(metaHeader(snap), BorderLayout.NORTH)
        val timeline = RunTimeline.from(snap.meta)
        if (timeline != null) {
            top.add(ManualChangesSection(timeline), BorderLayout.CENTER)
        } else {
            RunTimeline.absenceNote(snap.meta)?.let { top.add(ManualChangesNote(it), BorderLayout.CENTER) }
        }
        root.add(top, BorderLayout.NORTH)

        val tableModel = EndpointTableModel(snap.endpoints)
        val tablePanel = EndpointTablePanel(tableModel)

        val detail = DetailPane()

        tablePanel.table.selectionModel.addListSelectionListener {
            detail.showEndpoint(tablePanel.getSelectedEndpoint())
        }

        val splitter = OnePixelSplitter(false, 0.5f)
        splitter.setHonorComponentsMinimumSize(false)
        splitter.firstComponent = tablePanel
        splitter.secondComponent = detail
        root.add(splitter, BorderLayout.CENTER)

        return root
    }

    /** Right-hand detail pane: schema tree, status distribution, and sampling stats for the selected endpoint. */
    private class DetailPane : JPanel(BorderLayout()) {
        private val placeholder = JBLabel("Select an endpoint to view its schema and status distribution.").apply {
            border = JBUI.Borders.empty(12)
            foreground = JBColor.GRAY
        }

        init {
            showEndpoint(null)
        }

        fun showEndpoint(endpoint: EndpointSnap?) {
            removeAll()
            if (endpoint == null) {
                add(placeholder, BorderLayout.CENTER)
            } else {
                val content = JPanel(BorderLayout())

                val samplingLabel = JBLabel(samplingText(endpoint)).apply {
                    border = JBUI.Borders.empty(8, 8, 4, 8)
                    foreground = JBColor.GRAY
                }
                content.add(samplingLabel, BorderLayout.NORTH)

                val schemaSection = titledSection("Inferred JSON Schema", SchemaTreePanel(endpoint.schema))
                val statusSection = titledSection(
                    "Status Code Distribution",
                    StatusDistributionPanel(endpoint.statusDist, endpoint.errorRate)
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

        private fun samplingText(endpoint: EndpointSnap): String {
            val observed = endpoint.bodySamplesObserved
            val stored = endpoint.bodySamplesStored
            if (observed <= 0) return "No body samples observed for this endpoint."
            val confidence = (stored.toDouble() / observed.toDouble() * 100).coerceIn(0.0, 100.0)
            return "Body samples: $stored stored / $observed observed (%.0f%% of traffic sampled)".format(confidence)
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
        fun displayTag(snap: SnapModel): String = snap.meta?.tag?.ifBlank { null } ?: "(untagged)"

        private fun samplingSummary(meta: dev.gopherglide.ggplugin.snap.SnapMeta?): String {
            val settings = meta?.snapSettings ?: return "—"
            val ratePct = settings.sampleRate * 100
            val inverse = if (settings.sampleRate > 0) (1.0 / settings.sampleRate).let { "1 in %.0f".format(it) } else "n/a"
            return "%.1f%% (%s)".format(ratePct, inverse)
        }

        private fun formatTimestamp(raw: String): String = try {
            java.time.OffsetDateTime.parse(raw)
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", java.util.Locale.US))
        } catch (e: java.time.format.DateTimeParseException) {
            raw
        }

        private fun truncateMiddle(text: String, max: Int = 26): String {
            if (text.length <= max) return text
            val keep = (max - 1) / 2
            return text.take(keep) + "…" + text.takeLast(max - keep - 1)
        }

        private fun dateTimeMetric(meta: dev.gopherglide.ggplugin.snap.SnapMeta?): Metric {
            if (meta == null || meta.startTime.isBlank()) return Metric("Date / Time", "—")
            val tooltip = if (meta.endTime.isNotBlank() && meta.endTime != meta.startTime) {
                "${meta.startTime} → ${meta.endTime}"
            } else {
                meta.startTime
            }
            return Metric("Date / Time", formatTimestamp(meta.startTime), tooltip)
        }

        private fun metaHeader(snap: SnapModel): JComponent {
            val meta = snap.meta
            val profileText = meta?.profile?.ifBlank { null } ?: "(legacy — no profile recorded)"
            val configHash = meta?.configHash?.ifBlank { null } ?: "—"

            return MetricHeaderPanel(
                listOf(
                    dateTimeMetric(meta),
                    Metric("Total Requests", (meta?.totalRequests ?: 0).toString()),
                    Metric("Peak RPS", "%.1f r/s".format(meta?.peakRps ?: 0.0)),
                    Metric("Profile", profileText),
                    Metric("Config Hash", truncateMiddle(configHash), configHash),
                    Metric("Sampling", samplingSummary(meta))
                )
            )
        }

        fun show(project: Project, snap: SnapModel) {
            SnapViewDialog(project, snap).show()
        }
    }
}
