// import visualization libraries {
import org.algorithm_visualizer.*
// }

// The old approach: key recycling, ported from Clive's live code before tombstones
// Items without keys of their own were keyed by small numbers handed out by this recycler. When the
// window moved, the numbers of the indexes that left it went to a queue, and indexes entering the
// window took numbers from that queue first, so an entering item took over the Compose group of an
// item that left. The player shows the numbers as letters: 0 = A, 1 = B, ...
class LazyLayoutKeyRecycler {
    private var counter = 0
    private val currentKeys = mutableMapOf<Int, Int>() // index -> key
    private val keysRecyclePool = mutableListOf<Int>()   // released keys, oldest first

    // Called with the window before its items are keyed.
    // Clive's map releases the keys in the order of its hash table; this port releases them by index,
    // which is the order that moves the fewest groups.
    fun loadRangeUpdated(first: Int, last: Int) {
        for (index in currentKeys.keys.sorted()) {
            if (index !in first..last) keysRecyclePool.add(currentKeys.remove(index)!!)
        }
    }

    fun keyFor(index: Int): Int = currentKeys.getOrPut(index) {
        if (keysRecyclePool.isNotEmpty()) keysRecyclePool.removeAt(0) else counter++
    }
}

// The new approach: tombstones, ported from Clive's live code
// Items without keys are keyed by their index, and groups run from `base` to the window's last
// item. Groups below the window's first item are tombstones: empty groups kept so that the groups
// after them stay where they are.
class TombstoneBase {
    var base = -1 // as of the last applied composition; -1 before the first one

    fun baseFor(loadRangeFirst: Int, loadRangeSize: Int, intervalSize: Int): Int {
        val previous = base
        return when {
            // No window, the first window, or a window starting before the base: start at the window.
            loadRangeSize <= 0 || previous < 0 || previous > loadRangeFirst -> loadRangeFirst
            // The data shrank to the base or below it.
            previous >= intervalSize -> loadRangeFirst
            // Too many tombstones: drop them all at once.
            loadRangeFirst - previous > maxTombstones(loadRangeSize) -> loadRangeFirst
            // Otherwise the base stays put and the window slides away from it.
            else -> previous
        }
    }

    companion object {
        // Clive uses 8 and 64; they are smaller here so the limit shows up within a few steps.
        const val MAX_TOMBSTONES_PER_ITEM = 2
        const val MIN_MAX_TOMBSTONES = 4

        fun maxTombstones(loadRangeSize: Int) = maxOf(MAX_TOMBSTONES_PER_ITEM * loadRangeSize, MIN_MAX_TOMBSTONES)
    }
}

// The groups each approach asks Compose for, in order
// A group has a key and holds one item's content, or none for a tombstone (item = null).
// Keys are shown as text: a recycled key as a letter, an index as a number, an app's own key as #index.
data class Group(val key: String, val item: Int?)

fun keyedKey(index: Int) = "#$index" // items(…, key = { … }): the app's own key of the item at this index

fun oldGroups(recycler: LazyLayoutKeyRecycler, window: IntRange, keyed: Boolean): List<Group> {
    if (!keyed) recycler.loadRangeUpdated(window.first, window.last)
    return window.map { index ->
        Group(if (keyed) keyedKey(index) else ('A' + recycler.keyFor(index)).toString(), index)
    }
}

fun newGroups(base: Int, window: IntRange, keyed: Boolean): List<Group> =
    (base..window.last).map { index ->
        Group(if (keyed) keyedKey(index) else "$index", if (index >= window.first) index else null)
    }

// How Compose reconciles keyed groups (a model of Compose, not Clive code)
// Compose walks the new groups in order. A group whose key was there before is found and, if
// anything is still in front of it, moved into place: one MoveCurrentGroup. A new key is inserted
// in place, which moves nothing. Groups whose keys are gone are removed only at the end.
enum class Change { Kept, Moved, Inserted, Removed }

class Reconciled(val group: Group, val before: Group?, val change: Change)

