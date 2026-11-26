package dev.gopherglide.ggplugin.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    @Test
    fun delaysDoubleUpToTheCeiling() {
        val backoff = Backoff(500, 4_000)
        assertEquals(listOf(500L, 1000L, 2000L, 4000L, 4000L), List(5) { backoff.nextDelay() })
    }

    @Test
    fun aWorkingConnectionStartsItOver() {
        val backoff = Backoff(500, 4_000)
        repeat(3) { backoff.nextDelay() }
        backoff.reset()
        assertEquals(500L, backoff.nextDelay())
    }
}
