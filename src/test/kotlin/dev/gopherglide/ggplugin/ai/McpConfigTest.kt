package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigTest {
    private fun file(vararg entries: String) = """{"mcpServers":{${entries.joinToString(",")}}}"""

    private val gg = { args: String, command: String -> """"gg":{"command":"$command","args":[$args]}""" }

    @Test
    fun noFileIsNotSetUp() {
        assertEquals(McpHealth.NO_FILE, McpConfig.inspect(null).health)
    }

    @Test
    fun brokenJsonIsMalformedAndSoIsAnythingButAnObject() {
        listOf("{ nope", "[]", "42", "").forEach { assertEquals(it, McpHealth.MALFORMED, McpConfig.inspect(it).health) }
    }

    @Test
    fun aFileWithoutAGgEntryKeepsCountOfTheOthers() {
        val status = McpConfig.inspect(file(""""other":{"command":"x","args":["a"]}""", """"third":{"command":"y"}"""))
        assertEquals(McpHealth.NO_ENTRY, status.health)
        assertEquals(listOf("other", "third"), status.otherServers)
        assertEquals(McpHealth.NO_ENTRY, McpConfig.inspect("{}").health)
    }

    @Test
    fun theDefaultEntryNeedsToBeAttached() {
        val status = McpConfig.inspect(file(gg("\"mcp\"", "gg")))
        assertEquals(McpHealth.NEEDS_ATTACH, status.health)
        assertEquals(GgEntry("gg", listOf("mcp")), status.entry)
    }

    @Test
    fun anAttachingEntryIsLinked() {
        assertEquals(McpHealth.LINKED, McpConfig.inspect(file(gg("\"mcp\",\"--attach\"", "/usr/local/bin/gg"))).health)
    }

    @Test
    fun anythingElseIsADifferentEntry() {
        assertEquals(McpHealth.DIFFERENT, McpConfig.inspect(file(gg("\"mcp\",\"--port\",\"0\"", "gg"))).health)
        assertEquals(McpHealth.DIFFERENT, McpConfig.inspect(file(gg("", "gg"))).health)
        assertEquals("gg mcp --port 0", McpConfig.inspect(file(gg("\"mcp\",\"--port\",\"0\"", "gg"))).entry!!.display)
    }

    @Test
    fun anEntryWithoutACommandIsNoEntry() {
        assertEquals(McpHealth.NO_ENTRY, McpConfig.inspect("""{"mcpServers":{"gg":{"args":["mcp"]}}}""").health)
        assertEquals(McpHealth.NO_ENTRY, McpConfig.inspect("""{"mcpServers":{"gg":"gg mcp"}}""").health)
    }

    @Test
    fun attachingChangesOnlyTheArgsAndKeepsEveryOtherServerAndField() {
        val text = """{"mcpServers":{"other":{"command":"x","args":["a","b"],"env":{"K":"<v>&"}},"gg":{"args":["mcp"],"command":"gg","timeout":30}},"extra":1}"""
        val edited = McpConfig.withAttach(text)
        val status = McpConfig.inspect(edited)
        assertEquals(McpHealth.LINKED, status.health)
        assertEquals(listOf("other"), status.otherServers)
        assertTrue(edited.contains(""""env": {"""))
        assertTrue("html characters are not escaped: $edited", edited.contains(""""K": "<v>&""""))
        assertTrue(edited.contains(""""timeout": 30"""))
        assertTrue(edited.contains(""""extra": 1"""))
    }

    @Test
    fun attachingAnAlreadyLinkedFileChangesNothing() {
        val text = McpConfig.withAttach(file(gg("\"mcp\"", "gg")))
        assertEquals(text, McpConfig.withAttach(text))
    }

    @Test
    fun theTrailingNewlineIsKeptOrNotAsFound() {
        assertTrue(McpConfig.withAttach(file(gg("\"mcp\"", "gg")) + "\n").endsWith("}\n"))
        assertFalse(McpConfig.withAttach(file(gg("\"mcp\"", "gg"))).endsWith("\n"))
    }

    @Test
    fun theCommandCanBeMovedWithoutTouchingTheArgs() {
        val edited = McpConfig.withCommand(McpConfig.withAttach(file(gg("\"mcp\"", "/old/gg"))), "/new/gg")
        assertEquals(GgEntry("/new/gg", listOf("mcp", "--attach")), McpConfig.inspect(edited).entry)
    }

    @Test
    fun editingAFileThatCannotBeEditedFailsLoudly() {
        listOf("{ nope", "{}").forEach {
            val failure = runCatching { McpConfig.withAttach(it) }.exceptionOrNull()
            assertTrue(it, failure != null)
        }
    }
}
