package dev.gopherglide.ggplugin.mcp

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.mcp.DaemonTestSupport.annotations
import dev.gopherglide.ggplugin.mcp.DaemonTestSupport.labels
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Drives a real `gg mcp` daemon; skipped unless GG_V15 and SPIKE_DIR are set (GG_V13 for the fallback test).
 * SPIKE_DIR must hold a `test.http` that a server answers.
 */
class GgMcpDaemonLiveTest : BasePlatformTestCase() {
    private val gg get() = System.getenv("GG_V15")
    private val work get() = System.getenv("SPIKE_DIR")
    private val old get() = System.getenv("GG_V13")

    private fun enabled() = gg != null && work != null

    private lateinit var snapDir: File
    private lateinit var panel: GopherGlideRunPanel
    private val service get() = GgMcpDaemonService.getInstance(project)
    private var savedBinary = ""
    private var savedSnapDir = ""
    private lateinit var launcherDir: File
    private lateinit var launcher: dev.gopherglide.ggplugin.ai.LauncherFiles

    private val host = object : BridgeHost {
        override fun panelIfFree(): GopherGlideRunPanel? = if (panel.isRunning()) null else panel
        override fun runEnded() {}
        override fun agentRunQueued() {}
        override fun edt(action: () -> Unit) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { action() }
        }
    }

    override fun setUp() {
        super.setUp()
        val settings = GopherGlideSettings.instance
        savedBinary = settings.customBinaryPath
        savedSnapDir = settings.customSnapshotsDir
        snapDir = Files.createTempDirectory("gg-live-snaps").toFile()
        panel = GopherGlideRunPanel()
        if (enabled()) {
            settings.customBinaryPath = gg!!
            settings.customSnapshotsDir = snapDir.path
            service.workDirectory = work
            service.bridgeHost = host
            launcherDir = Files.createTempDirectory("gg-launcher").toFile()
            launcher = dev.gopherglide.ggplugin.ai.LauncherFiles(launcherDir.toPath(), windows = false).also { it.install(gg!!) }
            service.launcher = launcher
        }
    }

    override fun tearDown() {
        try {
            service.stop()
            GopherGlideSettings.instance.customBinaryPath = savedBinary
            GopherGlideSettings.instance.customSnapshotsDir = savedSnapDir
            snapDir.deleteRecursively()
            if (::launcherDir.isInitialized) launcherDir.deleteRecursively()
            dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings.getInstance(project).aiLinked = false
            GopherGlideSettings.instance.startDaemonWithProject = false
        } finally {
            super.tearDown()
        }
    }

    private fun waitUntil(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; labels=${labels(panel)}; annotations=${annotations(panel)}" }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(25)
        }
    }

    private fun startDaemon(): DaemonInfo = service.ensureRunning().get(30, java.util.concurrent.TimeUnit.SECONDS)

    private fun userRunArgs(seconds: Int = 30, snap: Boolean = false) =
        listOf("--profile", "smoke", "--http-file", "$work/test.http", "--duration", "${seconds}s") + if (snap) listOf("--snap", "--snap-tag", "live") else emptyList()

    private fun startUserRun(seconds: Int = 30, snap: Boolean = false) {
        assertTrue(DaemonRouting.tryRun(project, userRunArgs(seconds, snap), panel) { fail("fell back to a direct run") })
        waitUntil("run started") { labels(panel).any { it.startsWith("● RUNNING") } }
    }

    private fun status() = labels(panel).firstOrNull { it.startsWith("●") || it.startsWith("■") || it.startsWith("✓") || it.startsWith("✗") }.orEmpty()

    fun testTheDaemonStartsInTheProjectFolderAndStopsWhenAsked() {
        if (!enabled()) return
        val info = startDaemon()
        assertEquals(GgMcpDaemonService.State.RUNNING, service.state)
        assertTrue(info.baseUrl.startsWith("http://127.0.0.1:"))
        val answer = PluginHttp(info).post("/plugin/control", """{"command":"stop","id":"x"}""")
        assertTrue(answer.body, answer.body.contains(""""event":"error""""))
        service.stop()
        waitUntil("daemon to stop", 15_000) { service.state == GgMcpDaemonService.State.STOPPED }
        try {
            PluginHttp(info).post("/plugin/control", "{}")
            fail("expected the daemon to be gone")
        } catch (e: IOException) {
            // gone
        }
    }

    fun testAWrongTokenIsRefused() {
        if (!enabled()) return
        val info = startDaemon()
        val answer = PluginHttp(info.copy(token = "not-the-token-0123456789")).post("/plugin/control", "{}")
        assertEquals(401, answer.status)
    }

    fun testARunThroughTheDaemonFinishesAndSavesItsSnapshotWhereSettingsSay() {
        if (!enabled()) return
        startDaemon()
        startUserRun(seconds = 4, snap = true)
        waitUntil("run to finish") { status().startsWith("✓ FINISHED") }
        waitUntil("snapshot") { snapDir.listFiles { f -> f.extension == "snap" }.orEmpty().isNotEmpty() }
        assertTrue(snapDir.listFiles()!!.single().name.startsWith("live-"))
    }

    fun testSteeringAndStoppingTheUsersRunWorksThroughTheDaemon() {
        if (!enabled()) return
        startDaemon()
        startUserRun()
        val plus = UIUtil.findComponentsOfType(panel, javax.swing.JButton::class.java).first { it.text == "+5" }
        waitUntil("strip enabled") { plus.isEnabled }
        plus.doClick()
        waitUntil("bias badge") { labels(panel).contains("Bias +5 RPS") }
        panel.stop()
        waitUntil("run to stop") { !panel.isRunning() }
        assertTrue(status(), status().startsWith("■ STOPPED"))
    }

    fun testAnAgentsRunIsAdoptedAndItsSteersShowUp() {
        if (!enabled()) return
        val info = startDaemon()
        val agent = McpTestClient(info)
        val started = agent.json(
            "start_simulation",
            """{"http_path":"$work/test.http","profile":"smoke","duration":"30s","peak_rps":10,"snap":true,"snap_tag":"agentlive"}"""
        )
        val sid = started["session_id"].asString
        waitUntil("adoption") { labels(panel).contains("🤖 STARTED BY AI AGENT") }
        // gg refuses a steer until its first telemetry reading, so an agent retries; so does this.
        fun steer(arguments: String) = waitUntil("steer $arguments") {
            runCatching { com.google.gson.JsonParser.parseString(agent.call("steer_traffic", arguments)).asJsonObject["applied"].asBoolean }.getOrDefault(false)
        }
        steer("""{"session_id":"$sid","action":"mark","label":"agent-mark"}""")
        steer("""{"session_id":"$sid","action":"adjust_rps","delta":5}""")
        steer("""{"session_id":"$sid","action":"hold_rps","target_rps":20}""")
        waitUntil("agent steers") {
            val notes = annotations(panel)
            notes.any { it is RunAnnotation.Mark && it.source == AnnotationSource.AGENT } &&
                notes.any { it is RunAnnotation.BiasChange && it.source == AnnotationSource.AGENT } &&
                notes.any { it is RunAnnotation.HoldChange && it.rps == 20 && it.source == AnnotationSource.AGENT }
        }
        assertTrue(labels(panel).any { it.startsWith("Last mark: “🤖 agent-mark”") })
        panel.stop()
        waitUntil("run to stop") { !panel.isRunning() }
        waitUntil("snapshot") { snapDir.listFiles { f -> f.extension == "snap" }.orEmpty().isNotEmpty() }
    }

    fun testAnAgentCannotSteerTheUsersRunUntilTheUserAllowsIt() {
        if (!enabled()) return
        val info = startDaemon()
        startUserRun()
        val agent = McpTestClient(info)
        assertTrue(agent.call("steer_traffic", """{"action":"mark","label":"nope"}""").contains("user_session_locked"))
        val box = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { it.text == "🤖 Allow AI Steer" }
        waitUntil("box enabled") { box.isEnabled }
        box.doClick()
        waitUntil("agent allowed") { !agent.call("steer_traffic", """{"action":"mark","label":"allowed"}""").contains("user_session_locked") }
        waitUntil("agent mark on the user's chart") { annotations(panel).any { it is RunAnnotation.Mark && it.label == "allowed" && it.source == AnnotationSource.AGENT } }
        box.doClick()
        waitUntil("agent locked out again") { agent.call("steer_traffic", """{"action":"mark","label":"again"}""").contains("user_session_locked") }
        panel.stop()
        waitUntil("run to stop") { !panel.isRunning() }
    }

    fun testAKilledDaemonEndsTheRunInThePanelWithAReason() {
        if (!enabled()) return
        val info = startDaemon()
        startUserRun()
        ProcessHandle.of(info.pid).get().destroyForcibly()
        waitUntil("the run to be marked failed", 20_000) { status().startsWith("✗") }
        assertFalse(panel.isRunning())
        assertEquals(GgMcpDaemonService.State.FAILED, service.state)
        assertNotNull(service.lastError)
    }

    fun testChangingTheSnapshotFolderRestartsAnIdleDaemonAndRunsUseIt() {
        if (!enabled()) return
        val first = startDaemon()
        val other = Files.createTempDirectory("gg-live-snaps-2").toFile()
        try {
            GopherGlideSettings.instance.customSnapshotsDir = other.path
            val second = startDaemon()
            assertTrue(second.pid != first.pid)
            startUserRun(seconds = 3, snap = true)
            waitUntil("run to finish") { status().startsWith("✓ FINISHED") }
            waitUntil("snapshot in the new folder") { other.listFiles { f -> f.extension == "snap" }.orEmpty().isNotEmpty() }
            assertTrue(snapDir.listFiles().orEmpty().isEmpty())
        } finally {
            other.deleteRecursively()
        }
    }

    fun testSameSettingsKeepTheRunningDaemon() {
        if (!enabled()) return
        val first = startDaemon()
        assertEquals(first.pid, startDaemon().pid)
    }

    fun testAnOlderGgFallsBackToADirectRun() {
        val binary = old ?: return
        if (work == null) return
        GopherGlideSettings.instance.customBinaryPath = binary
        service.workDirectory = work
        var fellBack = false
        assertTrue(DaemonRouting.tryRun(project, userRunArgs(), panel) { fellBack = true })
        waitUntil("fallback") { fellBack }
        assertEquals(GgMcpDaemonService.State.FAILED, service.state)
        assertTrue(service.lastError!!.contains("needs gg"))
    }

    fun testClosingTheProjectLeavesNoDaemonBehind() {
        if (!enabled()) return
        val info = startDaemon()
        val process = ProcessHandle.of(info.pid).get()
        assertTrue(process.isAlive)
        service.dispose()
        val deadline = System.currentTimeMillis() + 10_000
        while (process.isAlive && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertFalse("the daemon outlived its project", process.isAlive)
    }

    private fun infoFile() = File(launcherDir, "daemons/" + dev.gopherglide.ggplugin.ai.LauncherFiles.key(File(work!!).canonicalPath) + ".txt")

    fun testTheRunningDaemonIsPublishedToTheLauncherAndWithdrawnWhenItStops() {
        if (!enabled()) return
        val info = startDaemon()
        waitUntil("published") { infoFile().exists() }
        assertEquals(
            listOf("url=${info.baseUrl}/mcp", "token=${info.token}", "pid=${info.pid}"),
            infoFile().readLines()
        )
        service.stop()
        waitUntil("withdrawn", 15_000) { !infoFile().exists() }
    }

    fun testALauncherInstalledAfterTheDaemonStartedStillGetsPublished() {
        if (!enabled()) return
        launcher.script.toFile().delete()
        startDaemon()
        assertFalse(infoFile().exists())
        launcher.install(gg!!)
        service.publishToLauncher()
        assertTrue(infoFile().exists())
    }

    fun testAKilledDaemonLeavesNoStaleLauncherFile() {
        if (!enabled()) return
        val info = startDaemon()
        waitUntil("published") { infoFile().exists() }
        ProcessHandle.of(info.pid).get().destroyForcibly()
        waitUntil("withdrawn", 20_000) { !infoFile().exists() }
    }

    fun testTheRunTabButtonSaysReadyOnceAgentsAreConnectedAndTheDaemonRuns() {
        if (!enabled()) return
        dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings.getInstance(project).aiLinked = true
        startDaemon()
        assertEquals("AI Agents: Ready", dev.gopherglide.ggplugin.ai.AiButton.look(true, service.state).text)
    }

    fun testAProjectWithAgentsConnectedStartsItsDaemonWhenItOpens() {
        if (!enabled()) return
        dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings.getInstance(project).aiLinked = true
        dev.gopherglide.ggplugin.ai.AiStartup.onProjectOpened(project)
        waitUntil("daemon to start") { service.state == GgMcpDaemonService.State.RUNNING }
    }

    fun testAProjectWithoutAgentsDoesNotStartItsDaemonUnlessTheSettingSaysSo() {
        if (!enabled()) return
        dev.gopherglide.ggplugin.ai.AiStartup.onProjectOpened(project)
        Thread.sleep(1_000)
        assertEquals(GgMcpDaemonService.State.STOPPED, service.state)
        GopherGlideSettings.instance.startDaemonWithProject = true
        dev.gopherglide.ggplugin.ai.AiStartup.onProjectOpened(project)
        waitUntil("daemon to start") { service.state == GgMcpDaemonService.State.RUNNING }
    }

    /** Speaks MCP to a stdio server the way a client such as Claude Code does: one JSON message per line. */
    private class StdioMcp(command: List<String>, cwd: File) : AutoCloseable {
        private val process = ProcessBuilder(command).directory(cwd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        private val out = process.outputStream.bufferedWriter()
        private val input = process.inputStream.bufferedReader()
        private var next = 1

        init {
            request("initialize", """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"0"}}""")
            send("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        }

        fun call(tool: String, arguments: String): String {
            val reply = request("tools/call", """{"name":"$tool","arguments":$arguments}""")
            return com.google.gson.JsonParser.parseString(reply).asJsonObject["result"].asJsonObject["content"].asJsonArray[0].asJsonObject["text"].asString
        }

        private fun request(method: String, params: String): String {
            val id = next++
            send("""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}""")
            while (true) {
                val line = input.readLine() ?: error("the server closed its output")
                val json = runCatching { com.google.gson.JsonParser.parseString(line).asJsonObject }.getOrNull() ?: continue
                if (json["id"]?.asInt == id) return line
            }
        }

        private fun send(line: String) {
            out.write(line)
            out.newLine()
            out.flush()
        }

        override fun close() {
            runCatching { out.close() }
            process.destroy()
        }
    }

    fun testTheMcpFileThePluginWritesAttachesAnAgentToTheDaemonSoItsRunShowsInThePanel() {
        if (!enabled()) return
        val projectDir = Files.createTempDirectory("gg-e2e-project").toFile().canonicalFile
        try {
            service.workDirectory = projectDir.path
            val target = dev.gopherglide.ggplugin.ai.AiTarget(projectDir.toPath(), gg!!, gg!!)
            val configurator = dev.gopherglide.ggplugin.ai.AiAgentConfigurator(
                dev.gopherglide.ggplugin.ai.GgRunner { args ->
                    val p = ProcessBuilder(listOf(gg!!) + args).start()
                    val stdout = p.inputStream.bufferedReader().readText()
                    val stderr = p.errorStream.bufferedReader().readText()
                    p.waitFor()
                    dev.gopherglide.ggplugin.ai.GgRunner.Output(p.exitValue(), stdout, stderr)
                },
                launcher, File(projectDir, "acp.json").toPath()
            )
            val applied = configurator.apply(target, dev.gopherglide.ggplugin.ai.AiRequest(mcp = true, skill = false, acp = false))
            assertTrue(applied.steps.toString(), applied.connected)

            startDaemon()
            val entry = dev.gopherglide.ggplugin.ai.McpConfig.inspect(File(projectDir, ".mcp.json").readText()).entry!!
            StdioMcp(listOf(entry.command) + entry.args, projectDir).use { agent ->
                val started = com.google.gson.JsonParser.parseString(
                    agent.call("start_simulation", """{"http_path":"$work/test.http","profile":"smoke","duration":"30s","peak_rps":10}""")
                ).asJsonObject
                waitUntil("the agent's run to show in the panel") { labels(panel).contains("🤖 STARTED BY AI AGENT") }
                agent.call("stop_simulation", """{"session_id":"${started["session_id"].asString}"}""")
                waitUntil("the run to end") { !panel.isRunning() }
            }
        } finally {
            projectDir.deleteRecursively()
        }
    }

    fun testAnMcpFileLeftAtGgsDefaultRunsInTheAgentsOwnProcessAndIsInvisibleHere() {
        if (!enabled()) return
        val projectDir = Files.createTempDirectory("gg-e2e-default").toFile().canonicalFile
        try {
            service.workDirectory = projectDir.path
            File(projectDir, ".mcp.json").writeText("""{"mcpServers":{"gg":{"command":"$gg","args":["mcp"]}}}""")
            startDaemon()
            StdioMcp(listOf(gg!!, "mcp"), projectDir).use { agent ->
                agent.call("start_simulation", """{"http_path":"$work/test.http","profile":"smoke","duration":"3s","peak_rps":10}""")
                Thread.sleep(1_500)
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                assertFalse(panel.isRunning())
            }
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
