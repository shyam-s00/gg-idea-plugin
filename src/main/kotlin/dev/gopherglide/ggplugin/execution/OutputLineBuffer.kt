package dev.gopherglide.ggplugin.execution

/** Reassembles process output chunks into complete lines; a chunk may hold several lines or end mid-line. */
class OutputLineBuffer {
    private val pending = StringBuilder()

    /** Appends [chunk] and returns every line it completed, trimmed, with blank lines dropped. */
    @Synchronized
    fun append(chunk: String): List<String> {
        pending.append(chunk)
        val lines = mutableListOf<String>()
        var newline = pending.indexOf('\n')
        while (newline >= 0) {
            pending.substring(0, newline).trim().takeIf { it.isNotEmpty() }?.let(lines::add)
            pending.delete(0, newline + 1)
            newline = pending.indexOf('\n')
        }
        return lines
    }

    /** Returns the unterminated tail left at end of output, or null if there is none. */
    @Synchronized
    fun flush(): String? {
        val tail = pending.toString().trim()
        pending.setLength(0)
        return tail.takeIf { it.isNotEmpty() }
    }
}
