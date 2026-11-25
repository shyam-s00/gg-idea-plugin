package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class AiEnvironmentDetectorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val project: Path by lazy { tmp.newFolder("p").toPath() }
    private val acp: Path by lazy { tmp.root.toPath().resolve("jb/acp.json") }
    private val launcher by lazy { LauncherFiles(tmp.root.toPath().resolve("home"), windows = false) }

    private fun detect(hosts: List<String> = emptyList()) = AiEnvironmentDetector(acp, launcher) { hosts }.inspect(project)

    @Test
    fun aBareProjectHasNothingConnected() {
        val status = detect()
        assertEquals(McpHealth.NO_FILE, status.mcp.health)
        assertEquals(AcpHealth.NO_FILE, status.acp.health)
        assertFalse(status.skillPresent)
        assertFalse(status.launcherInstalled)
        assertFalse(status.anythingLinked)
    }

    @Test
    fun anAttachingMcpEntryCountsAsLinked() {
        Files.writeString(project.resolve(".mcp.json"), McpConfig.withAttach("""{"mcpServers":{"gg":{"command":"gg","args":["mcp"]}}}"""))
        assertTrue(detect().anythingLinked)
    }

    @Test
    fun theDefaultMcpEntryAloneDoesNotCountAsLinked() {
        Files.writeString(project.resolve(".mcp.json"), """{"mcpServers":{"gg":{"command":"gg","args":["mcp"]}}}""")
        assertEquals(McpHealth.NEEDS_ATTACH, detect().mcp.health)
        assertFalse(detect().anythingLinked)
    }

    @Test
    fun theSkillFileIsNoticed() {
        Files.createDirectories(project.resolve(".claude/skills/gg"))
        Files.writeString(project.resolve(".claude/skills/gg/SKILL.md"), "x")
        assertTrue(detect().skillPresent)
    }

    @Test
    fun aRegistrationRunningOurLauncherCountsAsLinkedAndAnyOtherDoesNot() {
        Files.createDirectories(acp.parent)
        launcher.install("/opt/gg")
        Files.writeString(acp, """{"agent_servers":{"gg autopilot":{"command":"${launcher.script}","args":["autopilot","--acp"]}}}""")
        assertEquals(AcpHealth.REGISTERED, detect().acp.health)
        assertTrue(detect().anythingLinked)
        Files.writeString(acp, """{"agent_servers":{"gg autopilot":{"command":"/dev/gg","args":["autopilot","--acp"]}}}""")
        assertEquals(AcpHealth.DIFFERENT, detect().acp.health)
        assertFalse(detect().anythingLinked)
    }

    @Test
    fun withoutAnInstalledLauncherNoRegistrationIsOurs() {
        Files.createDirectories(acp.parent)
        Files.writeString(acp, """{"agent_servers":{"gg autopilot":{"command":"${launcher.script}","args":["autopilot","--acp"]}}}""")
        assertEquals(AcpHealth.DIFFERENT, detect().acp.health)
    }

    @Test
    fun theIdesAiFeaturesAreListed() {
        assertEquals(listOf("Junie", "JetBrains AI Assistant"), detect(listOf("Junie", "JetBrains AI Assistant")).detectedHosts)
    }
}
