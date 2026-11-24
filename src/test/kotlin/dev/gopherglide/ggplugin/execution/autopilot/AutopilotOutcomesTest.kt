package dev.gopherglide.ggplugin.execution.autopilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutopilotOutcomesTest {
    @Test
    fun pillNamesTheModeStateAndPinnedRate() {
        assertEquals("⚡ Autopilot: Find Ceiling · Stepping (28 RPS)", AutopilotOutcomes.pill("find-ceiling", "stepping", 28))
        assertEquals("⚡ Autopilot: SLO Guard · Watching", AutopilotOutcomes.pill("slo-guard", "watching", null))
    }

    @Test
    fun anUnknownModeIsShownAsGgNamedIt() {
        assertEquals("⚡ Autopilot: future-mode · Idle", AutopilotOutcomes.pill("future-mode", "idle", null))
    }

    @Test
    fun aFoundCeilingStatesTheRate() {
        assertEquals("⚡ Ceiling found: 28 RPS", AutopilotOutcomes.describe("find-ceiling", "ceiling_found", 28))
    }

    @Test
    fun aCapReachedBeforeAnyBreachSaysSo() {
        assertEquals("⚡ No ceiling found up to 25 RPS cap", AutopilotOutcomes.describe("find-ceiling", "ceiling_not_reached", 25))
    }

    @Test
    fun anUnconfirmedSearchSaysItRanOutOfTime() {
        assertEquals("⚡ No stable rate confirmed before the time limit", AutopilotOutcomes.describe("find-ceiling", "unstable", null))
    }

    @Test
    fun aPlainStopAddsNothing() {
        assertNull(AutopilotOutcomes.describe("slo-guard", "stopped", null))
    }

    @Test
    fun nothingIsSaidWithoutAnAutopilot() {
        assertNull(AutopilotOutcomes.describe(null, null, null))
        assertNull(AutopilotOutcomes.describe("slo-guard", null, null))
    }

    @Test
    fun anOutcomeFromALaterGgIsPassedThrough() {
        assertEquals("⚡ Autopilot SLO Guard: budget exhausted", AutopilotOutcomes.describe("slo-guard", "budget_exhausted", null))
    }
}
