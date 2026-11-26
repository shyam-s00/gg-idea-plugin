package dev.gopherglide.ggplugin.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The samples are `gg mcp init` output captured from gg v1.5.0. */
class InitOutputTest {
    private val created = """
        created        p1/.mcp.json
          + {
          +       "args": [
          +         "mcp"
          +       ],
          +       "command": "gg"
          +     }
    """.trimIndent()

    private val skipped = """
        skipped        p1/.mcp.json
                       has a different "gg" entry; use --force to replace it
        created        p1/.claude/skills/gg/SKILL.md
    """.trimIndent()

    private val acp = """
        skipped        p1/.mcp.json
                       has a different "gg" entry; use --force to replace it
        added          /home/me/.jetbrains/acp.json
                       restart the IDE to pick it up
    """.trimIndent()

    @Test
    fun aCreatedFileIsReportedWithoutItsDiffLines() {
        assertEquals(listOf(InitLine("created", "p1/.mcp.json", null)), InitOutput.parse(created))
    }

    @Test
    fun aSkippedFileCarriesItsReasonAndTheNextFileStillParses() {
        val lines = InitOutput.parse(skipped)
        assertEquals(InitLine("skipped", "p1/.mcp.json", "has a different \"gg\" entry; use --force to replace it"), lines[0])
        assertEquals(InitLine("created", "p1/.claude/skills/gg/SKILL.md", null), lines[1])
    }

    @Test
    fun theAcpFileIsFoundByItsName() {
        val lines = InitOutput.parse(acp)
        assertEquals("added", InitOutput.forFile(lines, "acp.json")!!.status)
        assertEquals("restart the IDE to pick it up", InitOutput.forFile(lines, "acp.json")!!.reason)
        assertEquals("skipped", InitOutput.forFile(lines, ".mcp.json")!!.status)
    }

    @Test
    fun printOutputUsesWouldPrefixedStatuses() {
        val lines = InitOutput.parse("would-replaced /home/me/.jetbrains/acp.json\n               restart the IDE to pick it up\n  + gg autopilot: {")
        assertEquals("replaced", lines.single().status)
    }

    @Test
    fun aPathWithSpacesIsKeptWhole() {
        assertEquals("/Users/me/My Project/.mcp.json", InitOutput.parse("created        /Users/me/My Project/.mcp.json").single().path)
    }

    @Test
    fun otherTextIsIgnored() {
        assertEquals(emptyList<InitLine>(), InitOutput.parse("Gopher Glide (gg) 1.5.0\ngg mcp init: bad\n"))
        assertNull(InitOutput.forFile(emptyList(), "x"))
    }
}
