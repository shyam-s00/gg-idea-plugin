package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.mcp.DaemonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

class LauncherFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val info = DaemonInfo("http://127.0.0.1:5555", "super-secret-token-0123456789", ProcessHandle.current().pid())

    private fun launcher() = LauncherFiles(tmp.root.toPath().resolve("gg-home"), windows = false)

    @Test
    fun folderNamesBecomeSafeFileNames() {
        assertEquals("_Users_me_My_Project", LauncherFiles.key("/Users/me/My Project"))
        assertEquals("C__Users_me_proj", LauncherFiles.key("C:\\Users\\me\\proj"))
    }

    @Test
    fun theScriptNeverContainsTheToken() {
        val files = launcher()
        files.install("/opt/gg")
        files.publishDaemon("/work/proj", info)
        assertFalse(Files.readString(files.script).contains(info.token))
    }

    @Test
    fun theScriptRunsTheGivenBinaryAndIsExecutable() {
        val files = launcher()
        assertTrue(files.install("/opt/it's gg"))
        val text = Files.readString(files.script)
        assertTrue(text, text.contains("""GG='/opt/it'\''s gg'"""))
        assertTrue(Files.isExecutable(files.script))
    }

    @Test
    fun installingTheSameBinaryAgainChangesNothingAndANewOneRewritesIt() {
        val files = launcher()
        assertTrue(files.install("/opt/a"))
        assertFalse(files.install("/opt/a"))
        assertTrue(files.install("/opt/b"))
        assertTrue(Files.readString(files.script).contains("'/opt/b'"))
    }

    @Test
    fun theWindowsScriptNamesTheSameThings() {
        val text = LauncherFiles.cmdScript("C:\\gg\\gg.exe", "C:\\Users\\me\\.jetbrains\\gopher-glide")
        assertTrue(text.contains("""set "GG=C:\gg\gg.exe""""))
        assertTrue(text.contains("daemons"))
        assertTrue(text.contains("""tasklist /FI "PID eq !P_PID!""""))
        assertTrue(text.contains(""""%GG%" %*"""))
        assertFalse(text.contains("token="))
    }

    @Test
    fun aDaemonIsPublishedOnlyWhereTheLauncherIsInstalled() {
        val files = launcher()
        files.publishDaemon("/work/proj", info)
        assertFalse(Files.exists(files.dir.resolve("daemons")))
        files.install("/opt/gg")
        files.publishDaemon("/work/proj", info, idePid = 4242)
        val lines = Files.readAllLines(files.dir.resolve("daemons/_work_proj.txt"))
        assertEquals(listOf("url=http://127.0.0.1:5555/mcp", "token=super-secret-token-0123456789", "pid=${info.pid}", "ide=4242"), lines)
    }

    @Test
    fun theDaemonFileIsPrivateToTheUser() {
        assumeFalse(windows)
        val files = launcher().also { it.install("/opt/gg") }
        files.publishDaemon("/work/proj", info)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(files.dir.resolve("daemons/_work_proj.txt"))))
    }

    @Test
    fun unpublishingRemovesTheFile() {
        val files = launcher().also { it.install("/opt/gg") }
        files.publishDaemon("/work/proj", info)
        files.unpublishDaemon("/work/proj")
        assertFalse(Files.exists(files.dir.resolve("daemons/_work_proj.txt")))
        files.unpublishDaemon("/work/proj")
    }

    @Test
    fun aFolderReachedThroughALinkIsPublishedUnderBothNames() {
        assumeFalse(windows)
        val real = tmp.newFolder("real")
        val link = tmp.root.toPath().resolve("link")
        Files.createSymbolicLink(link, real.toPath())
        val files = launcher().also { it.install("/opt/gg") }
        files.publishDaemon(link.toString(), info)
        val names = Files.list(files.dir.resolve("daemons")).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(2, names.size)
        assertTrue(names.contains(LauncherFiles.key(link.toString()) + ".txt"))
        assertTrue(names.contains(LauncherFiles.key(real.toPath().toRealPath().toString()) + ".txt"))
    }

    @Test
    fun onlyKnownSettingsAreKeptInTheAgentEnvAndTheFileIsPrivate() {
        val files = launcher()
        files.writeAgentEnv(mapOf("GEMINI_API_KEY" to "k", "GG_AUTOPILOT_MODEL" to "m", "PATH" to "/evil", "GG_AUTOPILOT_BASE_URL" to " ", "GG_AUTOPILOT_API_KEY" to "a\nb"))
        assertEquals(mapOf("GEMINI_API_KEY" to "k", "GG_AUTOPILOT_MODEL" to "m"), files.readAgentEnv())
        if (!windows) assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(files.envFile)))
    }

    @Test
    fun anEmptyAgentEnvRemovesTheFile() {
        val files = launcher()
        files.writeAgentEnv(mapOf("GEMINI_API_KEY" to "k"))
        files.writeAgentEnv(emptyMap())
        assertFalse(Files.exists(files.envFile))
        assertEquals(emptyMap<String, String>(), files.readAgentEnv())
    }

    @Test
    fun theScriptPassesAnyOtherSubcommandStraightThrough() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        val out = run(files, tmp.root, "version")
        assertEquals("URL= TOKEN= KEY= ARGS=version", out)
    }

    @Test
    fun forAutopilotItPointsGgAtTheLiveDaemonForTheCurrentFolder() {
        assumeFalse(windows)
        val project = tmp.newFolder("proj")
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(project.toString(), info)
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot --acp",
            run(files, project, "autopilot", "--acp")
        )
    }

    @Test
    fun aDeadDaemonIsIgnored() {
        assumeFalse(windows)
        val project = tmp.newFolder("proj2")
        val files = launcher().also { it.install(stub().toString()) }
        val gone = ProcessBuilder("true").start().also { it.waitFor() }.pid()
        files.publishDaemon(project.toString(), info.copy(pid = gone))
        assertEquals("URL= TOKEN= KEY= ARGS=autopilot --acp", run(files, project, "autopilot", "--acp"))
    }

    @Test
    fun aFolderWithNoDaemonUsesTheOnlyLiveOne() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(tmp.newFolder("elsewhere").toString(), info)
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot",
            run(files, tmp.newFolder("here"), "autopilot")
        )
    }

    @Test
    fun aFolderWithItsOwnDaemonPrefersIt() {
        assumeFalse(windows)
        val project = tmp.newFolder("mine")
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(tmp.newFolder("other").toString(), info.copy(baseUrl = "http://127.0.0.1:1111", pid = ProcessHandle.current().pid()))
        files.publishDaemon(project.toString(), info)
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot",
            run(files, project, "autopilot")
        )
    }

    @Test
    fun severalLiveDaemonsAreNotGuessedBetween() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(tmp.newFolder("a").toString(), info)
        files.publishDaemon(tmp.newFolder("b").toString(), info.copy(baseUrl = "http://127.0.0.1:1111", pid = ProcessHandle.current().parent().get().pid()))
        assertEquals("URL= TOKEN= KEY= ARGS=autopilot", run(files, tmp.newFolder("here"), "autopilot"))
    }

    @Test
    fun anAgentStartedOutsideAnyProjectUsesTheDaemonOfTheIdeThatStartedIt() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        val otherIde = ProcessHandle.current().parent().get().pid()
        files.publishDaemon(tmp.newFolder("a").toString(), info.copy(baseUrl = "http://127.0.0.1:1111", pid = otherIde), idePid = otherIde)
        files.publishDaemon(tmp.newFolder("b").toString(), info)
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot",
            run(files, tmp.newFolder("here"), "autopilot")
        )
    }

    @Test
    fun anotherIdesDaemonIsNeverUsed() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(tmp.newFolder("a").toString(), info, idePid = ProcessHandle.current().parent().get().pid())
        assertEquals("URL= TOKEN= KEY= ARGS=autopilot", run(files, tmp.newFolder("here"), "autopilot"))
    }

    @Test
    fun aDaemonFileWithoutAnIdeStillCountsWhenNoDaemonBelongsToThisIde() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(tmp.newFolder("a").toString(), info)
        Files.list(files.dir.resolve("daemons")).use { it.toList() }.forEach { file ->
            Files.writeString(file, Files.readAllLines(file).filterNot { it.startsWith("ide=") }.joinToString("\n", postfix = "\n"))
        }
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot",
            run(files, tmp.newFolder("here"), "autopilot")
        )
    }

    @Test
    fun oneDaemonPublishedUnderTwoNamesCountsOnce() {
        assumeFalse(windows)
        val real = tmp.newFolder("real")
        val link = tmp.root.toPath().resolve("link")
        Files.createSymbolicLink(link, real.toPath())
        val files = launcher().also { it.install(stub().toString()) }
        files.publishDaemon(link.toString(), info)
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot",
            run(files, tmp.newFolder("here"), "autopilot")
        )
    }

    @Test
    fun autopilotWaitsForADaemonTheIdeIsStillStarting() {
        assumeFalse(windows)
        val project = tmp.newFolder("late")
        val files = launcher().also { it.install(stub().toString()) }
        files.markDaemonPending(project.toString())
        val publisher = Thread {
            Thread.sleep(1_500)
            files.publishDaemon(project.toString(), info)
        }.also { it.start() }
        assertEquals(
            "URL=http://127.0.0.1:5555/mcp TOKEN=super-secret-token-0123456789 KEY= ARGS=autopilot --acp",
            run(files, project, "autopilot", "--acp")
        )
        publisher.join()
    }

    @Test
    fun autopilotDoesNotWaitWhenNoLiveIdeIsStartingADaemon() {
        assumeFalse(windows)
        val project = tmp.newFolder("nowait")
        val files = launcher().also { it.install(stub().toString()) }
        val gone = ProcessBuilder("true").start().also { it.waitFor() }.pid()
        files.markDaemonPending(project.toString(), gone)
        val started = System.nanoTime()
        assertEquals("URL= TOKEN= KEY= ARGS=autopilot", run(files, project, "autopilot"))
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5))
    }

    @Test
    fun aClearedPendingMarkerStopsTheWait() {
        val project = tmp.newFolder("cleared")
        val files = launcher().also { it.install("/opt/gg") }
        files.markDaemonPending(project.toString())
        val marker = files.dir.resolve("pending").resolve(LauncherFiles.key(project.toString()) + ".txt")
        assertTrue(Files.exists(marker))
        files.clearDaemonPending(project.toString())
        assertFalse(Files.exists(marker))
    }

    @Test
    fun theLanguageModelSettingsAreExportedForAutopilotOnly() {
        assumeFalse(windows)
        val files = launcher().also { it.install(stub().toString()) }
        files.writeAgentEnv(mapOf("GEMINI_API_KEY" to "key=with=equals", "GG_AUTOPILOT_MODEL" to "gemini-x"))
        assertEquals("URL= TOKEN= KEY=key=with=equals ARGS=autopilot --acp", run(files, tmp.root, "autopilot", "--acp"))
        assertEquals("URL= TOKEN= KEY= ARGS=snap list", run(files, tmp.root, "snap", "list"))
    }

    @Test
    fun aBinaryPathWithSpacesAndQuotesStillRuns() {
        assumeFalse(windows)
        val dir = tmp.newFolder("it's a dir")
        val binary = dir.toPath().resolve("my gg")
        Files.writeString(binary, "#!/bin/sh\necho \"ran:\$*\"\n")
        binary.toFile().setExecutable(true)
        val files = launcher().also { it.install(binary.toString()) }
        assertEquals("ran:hello", run(files, tmp.root, "hello"))
    }

    private fun stub(): java.nio.file.Path {
        val file = tmp.newFile("stub-gg").toPath()
        Files.writeString(file, "#!/bin/sh\necho \"URL=\$GG_MCP_URL TOKEN=\$GG_MCP_TOKEN KEY=\$GEMINI_API_KEY ARGS=\$*\"\n")
        file.toFile().setExecutable(true)
        return file
    }

    private fun run(files: LauncherFiles, cwd: java.io.File, vararg args: String): String {
        val builder = ProcessBuilder(listOf("/bin/sh", files.script.toString()) + args).directory(cwd).redirectErrorStream(true)
        builder.environment().remove("PWD")
        val process = builder.start()
        val text = process.inputStream.bufferedReader().readText().trim()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return text
    }
}
