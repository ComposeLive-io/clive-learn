// import visualization libraries {
import org.algorithm_visualizer.*
// }

// Splitting one window into a range per interval, ported from Clive's live code
// intervals: the size of each item()/items() call, in order.
// window: in the layout's own index space, the one the host reports in.
// Returns one range per interval in that interval's own indexes, or null if it has none.
fun spanning(window: IntRange, intervals: List<Int>): List<IntRange?> {
    val ranges = mutableListOf<IntRange?>()
    var intervalStart = 0
    for (size in intervals) {
        val first = maxOf(window.first, intervalStart)
        val last = minOf(window.last, intervalStart + size - 1)
        ranges += if (size == 0 || first > last) null else (first - intervalStart)..(last - intervalStart)
        intervalStart += size
    }
    return ranges
}

/* The layout:
  LazyColumn {
    item { Header() }
    items(20) { Product(it) }
    item { Loading() }
    items(searchResults.size) { Result(it) }   // no results yet
    items(15) { Recommendation(it) }
  }
*/
class Interval(val name: String, val size: Int)

val intervals = listOf(
    Interval("item { Header }", 1),
    Interval("items(20) Product", 20),
    Interval("item { Loading }", 1),
    Interval("items(0) Result", 0),
    Interval("items(15) Recommendation", 15),
)
val itemCount = intervals.sumOf { it.size }

// Windows the strategy might ask for as the user scrolls, and what each interval should get.
class Case(val window: IntRange, val expect: List<IntRange?>)

val cases = listOf(
    Case(window = 0..9, expect = listOf(0..0, 0..8, null, null, null)),
    Case(window = 12..31, expect = listOf(null, 11..19, 0..0, null, 0..9)),
    Case(window = 22..41, expect = listOf(null, null, null, null, 0..14)),
)

fun main() {
    // define tracer variables {
    val grid = Array2DTracer("Row 0: layout index. Other rows: each interval's own index. Blue = window, red = composed")
    val logger = LogTracer("What each interval composes")
    Layout.setRoot(VerticalLayout(listOf(grid, logger)))
    val rows = mutableListOf<List<Any>>(List(itemCount) { it })
    var start = 0
    for (interval in intervals) {
        rows += List(itemCount) { i -> if (i >= start && i < start + interval.size) i - start else "" }
        start += interval.size
    }
    grid.set(rows)
    logger.println("${intervals.size} intervals, $itemCount items: ${intervals.joinToString(", ") { "${it.name} (${it.size})" }}")
    Tracer.delay()
    // }

    fun format(range: IntRange?) = range?.toString() ?: "nothing"
    val sizes = intervals.map { it.size }

    for (case in cases) {
        val window = case.window
        val expect = case.expect
        val ranges = spanning(window, sizes)

        // self-check {
        check(ranges == expect) {
            "Window ${format(window)}: expected ${expect.joinToString(", ") { format(it) }}, got ${ranges.joinToString(", ") { format(it) }}"
        }
        // }

        // visualize {
        grid.selectRow(0, window.first, minOf(window.last, itemCount - 1))
        val pastEnd = if (window.last >= itemCount) " runs past the last item (${itemCount - 1})" else ""
        logger.println("Window ${format(window)}$pastEnd:")
        Tracer.delay()
        // }
        var intervalStart = 0
        ranges.forEachIndexed { i, range ->
            // visualize {
            if (range != null) {
                for (j in range) grid.patch(i + 1, intervalStart + j)
            }
            logger.println("  ${intervals[i].name}: ${format(range)}")
            Tracer.delay()
            // }
            intervalStart += sizes[i]
        }
        // visualize {
        intervalStart = 0
        ranges.forEachIndexed { i, range ->
            if (range != null) {
                for (j in range) grid.depatch(i + 1, intervalStart + j)
            }
            intervalStart += sizes[i]
        }
        grid.deselectRow(0, window.first, minOf(window.last, itemCount - 1))
        // }
    }
}
