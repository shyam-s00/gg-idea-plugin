package dev.gopherglide.ggplugin.snap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs the report calls against a real `gg`; skipped unless GG_V15 points at one. */
class SnapReportLiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gg: String? get() = System.getenv("GG_V15")

    private fun snap(name: String): File = tmp.newFile(name).also {
        it.writeBytes(checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).readBytes())
    }

    private class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private fun run(args: List<String>): Result {
        val process = ProcessBuilder(listOf(gg!!) + args).start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        return Result(process.exitValue(), out, err)
    }

    @Test
    fun markdownGoesToStdoutWithoutTheVersionBanner() {
        assumeTrue(gg != null)
        val result = run(SnapReport.markdownArgs(snap("snap-v150-autopilot.snap").path))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout, result.stdout.startsWith("# gg run report:"))
        assertTrue(result.stdout.contains("## Autopilot"))
    }

    @Test
    fun exportWritesMarkdownOrHtmlByExtension() {
        assumeTrue(gg != null)
        val snap = snap("snap-v150-find-ceiling.snap")
        val md = File(tmp.root, "out.md")
        val html = File(tmp.root, "out.html")
        assertEquals(0, run(SnapReport.exportArgs(snap.path, md.path)).exitCode)
        assertEquals(0, run(SnapReport.exportArgs(snap.path, html.path)).exitCode)
        assertTrue(md.readText().startsWith("# gg run report:"))
        assertTrue(html.readText().startsWith("<!doctype html>"))
        assertTrue(md.readText().contains("Ceiling") || md.readText().contains("ceiling"))
    }

    @Test
    fun anOlderSnapshotStillMakesAReport() {
        assumeTrue(gg != null)
        val result = run(SnapReport.markdownArgs(snap("snap-v130-annotated.snap").path))
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.startsWith("# gg run report:"))
    }

    @Test
    fun aMissingSnapshotFailsWithAMessageOnStderr() {
        assumeTrue(gg != null)
        val result = run(SnapReport.markdownArgs(File(tmp.root, "nope.snap").path))
        assertTrue(result.exitCode != 0)
        assertTrue(result.stderr.isNotBlank())
        assertEquals("", result.stdout)
    }
}
