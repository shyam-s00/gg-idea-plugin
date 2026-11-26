package dev.gopherglide.ggplugin.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonCommandTest {
    private val options = DaemonOptions(1000, 10, 2, emptyList(), "")

    @Test
    fun theDaemonIsStartedOnAnyPortAndStopsWhenItsStdinCloses() {
        val args = DaemonCommand.args(options)
        assertEquals(listOf("mcp", "--port", "0", "--exit-on-stdin-eof"), args.take(4))
        assertTrue(args.containsAll(listOf("--max-rps", "1000", "--max-duration", "10m", "--max-sessions", "2")))
    }

    @Test
    fun noHostsMeansNoRestriction() {
        assertFalse(DaemonCommand.args(options).contains("--allow-host"))
    }

    @Test
    fun everyAllowedHostGetsItsOwnFlag() {
        val args = DaemonCommand.args(options.copy(allowHosts = listOf("api.test:8080", "localhost")))
        assertEquals(listOf("--allow-host", "api.test:8080", "--allow-host", "localhost"), args.takeLast(4))
    }

    @Test
    fun theTokenTravelsInTheEnvironmentNotTheArguments() {
        val args = DaemonCommand.args(options)
        assertFalse(args.any { it.contains("token", ignoreCase = true) })
        assertEquals(mapOf("GG_MCP_TOKEN" to "abc"), DaemonCommand.environment("abc", options))
    }

    @Test
    fun aCustomSnapshotFolderIsPassedOn() {
        assertEquals("/snaps", DaemonCommand.environment("abc", options.copy(snapDir = "/snaps"))["GG_SNAP_DIR"])
    }

    @Test
    fun tokensAreLongEnoughForTheDaemonAndDifferEachTime() {
        val a = DaemonCommand.newToken()
        assertTrue(a.length >= 16)
        assertTrue(a.matches(Regex("[0-9a-f]+")))
        assertNotEquals(a, DaemonCommand.newToken())
    }

    @Test
    fun theListeningLineGivesTheBaseUrlWithoutTheMcpSuffix() {
        val info = DaemonCommand.parseListening("""{"event":"listening","pid":91467,"url":"http://127.0.0.1:64112/mcp"}""", "tok")!!
        assertEquals("http://127.0.0.1:64112", info.baseUrl)
        assertEquals(91467L, info.pid)
        assertEquals("tok", info.token)
    }

    @Test
    fun anyOtherLineIsNotTheListeningLine() {
        assertNull(DaemonCommand.parseListening("Gopher Glide (gg) 1.5.0", "t"))
        assertNull(DaemonCommand.parseListening("""{"event":"other"}""", "t"))
        assertNull(DaemonCommand.parseListening("""{"event":"listening"}""", "t"))
        assertNull(DaemonCommand.parseListening("", "t"))
    }

    @Test
    fun hostTextIsSplitOnCommasAndSpacesAndDeduplicated() {
        assertEquals(listOf("a.test", "b.test:81", "c"), DaemonCommand.parseHosts(" a.test, b.test:81  c\n a.test "))
        assertEquals(emptyList<String>(), DaemonCommand.parseHosts("  "))
    }
}
