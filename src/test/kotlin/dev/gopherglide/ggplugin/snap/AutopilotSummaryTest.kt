package dev.gopherglide.ggplugin.snap

import com.google.gson.Gson
import dev.gopherglide.ggplugin.snap.AutopilotSummary.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutopilotSummaryTest {
    private val gson = Gson()

    private fun autopilot(name: String): SnapAutopilot =
        gson.fromJson(checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).bufferedReader().readText(), SnapModel::class.java).meta!!.autopilot!!

    @Test
    fun aFoundCeilingIsTheHeadlineAndReadsAsGood() {
        val summary = AutopilotSummary.from(autopilot("snap-v150-find-ceiling.snap"))!!
        assertEquals("⚡ Autopilot Find Ceiling: Ceiling found: 28 RPS", summary.headline)
        assertEquals(Tone.GOOD, summary.tone)
    }

    @Test
    fun theDetailGivesTheLimitsAndWhyTheRateBackedOff() {
        val summary = AutopilotSummary.from(autopilot("snap-v150-find-ceiling.snap"))!!
        assertEquals(
            "Limits: p99 ≤ 250ms, errors ≤ 1% · ceiling breached at 35 RPS (p99=406ms > 250ms); backing off to 28 RPS",
            summary.detail
        )
    }

    @Test
    fun aGuardThatClampedNamesTheClamp() {
        val summary = AutopilotSummary.from(autopilot("snap-v150-autopilot.snap"))!!
        assertEquals("⚡ Autopilot SLO Guard: Stopped before a result", summary.headline)
        assertEquals(Tone.NEUTRAL, summary.tone)
        assertTrue(summary.detail, summary.detail.endsWith("clamped to 17 RPS (errors=66.7% > 1.0%)"))
    }

    @Test
    fun theTrailListsEveryEventWithTimeSinceTheAutopilotStarted() {
        val trail = AutopilotSummary.from(autopilot("snap-v150-autopilot.snap"))!!.trail
        assertEquals(3, trail.size)
        assertTrue(trail[1], trail[1].startsWith("+0:02 clamped (17 RPS)"))
        assertTrue(trail[0], trail[0].startsWith("+0:00 watching"))
    }

    @Test
    fun anUnstableSearchIsAWarning() {
        val summary = AutopilotSummary.from(SnapAutopilot(mode = "find-ceiling", outcome = "unstable"))!!
        assertEquals(Tone.WARN, summary.tone)
        assertEquals("⚡ Autopilot Find Ceiling: No stable rate confirmed before the time limit", summary.headline)
        assertEquals("", summary.detail)
    }

    @Test
    fun noAutopilotMeansNoSummary() {
        assertNull(AutopilotSummary.from(null))
        assertNull(AutopilotSummary.from(SnapAutopilot()))
    }

    @Test
    fun aMissingOutcomeIsSaidPlainly() {
        assertEquals("⚡ Autopilot SLO Guard: No result recorded", AutopilotSummary.from(SnapAutopilot(mode = "slo-guard"))!!.headline)
    }

    @Test
    fun originNamesKnownStartersOnly() {
        assertEquals("🤖 Agent", SnapOrigin.label("agent"))
        assertEquals("👤 User", SnapOrigin.label("user"))
        assertNull(SnapOrigin.label(null))
        assertNull(SnapOrigin.label("autopilot-of-the-future"))
        assertNull(SnapOrigin.label(""))
    }
}