fun reconcile(before: List<Group>, after: List<Group>): List<Reconciled> {
    val slots = before.toMutableList() // the slot table as Compose rewrites it
    val result = mutableListOf<Reconciled>()
    after.forEachIndexed { position, group ->
        val found = slots.indexOfFirst { it.key == group.key }
        if (found < 0) {
            slots.add(position, group)
            result += Reconciled(group, null, Change.Inserted)
        } else {
            val old = slots.removeAt(found)
            slots.add(position, group)
            result += Reconciled(group, old, if (found == position) Change.Kept else Change.Moved)
        }
    }
    for (gone in slots.drop(after.size)) result += Reconciled(gone, gone, Change.Removed)
    return result
}

// What a step cost: groups moved, groups that changed item (old only), items composed from scratch,
// and items whose content was thrown away.
data class Cost(val moved: Int, val reused: Int, val composed: Int, val disposed: Int)

fun costOf(steps: List<Reconciled>): Cost {
    val kept = steps.filter { it.change == Change.Kept || it.change == Change.Moved }
    return Cost(
        moved = steps.count { it.change == Change.Moved },
        reused = kept.count { it.group.item != null && it.before?.item != null && it.group.item != it.before.item },
        composed = steps.count { it.change != Change.Removed && it.group.item != null && it.before?.item == null },
        disposed = steps.count { it.before?.item != null && (it.change == Change.Removed || it.group.item == null) },
    )
}

// The scenario: one interval of 20 items, first without keys, then with them
const val ITEM_COUNT = 20

// expect, worked out by hand:
// old = listOf(moved, reused, composed, disposed); new = listOf(base, moved, composed, disposed)
class Step(val window: IntRange, val old: List<Int>, val new: List<Int>, val say: String)

val unkeyedSteps = listOf(
    Step(0..3, old = listOf(0, 0, 4, 0), new = listOf(0, 0, 4, 0),
        say = "First window, items 0–3. Both approaches compose four groups."),
    Step(1..4, old = listOf(3, 1, 0, 0), new = listOf(0, 0, 1, 1),
        say = "Slide by one. Old: item 4 takes key A from item 0, whose group now sits in front of the three " +
            "that stay, so they are moved. New: item 0 stays as an empty tombstone and item 4 is appended."),
    Step(3..6, old = listOf(2, 2, 0, 0), new = listOf(0, 0, 2, 2),
        say = "Slide by two. Old: items 5 and 6 take keys B and C; the two items that stay move. " +
            "New: items 1 and 2 become tombstones, nothing moves."),
    Step(2..5, old = listOf(1, 1, 0, 0), new = listOf(0, 0, 1, 1),
        say = "Scroll back by one. Old: item 2 takes key C from item 6; that group was at the end, so it is moved to the front. " +
            "New: item 2 is composed again inside its tombstone and item 6's group goes from the end."),
    Step(2..7, old = listOf(0, 0, 2, 0), new = listOf(0, 0, 2, 0),
        say = "The window grows to six items. No key is free, so both approaches append two new groups."),
    Step(4..7, old = listOf(4, 0, 0, 2), new = listOf(0, 0, 0, 2),
        say = "It shrinks back from the front. Old: keys C and D are released, but nothing takes them, so their groups " +
            "are removed and all four items that stay move. New: items 2 and 3 become tombstones."),
    Step(5..8, old = listOf(3, 0, 1, 1), new = listOf(0, 0, 1, 1),
        say = "Slide by one. Old: item 8 gets key C from the front of the queue, but C's group is already gone, " +
            "so item 8 is composed from scratch while A's group is removed. Moves without any reuse."),
    Step(7..10, old = listOf(2, 0, 2, 2), new = listOf(0, 0, 2, 2),
        say = "Slide by two: seven tombstones, within the limit of max(2 × 4, 4) = 8. Old: the queue still hands out " +
            "keys of removed groups, two behind, so it composes and moves."),
    Step(8..11, old = listOf(3, 0, 1, 1), new = listOf(0, 0, 1, 1),
        say = "Eight tombstones: exactly the limit."),
    Step(9..12, old = listOf(3, 0, 1, 1), new = listOf(9, 3, 1, 1),
        say = "Nine would be over the limit, so the tombstones are dropped and the groups start at the window. " +
            "This one slide moves the three items that stay, as the old approach does on every slide."),
    Step(10..13, old = listOf(3, 0, 1, 1), new = listOf(9, 0, 1, 1),
        say = "Back to sliding with no moves."),
    Step(15..18, old = listOf(0, 2, 2, 2), new = listOf(9, 0, 4, 4),
        say = "A fling: the window jumps ahead. Old: the whole window is released; items 17 and 18 take over the groups " +
            "of 10 and 11, the other two come from removed keys. New: six tombstones, within the limit, " +
            "so all four items are composed from scratch after them."),
    Step(3..6, old = listOf(0, 2, 2, 2), new = listOf(3, 0, 4, 4),
        say = "A jump back, before the base. New: the groups start at the window; nothing is kept, so nothing moves. " +
            "Old: two items reuse groups, two are new."),
    Step(IntRange.EMPTY, old = listOf(0, 0, 0, 4), new = listOf(1, 0, 0, 4),
        say = "The interval scrolls out of the window, so it composes no items. Both remove all groups. " +
            "The new approach's base becomes 1, the first index of an empty range."),
    Step(4..7, old = listOf(0, 0, 4, 0), new = listOf(1, 0, 4, 0),
        say = "It comes back at items 4–7. New: the base 1 is within the limit, so tombstones 1–3 are inserted " +
            "before the items; inserting moves nothing. Old: four new groups."),
)
// Over the unkeyed scroll, worked out by hand: listOf(moved, reused, composed) old, then listOf(moved, composed) new.
val expectOldTotal = listOf(24, 8, 20)
val expectNewTotal = listOf(3, 28)

