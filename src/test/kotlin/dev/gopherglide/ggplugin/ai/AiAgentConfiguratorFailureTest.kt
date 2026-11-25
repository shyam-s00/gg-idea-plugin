package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/** The configurator against a gg that misbehaves or answers with canned output; no real gg needed. */
class AiAgentConfiguratorFailureTest {
    @get:Rule val tmp = TemporaryFolder()

    private val calls = mutableListOf<List<String>>()
    private val scratch = mutableListOf<Path>()

    private fun configurator(runner: (List<String>) -> GgRunner.Output): AiAgentConfigurator {
        val launcher = LauncherFiles(tmp.root.toPath().resolve("home"), windows = false)
        return AiAgentConfigurator(GgRunner { args -> calls += args; runner(args) }, launcher, tmp.root.toPath().resolve("jb/acp.json")) {
            Files.createTempDirectory("gg-scratch").also { scratch.add(it) }
        }
    }

    private fun target() = AiTarget(tmp.newFolder("p").toPath(), "gg", "/opt/gg")

    private val ok = { text: String -> GgRunner.Output(0, text, "") }

    @Test
    fun aFailingGgIsReportedWithItsOwnMessage() {
        val result = configurator { GgRunner.Output(1, "", "Gopher Glide 1.5.0\ngg mcp init: not valid JSON\n") }.apply(target(), AiRequest(true, false, false))
        val step = result.steps.single()
        assertEquals(Outcome.FAILED, step.outcome)
        assertEquals("gg mcp init: not valid JSON", step.detail)
        assertFalse(result.connected)
    }

    @Test
    fun aFailingGgWithNoMessageStillSaysSomething() {
        val result = configurator { GgRunner.Output(2, "", "") }.apply(target(), AiRequest(true, false, false))
        assertEquals("gg mcp init exited with code 2", result.steps.single().detail)
    }

    @Test
    fun anOutputThatNeverMentionsTheFileIsAFailureNotASuccess() {
        val target = target()
        Files.writeString(target.mcpFile, McpConfig.withAttach("""{"mcpServers":{"gg":{"command":"gg","args":["mcp"]}}}"""))
        val result = configurator { ok("") }.apply(target, AiRequest(false, true, false))
        assertEquals(Outcome.FAILED, result.steps.single().outcome)
    }

    @Test
    fun forceIsOnlyPassedWhenReplacingWasAskedFor() {
        val target = target()
        Files.writeString(target.mcpFile, """{"mcpServers":{"gg":{"command":"/dev/gg","args":["x"]}}}""")
        val c = configurator { ok("skipped        .mcp.json\n               different") }
        c.apply(target, AiRequest(true, false, false))
        assertTrue(calls.isEmpty())
        c.apply(target, AiRequest(true, false, false, replaceMcp = true))
        assertTrue(calls.single().contains("--force"))
    }

    @Test
    fun overwritingTheSkillPassesForceAndLeavesMcpJsonAsItWas() {
        val target = target()
        val mine = McpConfig.withAttach("""{"mcpServers":{"gg":{"command":"gg","args":["mcp"]}}}""")
        Files.writeString(target.mcpFile, mine)
        val c = configurator { args -> if ("--force" in args) Files.writeString(target.mcpFile, "{}"); ok("replaced       ${target.skillFile}") }
        c.apply(target, AiRequest(false, true, false))
        assertFalse(calls.single().contains("--force"))
        calls.clear()
        val result = c.apply(target, AiRequest(false, true, false, replaceSkill = true))
        assertTrue(calls.single().contains("--force"))
        assertEquals(Outcome.DONE, result.steps.single().outcome)
        assertEquals(mine, Files.readString(target.mcpFile))
    }

    @Test
    fun ggIsToldTheCommandToWriteAndTheProjectFolder() {
        val target = target().copy(mcpCommand = "/opt/gg")
        configurator { ok("created        ${target.mcpFile}") }.apply(target, AiRequest(true, false, false))
        val args = calls.single()
        assertEquals(listOf("mcp", "init", "--ide", "jetbrains"), args.take(4))
        assertEquals("/opt/gg", args[args.indexOf("--gg-path") + 1])
        assertEquals(target.projectDir.toString(), args[args.indexOf("--dir") + 1])
    }

    @Test
    fun registeringAirUsesAScratchFolderTheLauncherAndTheAcpFileAndRemovesTheScratch() {
        val result = configurator { ok("created        ${tmp.root}/jb/acp.json") }.apply(target(), AiRequest(false, false, true))
        assertEquals(Outcome.DONE, result.steps.single().outcome)
        val args = calls.single()
        assertTrue(args.contains("--register-acp"))
        assertEquals(scratch.single().toString(), args[args.indexOf("--dir") + 1])
        assertTrue(args[args.indexOf("--gg-path") + 1].endsWith("gg-launcher.sh"))
        assertTrue(scratch.none { Files.exists(it) })
    }

    @Test
    fun theScratchFolderGoesEvenWhenGgFails() {
        configurator { GgRunner.Output(1, "", "boom") }.apply(target(), AiRequest(false, false, true))
        assertTrue(scratch.isNotEmpty() && scratch.none { Files.exists(it) })
    }

    @Test
    fun aConnectedProjectIsOneWithALinkedMcpOrARegisteredAgent() {
        assertTrue(ApplyResult(listOf(StepResult(AiAgentConfigurator.MCP, Outcome.DONE))).connected)
        assertTrue(ApplyResult(listOf(StepResult(AiAgentConfigurator.ACP, Outcome.UNCHANGED))).connected)
        assertFalse(ApplyResult(listOf(StepResult(AiAgentConfigurator.MCP, Outcome.SKIPPED))).connected)
        assertFalse(ApplyResult(listOf(StepResult(AiAgentConfigurator.SKILL, Outcome.DONE))).connected)
        assertFalse(ApplyResult(listOf(StepResult(AiAgentConfigurator.ACP, Outcome.FAILED))).connected)
    }

    @Test
    fun theSummaryHasOneLinePerStepWithItsMark() {
        val text = AiSetup.summary(
            ApplyResult(
                listOf(
                    StepResult(AiAgentConfigurator.MCP, Outcome.DONE, "gg mcp --attach"),
                    StepResult(AiAgentConfigurator.SKILL, Outcome.UNCHANGED),
                    StepResult(AiAgentConfigurator.ACP, Outcome.SKIPPED, "kept <yours>"),
                    StepResult(AiAgentConfigurator.GIT, Outcome.FAILED, "denied")
                )
            )
        )
        assertEquals(".mcp.json — gg mcp --attach", text.split("<br>").first().removePrefix("✓ "))
        assertEquals(listOf("✓", "=", "–", "✗"), text.split("<br>").map { it.substringBefore(' ') })
        assertTrue(text.contains("kept &lt;yours>"))
        assertEquals("Nothing was selected.", AiSetup.summary(ApplyResult(emptyList())))
    }
}
