package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.services.InstalledBinary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class AiOnboardingTest {
    private val none = AiStatus(McpStatus(McpHealth.NO_FILE, null, emptyList()), false, AcpStatus(AcpHealth.NO_FILE, null, emptyList()), false, emptyList())

    private fun offer(
        dontAsk: Boolean = false, shown: Boolean = false, linked: Boolean = false, files: Boolean = true, usable: Boolean = true, status: AiStatus = none
    ) = AiOnboarding.shouldOffer(dontAsk, shown, linked, files, usable, status)

    @Test
    fun anUnconnectedProjectWithGgFilesAndAUsableGgIsOffered() {
        assertTrue(offer())
    }

    @Test
    fun theOfferIsMadeOnceAndNeverAgainAfterDontAskAgain() {
        assertFalse(offer(shown = true))
        assertFalse(offer(dontAsk = true))
    }

    @Test
    fun aProjectWithoutGgFilesOrWithAnOldGgIsLeftAlone() {
        assertFalse(offer(files = false))
        assertFalse(offer(usable = false))
    }

    @Test
    fun aProjectAlreadyConnectedIsNotAsked() {
        assertFalse(offer(linked = true))
        assertFalse(offer(status = none.copy(mcp = McpStatus(McpHealth.LINKED, GgEntry("gg", listOf("mcp", "--attach")), emptyList()))))
        assertFalse(offer(status = none.copy(acp = AcpStatus(AcpHealth.REGISTERED, GgEntry("l", listOf("autopilot", "--acp")), emptyList()))))
    }

    @Test
    fun aProjectWithOnlyTheDefaultEntryIsStillOffered() {
        assertTrue(offer(status = none.copy(mcp = McpStatus(McpHealth.NEEDS_ATTACH, GgEntry("gg", listOf("mcp")), emptyList()))))
    }

    @Test
    fun theConfigCallsGgByNameOnlyWhenTheIdeUsesTheOneOnPath() {
        val dir = Path.of("/p")
        assertEquals("gg", AiTargets.forBinary(dir, "/usr/local/bin/gg", InstalledBinary.Source.SYSTEM)!!.mcpCommand)
        assertEquals("/ide/plugins/gopher-glide-binary/gg", AiTargets.forBinary(dir, "/ide/plugins/gopher-glide-binary/gg", InstalledBinary.Source.PLUGIN)!!.mcpCommand)
        assertEquals("/opt/gg", AiTargets.forBinary(dir, "/opt/gg", InstalledBinary.Source.CUSTOM)!!.mcpCommand)
        assertEquals("/usr/local/bin/gg", AiTargets.forBinary(dir, "/usr/local/bin/gg", InstalledBinary.Source.SYSTEM)!!.ggBinary)
        assertNull(AiTargets.forBinary(dir, null, null))
    }

    @Test
    fun onlyAFullPathMakesTheFileMachineSpecific() {
        assertFalse(AiTarget(Path.of("/p"), "gg", "/x/gg").commandIsPath)
        assertTrue(AiTarget(Path.of("/p"), "/x/gg", "/x/gg").commandIsPath)
    }
}
