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

    private val tickSteps = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600)

    /** Round tick times from 0 up to [durationSeconds], at most [maxTicks] of them. */
    fun axisTicks(durationSeconds: Double, maxTicks: Int = 8): List<Double> {
        val step = tickSteps.firstOrNull { durationSeconds / it <= maxTicks - 1 } ?: tickSteps.last()
        return generateSequence(0.0) { it + step }.takeWhile { it <= durationSeconds + 1e-9 }.toList()
    }

    /** Which of the label anchors [xs] (ascending) to draw so that drawn labels stay [minGap] apart. */
    fun thinLabels(xs: List<Int>, minGap: Int): List<Boolean> {
        var lastKept = Int.MIN_VALUE / 2
        return xs.map { x -> (x - lastKept >= minGap).also { if (it) lastKept = x } }
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
