package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AcpConfigTest {
    private val launcher = "/home/me/.jetbrains/gopher-glide/gg-launcher.sh"

    private fun file(agent: String) = """{"default_mcp_settings":{}, "agent_servers": {"Claude": {"command":"claude-acp"}, $agent}}"""

    private val ours = """"gg autopilot": {"command":"$launcher","args":["autopilot","--acp"],"use_custom_mcp":true}"""

    @Test
    fun noFileAndBrokenFilesAreTold() {
        assertEquals(AcpHealth.NO_FILE, AcpConfig.inspect(null, launcher).health)
        assertEquals(AcpHealth.MALFORMED, AcpConfig.inspect("{ x", launcher).health)
        assertEquals(AcpHealth.MALFORMED, AcpConfig.inspect("[]", launcher).health)
    }

    @Test
    fun otherAgentsAreCountedWhetherOrNotGgIsThere() {
        val status = AcpConfig.inspect("""{"agent_servers":{"Claude":{"command":"c"},"OpenHands":{"command":"o"}}}""", launcher)
        assertEquals(AcpHealth.NO_ENTRY, status.health)
        assertEquals(listOf("Claude", "OpenHands"), status.otherAgents)
        assertEquals(AcpHealth.NO_ENTRY, AcpConfig.inspect("{}", launcher).health)
    }

    @Test
    fun anEntryRunningTheLauncherIsRegistered() {
        val status = AcpConfig.inspect(file(ours), launcher)
        assertEquals(AcpHealth.REGISTERED, status.health)
        assertEquals(listOf("Claude"), status.otherAgents)
    }

    @Test
    fun anEntryRunningAnythingElseIsDifferent() {
        val direct = AcpConfig.inspect(file(""""gg autopilot": {"command":"/dev/gg","args":["autopilot","--acp"]}"""), launcher)
        assertEquals(AcpHealth.DIFFERENT, direct.health)
        assertEquals("/dev/gg autopilot --acp", direct.entry!!.display)
    }

    @Test
    fun withoutALauncherEveryEntryIsDifferent() {
        assertEquals(AcpHealth.DIFFERENT, AcpConfig.inspect(file(ours), null).health)
    }

    @Test
    fun theLegacyNameOfTheAgentIsAnotherAgentNotOurs() {
        val status = AcpConfig.inspect(file(""""GopherGlide Autopilot": {"command":"/dev/gg","args":["autopilot","--acp"]}"""), launcher)
        assertEquals(AcpHealth.NO_ENTRY, status.health)
        assertEquals(listOf("Claude", "GopherGlide Autopilot"), status.otherAgents)
    }
}
