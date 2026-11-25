package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class AiSetupViewsTest {
    private val none = AiStatus(McpStatus(McpHealth.NO_FILE, null, emptyList()), false, AcpStatus(AcpHealth.NO_FILE, null, emptyList()), false, emptyList())
    private val linkedMcp = McpStatus(McpHealth.LINKED, GgEntry("gg", listOf("mcp", "--attach")), emptyList())
    private val target = AiTarget(Path.of("/p"), "gg", "/usr/local/bin/gg")
    private val absolute = target.copy(mcpCommand = "/opt/gg")

    private fun view(
        status: AiStatus = none, daemon: State = State.STOPPED, target: AiTarget? = this.target, git: Boolean = true, jetbrains: Boolean = false
    ) = AiSetupViews.of(status, daemon, "boom", target, git, jetbrains)

    @Test
    fun aStoppedDaemonSaysAgentRunsWillNotAppear() {
        assertEquals("Not running", view().daemon)
        assertTrue(view().daemonNote!!.startsWith("Agent runs will not appear in the IDE until the daemon is running."))
    }

    @Test
    fun withAnMcpLinkTheNoteAddsThatTheClientStillWorks() {
        val note = view(none.copy(mcp = linkedMcp)).daemonNote!!
        assertTrue(note, note.contains("An MCP client still works on its own, but its runs stay invisible here."))
    }

    @Test
    fun aRunningDaemonHasNoNote() {
        assertEquals("Running", view(daemon = State.RUNNING).daemon)
        assertNull(view(daemon = State.RUNNING).daemonNote)
    }

    @Test
    fun aFailedDaemonSaysWhy() {
        assertEquals("Failed: boom", view(daemon = State.FAILED).daemon)
        assertNotNull(view(daemon = State.FAILED).daemonNote)
    }

    @Test
    fun aFreshProjectOffersEverythingExceptAirOutsideAJetBrainsSetup() {
        val v = view()
        assertTrue(v.defaultMcp)
        assertTrue(v.defaultSkill)
        assertFalse(v.defaultAcp)
        assertEquals("Not set up", v.mcp)
        assertTrue(view(jetbrains = true).defaultAcp)
    }

    @Test
    fun aLinkedProjectShowsWhatIsSetUpAsTicked() {
        val v = view(none.copy(mcp = linkedMcp, skillPresent = true))
        assertTrue(v.defaultMcp)
        assertTrue(v.defaultSkill)
        assertEquals("Linked: gg mcp --attach", v.mcp)
        assertEquals("Installed", v.skill)
    }

    @Test
    fun theDefaultEntryIsOfferedTheAttachFix() {
        val v = view(none.copy(mcp = McpStatus(McpHealth.NEEDS_ATTACH, GgEntry("gg", listOf("mcp")), emptyList())))
        assertTrue(v.defaultMcp)
        assertEquals("Set up, but not attached to the IDE's daemon (gg mcp)", v.mcp)
        assertNull(v.replaceMcp)
    }

    @Test
    fun anEntryOfSomeoneElsesIsShownWithItsCommandAndOnlyReplacedOnRequest() {
        val v = view(none.copy(mcp = McpStatus(McpHealth.DIFFERENT, GgEntry("/dev/gg", listOf("mcp", "--port", "0")), listOf("a"))))
        assertEquals("Has its own gg entry: /dev/gg mcp --port 0", v.mcp)
        assertEquals("Replace the existing gg entry (currently: /dev/gg mcp --port 0)", v.replaceMcp)
        assertFalse(v.defaultMcp)
    }

    @Test
    fun aBrokenFileBlocksItsOwnOptionOnly() {
        val v = view(none.copy(mcp = McpStatus(McpHealth.MALFORMED, null, emptyList())))
        assertTrue(v.mcpBlocked)
        assertFalse(v.acpBlocked)
        assertFalse(v.defaultMcp)
        assertFalse(v.defaultSkill)
        assertTrue(view(none.copy(acp = AcpStatus(AcpHealth.MALFORMED, null, emptyList()))).acpBlocked)
    }

    @Test
    fun theAcpLineNamesWhatIsThereAndWhatIsKept() {
        assertEquals("gg autopilot is not registered (2 other agents kept)", view(none.copy(acp = AcpStatus(AcpHealth.NO_ENTRY, null, listOf("a", "b")))).acp)
        assertEquals("gg autopilot is not registered (1 other agent kept)", view(none.copy(acp = AcpStatus(AcpHealth.NO_ENTRY, null, listOf("a")))).acp)
        assertTrue(view(none.copy(acp = AcpStatus(AcpHealth.REGISTERED, GgEntry("l", listOf("autopilot", "--acp")), emptyList()))).defaultAcp)
        assertEquals("Registered through the IDE launcher", view(none.copy(acp = AcpStatus(AcpHealth.REGISTERED, GgEntry("l", listOf("autopilot", "--acp")), emptyList()))).acp)
    }

    @Test
    fun aDifferingAcpEntryIsShownAndOfferedAReplacement() {
        val v = view(none.copy(acp = AcpStatus(AcpHealth.DIFFERENT, GgEntry("/dev/gg", listOf("autopilot", "--acp")), emptyList())))
        assertEquals("Registered differently: /dev/gg autopilot --acp", v.acp)
        assertEquals("Replace the existing gg autopilot entry (currently: /dev/gg autopilot --acp)", v.replaceAcp)
        assertFalse(v.defaultAcp)
    }

    @Test
    fun theGitOfferNeedsAMachineSpecificPathAndACheckout() {
        assertFalse(view(target = target).offerExclude)
        assertTrue(view(target = absolute).offerExclude)
        assertFalse(view(target = absolute, git = false).offerExclude)
        assertFalse(view(target = null).offerExclude)
    }
}
