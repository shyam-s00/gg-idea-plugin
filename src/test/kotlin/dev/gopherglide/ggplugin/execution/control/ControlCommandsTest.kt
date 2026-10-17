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
