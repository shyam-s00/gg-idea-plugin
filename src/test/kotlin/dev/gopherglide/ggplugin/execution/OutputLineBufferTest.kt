package dev.gopherglide.ggplugin.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputLineBufferTest {
    @Test
    fun completeLineIsReturned() {
        assertEquals(listOf("""{"a":1}"""), OutputLineBuffer().append("{\"a\":1}\n"))
    }

    @Test
    fun lineSplitAcrossChunksIsReassembled() {
        val buffer = OutputLineBuffer()
        assertEquals(emptyList<String>(), buffer.append("""{"event":"hea"""))
        assertEquals(emptyList<String>(), buffer.append("rtbeat\",\"bias\""))
        assertEquals(listOf("""{"event":"heartbeat","bias":5}"""), buffer.append(":5}\n"))
    }

    @Test
    fun severalLinesInOneChunkAreAllReturnedInOrder() {
        assertEquals(listOf("one", "two", "three"), OutputLineBuffer().append("one\ntwo\nthree\n"))
    }

    @Test
    fun trailingPartialLineIsHeldUntilCompleted() {
        val buffer = OutputLineBuffer()
        assertEquals(listOf("one"), buffer.append("one\ntw"))
        assertEquals(listOf("two"), buffer.append("o\n"))
    }

    @Test
    fun crlfAndBlankLinesAreDropped() {
        assertEquals(listOf("one", "two"), OutputLineBuffer().append("one\r\n\r\n  \ntwo\r\n"))
    }

    @Test
    fun flushReturnsUnterminatedTailOnce() {
        val buffer = OutputLineBuffer()
        buffer.append("""{"event":"stopped"}""")
        assertEquals("""{"event":"stopped"}""", buffer.flush())
        assertNull(buffer.flush())
    }

    @Test
    fun flushIsNullWhenNothingIsPending() {
        val buffer = OutputLineBuffer()
        buffer.append("done\n")
        assertNull(buffer.flush())
    }

    @Test
    fun nonJsonNoisePassesThroughUnjudged() {
        assertEquals(listOf("Starting..."), OutputLineBuffer().append("Starting...\n"))
    }

    @Test
    fun anyChunkSizeYieldsTheSameLinesAsOneBigChunk() {
        val text = checkNotNull(javaClass.getResourceAsStream("/fixtures/stream-v130-control.ndjson")).bufferedReader().readText()
        val whole = OutputLineBuffer().append(text)
        for (size in listOf(1, 3, 7, 64, 500)) {
            val buffer = OutputLineBuffer()
            val chunked = text.chunked(size).flatMap(buffer::append) + listOfNotNull(buffer.flush())
            assertEquals("chunk size $size", whole, chunked)
        }
    }
}