val keyedSteps = listOf(
    Step(0..3, old = listOf(0, 0, 4, 0), new = listOf(0, 0, 4, 0),
        say = "Now items(…, key = { … }): groups are keyed by the app's keys (#index). Key recycling never " +
            "applied to them."),
    Step(1..4, old = listOf(3, 0, 1, 1), new = listOf(0, 0, 1, 1),
        say = "Slide by one. Old: item 0's group is removed in front of the others, so they move. " +
            "New: tombstones work the same with the app's keys."),
    Step(3..6, old = listOf(2, 0, 2, 2), new = listOf(0, 0, 2, 2),
        say = "Slide by two: two moves with the old approach, none with tombstones."),
)

fun main() {
    // define tracer variables {
    val items = Array1DTracer("Items by index: blue = load window, red = entering it")
    val old = Array2DTracer("Key recycling: groups in Compose's order, then what happened to each. Red = moved")
    val new = Array2DTracer("Tombstones: groups in Compose's order (tN = tombstone), then what happened to each. Red = moved")
    val logger = LogTracer("What Compose does")
    Layout.setRoot(VerticalLayout(listOf(items, old, new, logger)))
    // }

    // visualize {
    fun label(group: Group) = when {
        group.item == null -> "t${group.key}"
        group.key.removePrefix("#") == "${group.item}" -> group.key
        else -> "${group.key}:${group.item}"
    }

    fun what(step: Reconciled): String = when (step.change) {
        Change.Inserted -> "new"
        Change.Removed -> "gone"
        else -> listOfNotNull(
            "moved".takeIf { step.change == Change.Moved },
            when {
                step.before?.item == null && step.group.item != null -> "refilled"
                step.before?.item != null && step.group.item == null -> "emptied"
                step.before?.item != step.group.item -> "reused"
                else -> null
            },
        ).joinToString("+").ifEmpty { "–" }
    }

    fun draw(tracer: Array2DTracer, steps: List<Reconciled>, width: Int) {
        val cells = steps.map { label(it.group) } + List(width - steps.size) { "" }
        val changes = steps.map(::what) + List(width - steps.size) { "" }
        tracer.set(listOf(cells, changes))
        steps.forEachIndexed { i, step ->
            if (step.change == Change.Moved) {
                tracer.patch(0, i)
                tracer.patch(1, i)
            } else if (step.change != Change.Removed && step.group.item != null) {
                tracer.select(0, i)
            }
        }
    }
    // }

    fun play(title: String, steps: List<Step>, keyed: Boolean): Pair<List<Cost>, List<Cost>> {
        val recycler = LazyLayoutKeyRecycler()
        val tombstones = TombstoneBase()
        var window: IntRange = IntRange.EMPTY
        var oldBefore = emptyList<Group>()
        var newBefore = emptyList<Group>()
        val oldCosts = mutableListOf<Cost>()
        val newCosts = mutableListOf<Cost>()
        // logger {
        logger.println(title)
        // }

        steps.forEachIndexed { n, step ->
            val next = step.window
            val oldAfter = oldGroups(recycler, next, keyed)
            val base = tombstones.baseFor(next.first, next.last - next.first + 1, ITEM_COUNT)
            val newAfter = newGroups(base, next, keyed)
            val oldSteps = reconcile(oldBefore, oldAfter)
            val newSteps = reconcile(newBefore, newAfter)
            val oldCost = costOf(oldSteps)
            val newCost = costOf(newSteps)

            // self-check {
            val gotOld = listOf(oldCost.moved, oldCost.reused, oldCost.composed, oldCost.disposed)
            val gotNew = listOf(base, newCost.moved, newCost.composed, newCost.disposed)
            check(gotOld == step.old) { "$title, step $n: old moved/reused/composed/disposed expected ${step.old}, got $gotOld" }
            check(gotNew == step.new) { "$title, step $n: new base/moved/composed/disposed expected ${step.new}, got $gotNew" }
            // }

            tombstones.base = base // the SideEffect once the composition is applied
            // visualize {
            items.set(List(ITEM_COUNT) { it })
            if (!next.isEmpty()) items.select(next.first, next.last)
            for (i in next) if (i !in window) items.patch(i)
            draw(old, oldSteps, 8)
            draw(new, newSteps, 14)
            // }
            // logger {
            val shown = if (next.isEmpty()) "empty" else "${next.first}–${next.last}"
            logger.println("Step $n, window $shown. ${step.say}")
            logger.println("  old: ${oldCost.moved} moved, ${oldCost.reused} reused, ${oldCost.composed} composed, ${oldCost.disposed} disposed")
            logger.println("  new: ${newCost.moved} moved, ${newCost.composed} composed, ${newCost.disposed} disposed; " +
                "base ${base}, ${if (next.isEmpty()) 0 else next.first - base} tombstones")
            // }
            Tracer.delay()

            window = next
            oldBefore = oldAfter
            newBefore = newAfter
            oldCosts += oldCost
            newCosts += newCost
        }
        return oldCosts to newCosts
    }

    val (oldCosts, newCosts) = play("Items without keys", unkeyedSteps, keyed = false)
    val oldTotal = listOf(oldCosts.sumOf { it.moved }, oldCosts.sumOf { it.reused }, oldCosts.sumOf { it.composed })
    val newTotal = listOf(newCosts.sumOf { it.moved }, newCosts.sumOf { it.composed })
    // self-check {
    check(oldTotal == expectOldTotal) { "old totals: expected $expectOldTotal, got $oldTotal" }
    check(newTotal == expectNewTotal) { "new totals: expected $expectNewTotal, got $newTotal" }
    // }
    // logger {
    logger.println("Without keys, over the whole scroll: key recycling moved ${oldTotal[0]} groups and reused ${oldTotal[1]}, " +
        "tombstones moved ${newTotal[0]}. Tombstones composed ${newTotal[1] - oldTotal[2]} items more: the ones recycling reused.")
    // }
    Tracer.delay()

    play("Items with keys", keyedSteps, keyed = true)
    // logger {
    logger.println("With keys, nothing is reused either way; tombstones only remove the moves.")
    // }
    Tracer.delay()
}
