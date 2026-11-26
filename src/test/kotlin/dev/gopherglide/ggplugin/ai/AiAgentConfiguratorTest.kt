package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs the real `gg mcp init` (GG_V15 must name a gg v1.5.0+); skipped otherwise. */
class AiAgentConfiguratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ggBinary: String? get() = System.getenv("GG_V15")

    private val scratchDirs = mutableListOf<Path>()
    private lateinit var project: Path
    private lateinit var acpFile: Path
    private lateinit var launcher: LauncherFiles

    private fun setUp(): AiAgentConfigurator {
        project = tmp.newFolder("project").toPath()
        acpFile = tmp.root.toPath().resolve("jb/acp.json")
        launcher = LauncherFiles(tmp.root.toPath().resolve("gg-home"), windows = false)
        return AiAgentConfigurator(realGg(), launcher, acpFile) { Files.createTempDirectory("gg-scratch").also { scratchDirs.add(it) } }
    }

    private fun realGg() = GgRunner { args ->
        val process = ProcessBuilder(listOf(ggBinary!!) + args).start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS))
        GgRunner.Output(process.exitValue(), out, err)
    }

    private fun target(command: String = "gg") = AiTarget(project, command, "/opt/real/gg")
    private fun mcpFile() = project.resolve(".mcp.json")
    private fun mcpStatus() = McpConfig.inspect(Files.readString(mcpFile()))
    private fun step(result: ApplyResult, what: String) = result.steps.single { it.what == what }

    private val mcpOnly = AiRequest(mcp = true, skill = false, acp = false)
    private val acpOnly = AiRequest(mcp = false, skill = false, acp = true)

    @Test
    fun aFreshProjectGetsAnAttachingEntry() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        val result = configurator.apply(target(), mcpOnly)
        assertEquals(Outcome.DONE, step(result, AiAgentConfigurator.MCP).outcome)
        assertEquals(McpHealth.LINKED, mcpStatus().health)
        assertEquals("gg", mcpStatus().entry!!.command)
        assertTrue(result.connected)
    }

    @Test
    fun applyingAgainChangesNothing() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target(), mcpOnly)
        val before = Files.readString(mcpFile())
        val again = configurator.apply(target(), mcpOnly)
        assertEquals(Outcome.UNCHANGED, step(again, AiAgentConfigurator.MCP).outcome)
        assertEquals(before, Files.readString(mcpFile()))
    }

    @Test
    fun ggsOwnDefaultEntryIsUpgradedInPlaceAndOtherServersSurvive() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.writeString(mcpFile(), """{"mcpServers":{"other":{"command":"x","args":["a"]},"gg":{"command":"gg","args":["mcp"]}}}""")
        val result = configurator.apply(target(), mcpOnly)
        assertEquals(Outcome.DONE, step(result, AiAgentConfigurator.MCP).outcome)
        assertEquals(McpHealth.LINKED, mcpStatus().health)
        assertEquals(listOf("other"), mcpStatus().otherServers)
    }

    @Test
    fun anEntryOfYourOwnIsKeptUntilYouAskToReplaceIt() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        val own = """{"mcpServers":{"other":{"command":"x"},"gg":{"command":"/dev/gg","args":["mcp","--port","0"]}}}"""
        Files.writeString(mcpFile(), own)
        val kept = configurator.apply(target(), mcpOnly)
        assertEquals(Outcome.SKIPPED, step(kept, AiAgentConfigurator.MCP).outcome)
        assertTrue(step(kept, AiAgentConfigurator.MCP).detail!!.contains("/dev/gg mcp --port 0"))
        assertEquals(own, Files.readString(mcpFile()))
        assertFalse(kept.connected)

        val replaced = configurator.apply(target(), mcpOnly.copy(replaceMcp = true))
        assertEquals(Outcome.DONE, step(replaced, AiAgentConfigurator.MCP).outcome)
        assertEquals(McpHealth.LINKED, mcpStatus().health)
        assertEquals("gg", mcpStatus().entry!!.command)
        assertEquals(listOf("other"), mcpStatus().otherServers)
    }

    @Test
    fun aBrokenFileIsReportedAndLeftAlone() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.writeString(mcpFile(), "{ nope")
        val result = configurator.apply(target(), mcpOnly)
        assertEquals(Outcome.FAILED, step(result, AiAgentConfigurator.MCP).outcome)
        assertEquals("{ nope", Files.readString(mcpFile()))
        assertTrue(result.failed)
    }

    @Test
    fun theSkillIsInstalledOnceAndNeedsTheLink() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        val skillOnly = configurator.apply(target(), AiRequest(mcp = false, skill = true, acp = false))
        assertEquals(Outcome.SKIPPED, step(skillOnly, AiAgentConfigurator.SKILL).outcome)
        assertFalse(Files.exists(mcpFile()))
        assertFalse(Files.exists(target().skillFile))

        val both = configurator.apply(target(), AiRequest(mcp = true, skill = true, acp = false))
        assertEquals(Outcome.DONE, step(both, AiAgentConfigurator.SKILL).outcome)
        assertTrue(Files.exists(target().skillFile))
        assertEquals(McpHealth.LINKED, mcpStatus().health)

        val again = configurator.apply(target(), AiRequest(mcp = false, skill = true, acp = false))
        assertEquals(Outcome.UNCHANGED, step(again, AiAgentConfigurator.SKILL).outcome)
    }

    @Test
    fun anEditedSkillIsNeverOverwritten() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target(), AiRequest(mcp = true, skill = true, acp = false))
        Files.writeString(target().skillFile, "my own notes")
        val again = configurator.apply(target(), AiRequest(mcp = true, skill = true, acp = false, replaceMcp = true))
        assertEquals(Outcome.SKIPPED, step(again, AiAgentConfigurator.SKILL).outcome)
        assertEquals("my own notes", Files.readString(target().skillFile))
    }

    @Test
    fun registeringAirAddsOurAgentWithoutDisturbingTheRestOfTheFile() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.createDirectories(acpFile.parent)
        val original = """{"default_mcp_settings":{}, "agent_servers": {"Claude": {"command":"claude-acp","args":["--x"]}}}"""
        Files.writeString(acpFile, original)
        val result = configurator.apply(target(), acpOnly)
        assertEquals(Outcome.DONE, step(result, AiAgentConfigurator.ACP).outcome)
        val after = Files.readString(acpFile)
        assertTrue("hand formatting kept: $after", after.startsWith("""{"default_mcp_settings":{}, "agent_servers": {"Claude": {"command":"claude-acp","args":["--x"]}"""))
        val status = AcpConfig.inspect(after, launcher.script.toString())
        assertEquals(AcpHealth.REGISTERED, status.health)
        assertEquals(listOf("Claude"), status.otherAgents)
        assertTrue(launcher.isInstalled())
        assertTrue(Files.readString(launcher.script).contains("/opt/real/gg"))
    }

    @Test
    fun registeringAirNeverCreatesAnMcpFileInTheProjectAndCleansUpItsScratchFolder() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target(), acpOnly)
        assertFalse(Files.exists(mcpFile()))
        assertTrue(scratchDirs.isNotEmpty())
        assertTrue(scratchDirs.none { Files.exists(it) })
    }

    @Test
    fun aMissingAcpFileIsCreatedAndAnExistingRegistrationIsLeft() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        assertEquals(Outcome.DONE, step(configurator.apply(target(), acpOnly), AiAgentConfigurator.ACP).outcome)
        assertTrue(Files.exists(acpFile))
        val before = Files.readString(acpFile)
        assertEquals(Outcome.UNCHANGED, step(configurator.apply(target(), acpOnly), AiAgentConfigurator.ACP).outcome)
        assertEquals(before, Files.readString(acpFile))
    }

    @Test
    fun aDifferingAirEntryIsKeptUntilYouAskToReplaceIt() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.createDirectories(acpFile.parent)
        val original = """{"agent_servers": {"gg autopilot": {"command":"/dev/gg","args":["autopilot","--acp"],"use_custom_mcp":true}}}"""
        Files.writeString(acpFile, original)
        val kept = configurator.apply(target(), acpOnly)
        assertEquals(Outcome.SKIPPED, step(kept, AiAgentConfigurator.ACP).outcome)
        assertEquals(original, Files.readString(acpFile))
        val replaced = configurator.apply(target(), acpOnly.copy(replaceAcp = true))
        assertEquals(Outcome.DONE, step(replaced, AiAgentConfigurator.ACP).outcome)
        assertEquals(AcpHealth.REGISTERED, AcpConfig.inspect(Files.readString(acpFile), launcher.script.toString()).health)
    }

    @Test
    fun aBrokenAcpFileIsReportedWithNothingWrittenAndNoLauncherInstalled() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.createDirectories(acpFile.parent)
        Files.writeString(acpFile, "{ x")
        val result = configurator.apply(target(), acpOnly)
        assertEquals(Outcome.FAILED, step(result, AiAgentConfigurator.ACP).outcome)
        assertEquals("{ x", Files.readString(acpFile))
        assertFalse(launcher.isInstalled())
    }

    @Test
    fun theLanguageModelSettingsGoToTheLauncherEnvNotToAcpJson() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target(), acpOnly.copy(agentEnv = mapOf("GEMINI_API_KEY" to "secret-key", "GG_AUTOPILOT_MODEL" to "m")))
        assertEquals("secret-key", launcher.readAgentEnv()["GEMINI_API_KEY"])
        assertFalse(Files.readString(acpFile).contains("secret-key"))
        configurator.apply(target(), acpOnly.copy(agentEnv = emptyMap()))
        assertFalse(Files.exists(launcher.envFile))
    }

    @Test
    fun theMcpFileIsKeptOutOfGitOnceAndOnlyWhenItHoldsAPath() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        Files.createDirectories(project.resolve(".git/info"))
        Files.writeString(project.resolve(".git/info/exclude"), "# comment")
        val request = mcpOnly.copy(excludeFromGit = true)
        val first = configurator.apply(target("/opt/gg"), request)
        assertEquals(Outcome.DONE, step(first, AiAgentConfigurator.GIT).outcome)
        assertEquals("# comment\n.mcp.json\n", Files.readString(project.resolve(".git/info/exclude")))
        assertEquals(Outcome.UNCHANGED, step(configurator.apply(target("/opt/gg"), request), AiAgentConfigurator.GIT).outcome)
        assertTrue(configurator.apply(target("gg"), request).steps.none { it.what == AiAgentConfigurator.GIT })
    }

    @Test
    fun withoutAGitCheckoutTheExcludeIsSkipped() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        val result = configurator.apply(target("/opt/gg"), mcpOnly.copy(excludeFromGit = true))
        assertEquals(Outcome.SKIPPED, step(result, AiAgentConfigurator.GIT).outcome)
    }

    @Test
    fun theMcpCommandAsGivenIsWhatGetsWritten() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target("/opt/my gg/gg"), mcpOnly)
        assertEquals("/opt/my gg/gg", mcpStatus().entry!!.command)
    }

    @Test
    fun thePreviewShowsWhatWouldBeWrittenWithoutWritingIt() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        val text = configurator.preview(target(), AiRequest(mcp = true, skill = true, acp = true))
        assertTrue(text, text.contains("would-created"))
        assertTrue(text, text.contains("mcp, --attach") || text.contains("[mcp, --attach]"))
        assertTrue(text, text.contains(launcher.script.toString()))
        assertFalse(Files.exists(mcpFile()))
        assertFalse(Files.exists(acpFile))
        assertFalse(launcher.isInstalled())
    }

    @Test
    fun theTextForALinkedProjectSaysSo() {
        assumeTrue(ggBinary != null)
        val configurator = setUp()
        configurator.apply(target(), mcpOnly)
        assertTrue(configurator.preview(target(), mcpOnly).contains("Already attaches"))
        assertEquals("Nothing selected.", configurator.preview(target(), AiRequest(mcp = false, skill = false, acp = false)))
    }

    @Test
    fun nothingSelectedDoesNothing() {
        val configurator = AiAgentConfigurator(GgRunner { error("gg must not be called") }, LauncherFiles(tmp.root.toPath().resolve("h"), windows = false), tmp.root.toPath().resolve("a.json"))
        project = tmp.root.toPath()
        assertEquals(emptyList<StepResult>(), configurator.apply(target(), AiRequest(mcp = false, skill = false, acp = false)).steps)
    }
}
