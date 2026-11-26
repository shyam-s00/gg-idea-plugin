package dev.gopherglide.ggplugin.execution.control

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ControlCommandsTest {
    @Test
    fun biasMatchesTheDocumentedWireForm() {
        assertEquals("""{"id":"c1","command":"bias","amount":5}""", ControlCommand.Bias("c1", 5).toLine())
        assertEquals("""{"id":"c2","command":"bias","amount":-5}""", ControlCommand.Bias("c2", -5).toLine())
    }

    @Test
    fun markMatchesTheDocumentedWireForm() {
        assertEquals(
            """{"id":"c3","command":"mark","label":"deploy-v2-canary"}""",
            ControlCommand.Mark("c3", "deploy-v2-canary").toLine()
        )
    }

    @Test
    fun markWithoutATimeOmitsElapsed() {
        assertFalse(ControlCommand.Mark("m", "x").toLine().contains("elapsed_s"))
    }

    @Test
    fun markCarriesTheRequestedTimeAsANumber() {
        assertEquals(
            """{"id":"m1","command":"mark","label":"x","elapsed_s":18.0}""",
            ControlCommand.Mark("m1", "x", 18.0).toLine()
        )
        assertEquals(18.234, JsonParser.parseString(ControlCommand.Mark("m", "x", 18.234).toLine()).asJsonObject["elapsed_s"].asDouble, 0.0)
    }

    @Test
    fun holdAndReleaseMatchTheWireForm() {
        assertEquals("""{"id":"h1","command":"hold","rps":15}""", ControlCommand.Hold("h1", 15).toLine())
        assertEquals("""{"id":"h2","command":"release"}""", ControlCommand.Release("h2").toLine())
    }

    @Test
    fun autopilotStartNamesTheModeAndStopDoesNot() {
        assertEquals(
            """{"id":"a1","command":"autopilot","action":"start","mode":"slo-guard"}""",
            ControlCommand.Autopilot("a1", ControlCommand.Autopilot.Action.START, "slo-guard").toLine()
        )
        assertEquals(
            """{"id":"a2","command":"autopilot","action":"stop"}""",
            ControlCommand.Autopilot("a2", ControlCommand.Autopilot.Action.STOP).toLine()
        )
    }

    @Test
    fun autopilotStartCanCarryItsLimits() {
        assertEquals(
            """{"id":"a1","command":"autopilot","action":"start","mode":"slo-guard","max_p99_ms":120,"max_error_rate":0.02}""",
            ControlCommand.Autopilot("a1", ControlCommand.Autopilot.Action.START, "slo-guard", 120, 0.02).toLine()
        )
    }

    @Test
    fun agentSteerMatchesTheWireForm() {
        assertEquals("""{"id":"g1","command":"agent_steer","allow":true}""", ControlCommand.AgentSteer("g1", true).toLine())
        assertEquals("""{"id":"g2","command":"agent_steer","allow":false}""", ControlCommand.AgentSteer("g2", false).toLine())
    }

    @Test
    fun stopMatchesTheDocumentedWireForm() {
        assertEquals("""{"id":"c4","command":"stop"}""", ControlCommand.Stop("c4").toLine())
    }

    @Test
    fun labelSpecialCharactersRoundTripOnASingleLine() {
        val labels = listOf("say \"hi\"", "back\\slash", "two\nlines", "tab\there", "ünïcödé ✓ 日本", "<b>&'</b>")
        labels.forEach { label ->
            val line = ControlCommand.Mark("m", label).toLine()
            assertFalse("no raw newline in $line", line.contains('\n'))
            assertEquals(label, JsonParser.parseString(line).asJsonObject["label"].asString)
        }
    }

    @Test
    fun htmlCharactersAreNotEscapedToUnicodeSequences() {
        assertEquals("""{"id":"m","command":"mark","label":"<a&b>"}""", ControlCommand.Mark("m", "<a&b>").toLine())
    }
}
