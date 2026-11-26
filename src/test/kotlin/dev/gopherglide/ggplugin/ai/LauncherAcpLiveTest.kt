package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.mcp.DaemonCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Starts a real `gg mcp` daemon and a real `gg autopilot --acp` through the generated launcher; skipped unless
 * GG_V15 names gg v1.5.0+. The daemon's debug log is the only place that shows whether the agent joined it.
 */
class LauncherAcpLiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gg: String? get() = System.getenv("GG_V15")
    private var daemon: Process? = null
    private val log = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        daemon?.let {
            runCatching { it.outputStream.close() }
            it.destroy()
        }
    }

    private fun startDaemon(workDir: File, token: String): dev.gopherglide.ggplugin.mcp.DaemonInfo {
        val builder = ProcessBuilder(listOf(gg!!, "mcp", "--port", "0", "--exit-on-stdin-eof", "--log-level", "debug"))
            .directory(workDir)
        builder.environment()["GG_MCP_TOKEN"] = token
        val process = builder.start().also { daemon = it }
        Thread { process.errorStream.bufferedReader().forEachLine { log += it } }.apply { isDaemon = true }.start()
        val line = process.inputStream.bufferedReader().readLine()
        return checkNotNull(DaemonCommand.parseListening(line, token)) { "no listening line: $line" }.copy(pid = process.pid())
    }

    private fun handshake(launcher: LauncherFiles, cwd: File) {
        val builder = ProcessBuilder(listOf("/bin/sh", launcher.script.toString(), "autopilot", "--acp")).directory(cwd)
        builder.environment().remove("PWD")
        val process = builder.start()
        process.outputStream.use { stdin ->
            stdin.write("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1,"clientCapabilities":{}}}""".plus("\n").toByteArray())
            stdin.flush()
            Thread.sleep(800)
            stdin.write("""{"jsonrpc":"2.0","id":2,"method":"session/new","params":{"cwd":"${cwd.canonicalPath}","mcpServers":[]}}""".plus("\n").toByteArray())
            stdin.flush()
            Thread.sleep(2_500)
        }
        val answers = process.inputStream.bufferedReader().readText()
        process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
        assertTrue(answers, answers.contains("\"sessionId\""))
    }

    private fun joined() = log.count { it.contains("session initialized") }

    @Test
    fun theAgentJoinsTheDaemonOfItsFolderWhenTheLauncherKnowsIt() {
        assumeTrue(gg != null)
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val project = tmp.newFolder("proj")
        val home = tmp.newFolder("home")
        val launcher = LauncherFiles(home.toPath(), windows = false).also { it.install(gg!!) }
        val info = startDaemon(project, "live-token-0123456789abcdef")
        Thread.sleep(500)

        handshake(launcher, project)
        assertEquals("without daemon info the agent runs on its own", 0, joined())

        launcher.publishDaemon(project.canonicalPath, info)
        handshake(launcher, project)
        assertEquals("the agent joined the daemon", 1, joined())
    }

    @Test
    fun aDaemonOfAnotherFolderIsNotJoined() {
        assumeTrue(gg != null)
        assumeFalse(System.getProperty("os.name").lowercase().contains("win"))
        val project = tmp.newFolder("proj")
        val other = tmp.newFolder("other")
        val launcher = LauncherFiles(tmp.newFolder("home").toPath(), windows = false).also { it.install(gg!!) }
        val info = startDaemon(project, "live-token-0123456789abcdef")
        launcher.publishDaemon(project.canonicalPath, info)
        Thread.sleep(500)
        handshake(launcher, other)
        assertFalse(joined() > 0)
    }
}
