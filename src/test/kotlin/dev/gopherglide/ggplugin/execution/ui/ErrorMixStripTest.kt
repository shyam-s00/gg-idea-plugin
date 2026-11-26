package dev.gopherglide.ggplugin.execution.ui

import com.google.gson.Gson
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.ErrorEndpoint
import dev.gopherglide.ggplugin.execution.ErrorMixPayload
import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import dev.gopherglide.ggplugin.execution.WindowHealthPayload

class ErrorMixStripTest : BasePlatformTestCase() {
    private val gson = Gson()

    private fun heartbeats(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { gson.fromJson(it, HeartbeatPayload::class.java) }.filter { it.event == "heartbeat" }

    private fun texts(strip: ErrorMixStrip) = UIUtil.findComponentsOfType(strip, JBLabel::class.java).map { it.text.orEmpty() }

    fun testHiddenUntilAHeartbeatCarriesWindowHealth() {
        val strip = ErrorMixStrip()
        assertFalse(strip.isVisible)
        strip.update(HeartbeatPayload(event = "heartbeat"))
        assertFalse(strip.isVisible)
    }

    fun testShowsTheFailureClassesAndTopEndpoints() {
        val strip = ErrorMixStrip()
        strip.update(heartbeats("stream-v150-errors.ndjson").first())
        assertTrue(strip.isVisible)
        assertTrue(texts(strip).contains("▲ Recent errors (10s): 5xx 20 · 4xx 20"))
        val detail = texts(strip).first { it.startsWith("Top failing") }
        assertTrue(detail, detail.contains("GET /fail/checkout (20× 503)"))
        assertTrue(detail, detail.contains("GET /missing/item (20× 404)"))
        assertTrue(strip.toolTipText.contains("503 (20×)"))
    }

    fun testStaysVisibleAndSaysSoWhenNothingFails() {
        val strip = ErrorMixStrip()
        strip.update(heartbeats("stream-v150-find-ceiling.ndjson").first())
        assertTrue(strip.isVisible)
        assertTrue(texts(strip).contains("✓ No errors in the last 10s"))
        assertNull(strip.toolTipText)
    }

    fun testKeepsItsHeightWhetherOrNotThereAreErrors() {
        val withErrors = ErrorMixStrip().also { it.update(heartbeats("stream-v150-errors.ndjson").first()) }
        val quiet = ErrorMixStrip().also { it.update(heartbeats("stream-v150-find-ceiling.ndjson").first()) }
        assertEquals(withErrors.preferredSize.height, quiet.preferredSize.height)
    }

    fun testRecoveringFromErrorsSwitchesBackToTheQuietLine() {
        val strip = ErrorMixStrip()
        strip.update(heartbeats("stream-v150-errors.ndjson").first())
        strip.update(HeartbeatPayload(event = "heartbeat", window = WindowHealthPayload(seconds = 5, samples = 100)))
        assertTrue(texts(strip).contains("✓ No errors in the last 10s"))
        assertFalse(texts(strip).any { it.startsWith("Top failing") })
    }

    fun testClearHidesTheStrip() {
        val strip = ErrorMixStrip()
        strip.update(heartbeats("stream-v150-errors.ndjson").first())
        strip.clear()
        assertFalse(strip.isVisible)
    }

    fun testCountsListOnlyTheClassesThatOccurred() {
        assertEquals("5xx 14 · transport 2", ErrorMixText.counts(ErrorMixPayload(sampled = 16, http5xx = 14, transport = 2)))
        assertEquals("", ErrorMixText.counts(ErrorMixPayload()))
    }

    fun testTopFailingIsLimitedAndNullWithoutEndpoints() {
        val endpoints = (1..5).map { ErrorEndpoint("GET /e$it", it, 500, "http_5xx") }
        val text = ErrorMixText.topFailing(ErrorMixPayload(topEndpoints = endpoints))!!
        assertEquals(3, text.split(" · ").size)
        assertNull(ErrorMixText.topFailing(ErrorMixPayload()))
    }

    fun testATransportFailureNamesItsKindInsteadOfACode() {
        assertEquals("GET /x (3× transport)", ErrorMixText.endpoint(ErrorEndpoint("GET /x", 3, 0, "transport")))
        assertEquals("GET /x (3× 503)", ErrorMixText.endpoint(ErrorEndpoint("GET /x", 3, 503, "http_5xx")))
    }

    fun testTooltipEscapesHtmlInEndpoints() {
        val mix = ErrorMixPayload(windowSeconds = 10, sampled = 1, topEndpoints = listOf(ErrorEndpoint("GET /<b>&", 1, 500, "http_5xx")))
        assertTrue(ErrorMixText.tooltip(mix).contains("GET /&lt;b>&amp;"))
    }
}
