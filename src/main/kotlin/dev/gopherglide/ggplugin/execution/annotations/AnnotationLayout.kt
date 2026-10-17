package dev.gopherglide.ggplugin.execution.annotations

/** The horizontal extent of a drawn label, in pixels. */
data class LabelSpan(val left: Int, val width: Int) {
    val right: Int get() = left + width
}

object AnnotationLayout {
    /**
     * Left edge for a label anchored at [x]: just right of the anchor, or left of it when that would
     * run past [maxX]; never before [minX].
     */
    fun labelLeft(x: Int, width: Int, minX: Int, maxX: Int, gap: Int = 3): Int {
        val right = x + gap
        val left = if (right + width <= maxX) right else x - gap - width
        return left.coerceAtLeast(minX)
    }

    /**
     * Gives each span, in order, the first of [rows] rows where it stays [gap] clear of the spans
     * already there, or null when every row is taken.
     */
    fun assignRows(spans: List<LabelSpan>, rows: Int, gap: Int = 4): List<Int?> {
        val placed = List(rows) { mutableListOf<LabelSpan>() }
        return spans.map { span ->
            val row = placed.indexOfFirst { inRow -> inRow.none { span.left < it.right + gap && it.left < span.right + gap } }
            if (row >= 0) placed[row].add(span)
            row.takeIf { it >= 0 }
        }
    }
}
