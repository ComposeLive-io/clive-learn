// import visualization libraries {
import org.algorithm_visualizer.*
import kotlin.math.roundToInt
// }

// The strategy, ported from Clive's live code
// How many items to compose before the host has reported anything.
const val FALLBACK_ITEM_COUNT = 30
// A window only shrinks once it wants to be smaller than this fraction of its current size.
const val SHRINK_THRESHOLD = 0.75f

class ProportionalLoadWindow(viewportsAround: Float = 0.5f) {
    private val viewportsTotal = 1f + 2f * viewportsAround // the viewport plus a margin on each side
    var size = 0                 // remembered across reports
        private set
    var wanted = 0               // what the last report asked for, shown in the log
        private set
    var window: IntRange? = null // in the layout's index space; null until the first report
        private set

    // Called when the host reports what is on screen. Works the window out and stores it.
    fun visibleItemsChanged(firstVisible: Int, lastVisible: Int, itemCount: Int) {
        // A range that reaches the last item is as short as the list, not as the screen.
        val mayShrink = lastVisible < itemCount - 1
        window = windowFor(firstVisible, lastVisible, mayShrink)
    }

    private fun windowFor(firstVisible: Int, lastVisible: Int, mayShrink: Boolean): IntRange {
        val visibleCount = maxOf(1, lastVisible - firstVisible + 1)
        val size = sizeFor(visibleCount, mayShrink)
        // Everything beyond the viewport, split evenly around it; an odd item goes below.
        val padding = size - visibleCount
        val first = maxOf(0, firstVisible - padding / 2)
        return first..<first + size // may run past the end of the list
    }

    private fun sizeFor(visibleCount: Int, mayShrink: Boolean): Int {
        // Never smaller than the viewport.
        wanted = maxOf(visibleCount, (visibleCount * viewportsTotal).roundToInt())
        if (wanted > size || (mayShrink && wanted < size * SHRINK_THRESHOLD)) {
            size = wanted
        }
        return size
    }

    // Called while composing: only reads. Clips the window to the items that exist.
    fun loadWindow(firstVisible: Int, itemCount: Int): IntRange? {
        val window = window ?: firstVisible..<firstVisible + FALLBACK_ITEM_COUNT
        val last = minOf(window.last, itemCount - 1)
        return if (window.first > last) null else window.first..last
    }
}

// The scenario: what the host reports, step by step
// expect: the window composed after the step, worked out by hand. The run fails if the port disagrees.
class Step(val itemCount: Int, val report: IntRange?, val expect: IntRange?, val say: String)

val steps = listOf(
    Step(itemCount = 60, report = null, expect = 0..29,
        say = "First frame. Nothing is laid out yet, so the host has nothing to report: compose 30 items from the first visible one."),
    Step(itemCount = 60, report = 0..9, expect = 0..19,
        say = "The host reports 0..9. Ten visible, half a viewport either side: a window of 20."),
    Step(itemCount = 60, report = 3..13, expect = 0..21,
        say = "Scrolling. An item is cut at the edge, so 11 are visible: the window grows to 22 at once."),
    Step(itemCount = 60, report = 6..15, expect = 0..21,
        say = "Back to 10 visible. 20 is not below 75% of 22, so the size holds."),
    Step(itemCount = 60, report = 10..20, expect = 5..26,
        say = "11 visible again. Same size, so the window only slides."),
    Step(itemCount = 60, report = 14..23, expect = 8..29,
        say = "The visible count keeps alternating 10, 11, 10 ... The window size does not."),
    Step(itemCount = 60, report = 18..28, expect = 13..34, say = "Still sliding at size 22."),
    Step(itemCount = 60, report = 22..31, expect = 16..37, say = "Still sliding at size 22."),
    Step(itemCount = 60, report = 24..28, expect = 22..31,
        say = "The screen rotates: only 5 visible. 10 is below 75% of 22, so the window shrinks."),
    Step(itemCount = 60, report = 24..33, expect = 19..38,
        say = "Rotated back: 10 visible. Growing needs no evidence: 20 at once."),
    Step(itemCount = 60, report = 50..59, expect = 45..59,
        say = "A fling to the end. The window runs past the last item and is clipped to the list."),
    Step(itemCount = 1, report = null, expect = null,
        say = "The data reloads: the list is now one Loading item. The old window 45..64 holds none of it."),
    Step(itemCount = 1, report = 0..0, expect = 0..0,
        say = "The host reports 0..0. It reaches the last item, so it is no evidence of a smaller screen: the size stays 20."),
    Step(itemCount = 60, report = null, expect = 0..19,
        say = "The items are back. The window still has size 20, so a screenful is composed at once, before the next report."),
    Step(itemCount = 60, report = 0..9, expect = 0..19,
        say = "The host reports 0..9. Same window: nothing to compose, nothing to throw away."),
)

fun main() {
    // define tracer variables {
    val chart = ChartTracer("Window size after each step")
    val list = Array1DTracer("Items: number = composed, · = placeholder, blue = on screen, red = composed this step")
    val logger = LogTracer("What happens")
    Layout.setRoot(VerticalLayout(listOf(chart, list, logger)))
    chart.set(steps.map { 0 })
    // }

    val strategy = ProportionalLoadWindow(viewportsAround = 0.5f)
    fun format(range: IntRange?) = range?.toString() ?: "nothing"
    fun IntRange?.has(i: Int) = this != null && i in this
    var composed: IntRange? = null
    var firstVisible = 0
    var previousCount = 0

    steps.forEachIndexed { n, step ->
        // logger {
        logger.println("Step $n: ${step.say}")
        // }
        if (step.report != null) {
            firstVisible = step.report.first
            val sizeBefore = strategy.size
            strategy.visibleItemsChanged(step.report.first, step.report.last, step.itemCount)
            // logger {
            logger.println("  report ${format(step.report)} of ${step.itemCount}: wants ${strategy.wanted}, size $sizeBefore → ${strategy.size}")
            // }
        }
        val window = strategy.loadWindow(firstVisible, step.itemCount)

        // self-check {
        check(window == step.expect) { "Step $n: expected window ${format(step.expect)}, got ${format(window)}" }
        // }

        // Compare with what was composed before. An item entering the window is composed from scratch
        // and one leaving it is disposed; the items that stay cost nothing.
        var entered = 0
        var left = 0
        for (i in 0..<maxOf(step.itemCount, previousCount)) {
            if (window.has(i) && !composed.has(i)) entered++
            if (composed.has(i) && !window.has(i)) left++
        }

        // visualize {
        list.set(List(step.itemCount) { i -> if (window.has(i)) i else "·" })
        for (i in 0..<step.itemCount) if (window.has(i) && !composed.has(i)) list.patch(i)
        if (step.report != null) list.select(step.report.first, step.report.last)
        chart.patch(n, if (strategy.window != null) strategy.size else FALLBACK_ITEM_COUNT)
        // }
        // logger {
        logger.println("  window ${format(window)}: $entered composed, $left disposed")
        // }
        Tracer.delay()
        // visualize {
        chart.depatch(n)
        // }
        composed = window
        previousCount = step.itemCount
    }
}
