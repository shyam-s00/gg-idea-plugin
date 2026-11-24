package dev.gopherglide.ggplugin.snap

import dev.gopherglide.ggplugin.snap.SnapReport.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapReportTest {
    @Test
    fun theFormatFollowsTheFileExtension() {
        assertEquals(Format.HTML, SnapReport.formatFor("run.html"))
        assertEquals(Format.HTML, SnapReport.formatFor("RUN.HTM"))
        assertEquals(Format.MARKDOWN, SnapReport.formatFor("run.md"))
        assertEquals(Format.MARKDOWN, SnapReport.formatFor("run"))
        assertEquals(Format.MARKDOWN, SnapReport.formatFor("run.html.txt"))
    }

    @Test
    fun exportNamesTheFormatAndTheOutputFile() {
        assertEquals(
            listOf("snap", "report", "/s/a.snap", "--format", "html", "--out", "/o/report.html"),
            SnapReport.exportArgs("/s/a.snap", "/o/report.html")
        )
        assertEquals(
            listOf("snap", "report", "/s/a.snap", "--format", "md", "--out", "/o/report.md"),
            SnapReport.exportArgs("/s/a.snap", "/o/report.md")
        )
    }

    @Test
    fun copyAsksForMarkdownOnStdout() {
        assertEquals(listOf("snap", "report", "/s/a.snap", "--format", "md"), SnapReport.markdownArgs("/s/a.snap"))
    }

    @Test
    fun theDefaultFileNameUsesTheTagAndIsSafe() {
        assertEquals("ceiling-v2-report.md", SnapReport.defaultFileName(SnapModel(id = "x", meta = SnapMeta(tag = "ceiling-v2"))))
        assertEquals("a_b_c-report.md", SnapReport.defaultFileName(SnapModel(id = "x", meta = SnapMeta(tag = "a/b c"))))
    }

    @Test
    fun anUntaggedSnapshotFallsBackToItsId() {
        assertEquals("20261124-023924-report.md", SnapReport.defaultFileName(SnapModel(id = "20261124-023924", meta = SnapMeta())))
        assertEquals("snapshot-report.md", SnapReport.defaultFileName(SnapModel()))
    }
}
