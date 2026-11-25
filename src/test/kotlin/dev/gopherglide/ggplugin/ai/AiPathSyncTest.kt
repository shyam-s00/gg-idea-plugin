package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class AiPathSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun mcp(command: String, args: String = """"mcp","--attach"""") = """{"mcpServers":{"gg":{"command":"$command","args":[$args]}}}"""

    private val managed = "/ide/plugins/gopher-glide-binary"

    @Test
    fun anEntryPointingAtAVanishedPathMovesToTheBinaryInUse() {
        val edited = AiPathSync.syncedMcp(mcp("/gone/gg"), "/opt/gg", managed) { false }!!
        assertEquals("/opt/gg", McpConfig.inspect(edited).entry!!.command)
    }

    @Test
    fun anEntryPointingAtThePluginManagedBinaryMovesToo() {
        assertNotNull(AiPathSync.syncedMcp(mcp("$managed/gg"), "gg", managed) { true })
    }

    @Test
    fun anEntryTheUserPointedAtAnExistingBinaryOfTheirOwnIsLeftAlone() {
        assertNull(AiPathSync.syncedMcp(mcp("/home/me/bin/gg"), "/opt/gg", managed) { true })
    }

    @Test
    fun theBareCommandIsNeverRewritten() {
        assertNull(AiPathSync.syncedMcp(mcp("gg"), "/opt/gg", managed) { false })
    }

    @Test
    fun anEntryThatIsAlreadyRightOrNotAttachingIsLeftAlone() {
        assertNull(AiPathSync.syncedMcp(mcp("/opt/gg"), "/opt/gg", managed) { false })
        assertNull(AiPathSync.syncedMcp(mcp("/gone/gg", """"mcp""""), "/opt/gg", managed) { false })
        assertNull(AiPathSync.syncedMcp("{ broken", "/opt/gg", managed) { false })
    }

    @Test
    fun syncingRewritesTheLauncherAndTheMcpFileInOneGo() {
        val project = tmp.newFolder("p").toPath()
        Files.writeString(project.resolve(".mcp.json"), mcp("/gone/gg"))
        val launcher = LauncherFiles(tmp.root.toPath().resolve("home"), windows = false).also { it.install("/gone/gg") }
        val target = AiTarget(project, "/opt/gg", "/opt/gg")
        assertTrue(AiPathSync.sync(target, launcher, managed))
        assertTrue(Files.readString(launcher.script).contains("'/opt/gg'"))
        assertEquals("/opt/gg", McpConfig.inspect(Files.readString(project.resolve(".mcp.json"))).entry!!.command)
        assertFalse(AiPathSync.sync(target, launcher, managed))
    }

    @Test
    fun withoutALauncherOrAnMcpFileThereIsNothingToSync() {
        val project = tmp.newFolder("empty").toPath()
        val launcher = LauncherFiles(tmp.root.toPath().resolve("none"), windows = false)
        assertFalse(AiPathSync.sync(AiTarget(project, "gg", "/opt/gg"), launcher, managed))
        assertFalse(launcher.isInstalled())
    }
}
