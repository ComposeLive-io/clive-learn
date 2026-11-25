// import visualization libraries {
import org.algorithm_visualizer.*
// }

// Where an interval's item groups start, ported from Clive's live code
// Live code emits one keyed group per index from `base` to the window's last item. The groups below
// the window's first item are tombstones: items that left the front of the window, kept as empty
// groups so that the groups after them stay where they are.
class TombstoneBase {
    var base = -1 // as of the last applied composition; -1 before the first one
        private set

    // Where the groups start for a window of loadRangeSize items from loadRangeFirst.
    fun baseFor(loadRangeFirst: Int, loadRangeSize: Int, intervalSize: Int): Int {
        val previous = base
        return when {
            // No window, the first window, or a window starting before the base: start at the window.
            // The groups between it and the old base are inserted, and insertions move nothing.
            loadRangeSize <= 0 || previous < 0 || previous > loadRangeFirst -> loadRangeFirst
            // The data shrank to the base or below it.
            previous >= intervalSize -> loadRangeFirst
            // Too many tombstones: drop them all at once.
            loadRangeFirst - previous > maxTombstones(loadRangeSize) -> loadRangeFirst
            // Otherwise the base stays put and the window slides away from it.
            else -> previous
        }
    }

    // Called once the composition is applied, so a discarded composition changes nothing.
    fun applied(base: Int) {
        this.base = base
    }

    companion object {
        const val MAX_TOMBSTONES_PER_ITEM = 8
        const val MIN_MAX_TOMBSTONES = 64

        fun maxTombstones(loadRangeSize: Int) = maxOf(MAX_TOMBSTONES_PER_ITEM * loadRangeSize, MIN_MAX_TOMBSTONES)
    }
}

// How Compose reconciles keyed groups, reduced to what matters here (a model, not Clive code)
// Going through the new groups in order, a group that was there before is moved if a group that is
// gone came before it. Gone groups are removed at the end, and new groups are inserted where they
// belong, which moves nothing.
fun movesFor(before: List<Int>, after: List<Int>): Int {
    val gone = before.filter { it !in after }
    return after.count { key -> key in before && gone.any { before.indexOf(it) < before.indexOf(key) } }
}

// The scenario: one items(200) interval, unkeyed, so each item's key is its index
const val ITEM_COUNT = 200
const val COLUMNS = 76 // how many indexes the player shows

// expect, worked out by hand:
// listOf(base, composed, disposed, moves with tombstones, moves without tombstones)
class Step(val window: IntRange, val expect: List<Int>, val say: String)

val steps = listOf(
    Step(window = 0..5, expect = listOf(0, 6, 0, 0, 0), say = "First window: six items, six groups."),
    Step(window = 2..7, expect = listOf(0, 2, 2, 0, 4),
        say = "Slide by two. Items 0 and 1 leave and stay as tombstones; 6 and 7 are appended. " +
            "Without tombstones, the groups of 0 and 1 would be removed and the four that stay would move."),
    Step(window = 4..9, expect = listOf(0, 2, 2, 0, 4), say = "And again: four tombstones, still no moves."),
    Step(window = 3..8, expect = listOf(0, 1, 1, 0, 0),
        say = "Scroll back by one. Item 3 is composed again inside its tombstone; item 9's group goes from the end. " +
            "Nothing moves either way."),
    Step(window = 60..65, expect = listOf(0, 6, 6, 0, 0),
        say = "A fling: 60 tombstones. Six new items are composed and the old six disposed."),
    Step(window = 62..67, expect = listOf(0, 2, 2, 0, 4),
        say = "Sliding on: 62 tombstones, within the limit of max(8 × 6, 64) = 64."),
    Step(window = 64..69, expect = listOf(0, 2, 2, 0, 4), say = "64 tombstones: exactly the limit."),
    Step(window = 66..71, expect = listOf(66, 2, 2, 4, 4),
        say = "66 would be over the limit, so the tombstones are dropped and the groups start at the window. " +
            "This one slide moves the four items that stay, as it would without tombstones."),
    Step(window = 68..73, expect = listOf(66, 2, 2, 0, 4), say = "Back to sliding with no moves."),
    Step(window = 30..35, expect = listOf(30, 6, 6, 0, 0),
        say = "A jump back to before the base: the groups start at the window again. Nothing is kept, so nothing moves."),
)
// Over the whole scroll, worked out by hand: moves with tombstones, moves without.
val expectTotal = listOf(4, 24)

fun main() {
    // define tracer variables {
    val grid = Array2DTracer("Row 0: with tombstones. Row 1: without. number = composed item, t = tombstone, blank = no group. Blue = window, red = composed this step")
    val logger = LogTracer("What Compose does")
    Layout.setRoot(VerticalLayout(listOf(grid, logger)))
    grid.set(List(2) { List(COLUMNS) { "" } })
    // }

    val tombstones = TombstoneBase()
    var window: IntRange = IntRange.EMPTY
    var groups = emptyList<Int>()        // keys of the item groups, with tombstones
    var groupsWithout = emptyList<Int>() // the same without tombstones: the window's items only
    var totalMoves = 0
    var totalMovesWithout = 0

    steps.forEachIndexed { n, step ->
        // logger {
        logger.println("Step $n, window ${step.window}: ${step.say}")
        // }
        val next = step.window
        val base = tombstones.baseFor(next.first, next.last - next.first + 1, ITEM_COUNT)
        val nextGroups = (base..next.last).toList()
        val nextGroupsWithout = next.toList()

        val composed = next.count { it !in window }
        val disposed = window.count { it !in next }
        val moves = movesFor(groups, nextGroups)
        val movesWithout = movesFor(groupsWithout, nextGroupsWithout)

        // self-check {
        val got = listOf(base, composed, disposed, moves, movesWithout)
        check(got == step.expect) {
            "Step $n: expected base/composed/disposed/moves/without ${step.expect.joinToString("/")}, got ${got.joinToString("/")}"
        }
        // }

        tombstones.applied(base)
        // visualize {
        fun row(keys: List<Int>) = List(COLUMNS) { i -> if (i !in keys) "" else if (i in next) i else "t" }
        grid.set(listOf(row(nextGroups), row(nextGroupsWithout)))
        grid.selectRow(0, next.first, next.last)
        grid.selectRow(1, next.first, next.last)
        for (i in next) if (i !in window) {
            grid.patch(0, i)
            grid.patch(1, i)
        }
        // }
        // logger {
        logger.println("  groups $base..${next.last} (${next.first - base} tombstones): $composed composed, $disposed disposed; $moves moved, without tombstones $movesWithout")
        // }
        Tracer.delay()

        window = next
        groups = nextGroups
        groupsWithout = nextGroupsWithout
        totalMoves += moves
        totalMovesWithout += movesWithout
    }

    // self-check {
    check(listOf(totalMoves, totalMovesWithout) == expectTotal) {
        "Total moves: expected ${expectTotal.joinToString("/")}, got $totalMoves/$totalMovesWithout"
    }
    // }
    // logger {
    logger.println("Over the whole scroll: $totalMoves moves with tombstones, $totalMovesWithout without.")
    // }
    Tracer.delay()
}
