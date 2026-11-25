package dev.gopherglide.ggplugin.ai

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService.State
import java.nio.file.Path
import javax.swing.JComponent

class ConfigureAiAgentsDialogTest : BasePlatformTestCase() {
    private val none = AiStatus(McpStatus(McpHealth.NO_FILE, null, emptyList()), false, AcpStatus(AcpHealth.NO_FILE, null, emptyList()), false, emptyList())
    private val target = AiTarget(Path.of("/p"), "/opt/gg", "/opt/gg")

    private class Shown(val dialog: ConfigureAiAgentsDialog, val content: JComponent) {
        fun labels() = UIUtil.findComponentsOfType(content, javax.swing.JLabel::class.java).map { it.text.orEmpty() }
        fun boxes() = UIUtil.findComponentsOfType(content, JBCheckBox::class.java)
        fun box(text: String) = boxes().first { it.text.startsWith(text) }
    }

    private fun withDialog(
        status: AiStatus = none, daemon: State = State.STOPPED, jetBrains: Boolean = false, git: Boolean = true,
        env: Map<String, String> = emptyMap(), check: (Shown) -> Unit
    ) {
        val view = AiSetupViews.of(status, daemon, null, target, git, jetBrains)
        val dialog = ConfigureAiAgentsDialog(project, view, "Found in this IDE: Junie", env) { _, _ -> }
        try {
            val content = ConfigureAiAgentsDialog::class.java.getDeclaredMethod("createCenterPanel").apply { isAccessible = true }.invoke(dialog) as JComponent
            check(Shown(dialog, content))
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testADownDaemonIsSaidPlainlyAndAgentRunsAreWarnedAbout() {
        withDialog { shown ->
            assertTrue(shown.labels().contains("Not running"))
            assertTrue(shown.labels().any { it.startsWith("Agent runs will not appear in the IDE until the daemon is running.") })
        }
    }

    fun testARunningDaemonHasNoWarning() {
        withDialog(daemon = State.RUNNING) { shown ->
            assertTrue(shown.labels().contains("Running"))
            assertTrue(shown.labels().none { it.startsWith("Agent runs will not appear") })
        }
    }

    fun testItNamesWhatIsFoundInThisIdeAndTheStateOfEachFile() {
        withDialog { shown ->
            assertTrue(shown.labels().contains("Found in this IDE: Junie"))
            assertTrue(shown.labels().containsAll(listOf("Not set up", "Not installed", "No acp.json yet")))
        }
    }

    fun testAFreshProjectStartsWithTheMcpLinkAndTheSkillTickedAndAirOnlyInAJetBrainsSetup() {
        withDialog { shown ->
            assertTrue(shown.dialog.request().mcp)
            assertTrue(shown.dialog.request().skill)
            assertFalse(shown.dialog.request().acp)
        }
        withDialog(jetBrains = true) { assertTrue(it.dialog.request().acp) }
    }

    fun testReplaceBoxesAppearOnlyForAnEntryOfSomeoneElses() {
        withDialog { shown -> assertTrue(shown.boxes().none { it.text.startsWith("Replace") }) }
        val different = none.copy(
            mcp = McpStatus(McpHealth.DIFFERENT, GgEntry("/dev/gg", listOf("mcp", "--port", "0")), emptyList()),
            acp = AcpStatus(AcpHealth.DIFFERENT, GgEntry("/dev/gg", listOf("autopilot", "--acp")), emptyList())
        )
        withDialog(different) { shown ->
            assertEquals(2, shown.boxes().count { it.text.startsWith("Replace") })
            assertFalse("never ticked on its own", shown.dialog.request().replaceMcp || shown.dialog.request().replaceAcp)
            shown.box("Replace the existing gg entry").isSelected = true
            assertTrue(shown.dialog.request().replaceMcp)
        }
    }

    fun testOverwriteOptionsForTheSkillAndAirAreAlwaysOfferedAndFollowTheirTicks() {
        withDialog(jetBrains = true) { shown ->
            val skill = shown.box("Overwrite the skill file")
            val air = shown.box("Overwrite the gg autopilot entry")
            assertFalse(skill.isSelected || air.isSelected)
            assertTrue(skill.isEnabled && air.isEnabled)
            skill.isSelected = true
            air.isSelected = true
            assertTrue(shown.dialog.request().replaceSkill && shown.dialog.request().replaceAcp)
            shown.box("Install the agent skill").isSelected = false
            shown.box("Register gg autopilot").isSelected = false
            assertFalse(skill.isEnabled || air.isEnabled)
        }
    }

    fun testTheGitOptionIsOfferedOnlyForAMachineSpecificCommandInACheckout() {
        withDialog { shown -> assertTrue(shown.boxes().any { it.text.startsWith("Keep .mcp.json out of version control") }) }
        withDialog(git = false) { shown -> assertTrue(shown.boxes().none { it.text.startsWith("Keep .mcp.json out") }) }
    }

    fun testTheLanguageModelSettingsAreOnlySentWhenAirIsBeingRegistered() {
        val env = mapOf("GEMINI_API_KEY" to "k", "GG_AUTOPILOT_MODEL" to "m")
        withDialog(env = env) { shown ->
            assertNull(shown.dialog.request().agentEnv)
            shown.box("Register gg autopilot in JetBrains Air").isSelected = true
            assertEquals("k", shown.dialog.request().agentEnv!!["GEMINI_API_KEY"])
            assertEquals("m", shown.dialog.request().agentEnv!!["GG_AUTOPILOT_MODEL"])
        }
    }

    fun testABrokenFileDisablesItsOwnOptionAndLeavesItUnticked() {
        val broken = none.copy(mcp = McpStatus(McpHealth.MALFORMED, null, emptyList()))
        withDialog(broken) { shown ->
            assertFalse(shown.dialog.request().mcp)
            assertFalse(shown.box("Link this project's .mcp.json").isEnabled)
            assertTrue(shown.labels().any { it.startsWith("Not valid JSON") })
        }
    }

    fun testTheDaemonLimitsComeFromSettingsAndGoBackOut() {
        withDialog { shown ->
            val result = shown.dialog.result
            assertEquals(1000, result.maxRps)
            assertEquals(10, result.maxDurationMinutes)
            assertEquals(2, result.maxSessions)
            assertEquals("", result.allowHosts)
        }
    }
}
