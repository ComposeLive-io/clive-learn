# Key Recycling and Tombstones

Two ways to give the items of a sliding load window their Compose groups, run side by side on the same scroll: key recycling, which Clive used before, hands a leaving item's group to an entering one, and tombstones, which it uses now, keep a leaving item's group empty in place. Recycling saves composing entering items but makes Compose move the groups of the items that stay on almost every slide; tombstones never move them, except once per a limited number of scrolled items.

## The problem

Live code composes the load window of each `items(…)` interval as a list of keyed groups, one per item, inside `key(…) { … }`. When the window moves, Compose reconciles the new list of keys with the old one:

- it walks the new keys in order; a key that was there before is found, and if anything is still in front of it, its group is **moved** into place;
- a new key is **inserted** in place, which moves nothing;
- groups whose keys are gone are **removed** only at the end.

Each move is an operation Compose has to apply, on every slide. So what matters is not only how many items enter and leave, but whether the items that stay keep their place.

## Key recycling (before)

Items without keys of their own were keyed by small numbers (shown as letters A, B, C, …):

1. **An index keeps its key while it stays in the window.**
2. **Keys of indexes that left the window go to a queue**, oldest first.
3. **An entering index takes the first key from the queue**, or a new one if the queue is empty. When the leaving item's group still exists, the entering item takes it over and recomposes it with its own data instead of being composed from scratch. That was the point: a window that keeps its size reuses groups instead of disposing and composing them.

What it cost:

- **The groups that stay move.** Sliding forward by one, item 0's key A goes to item 4, so A's group is now last instead of first, and every group that stays is moved in front of it. A window of `n` items slid by `s` moves `n − s` groups; scrolling back by `s` moves `s`.
- **The queue falls behind.** When the window shrinks, released keys stay in the queue with their groups removed. From then on, entering items get keys whose groups are gone: they are composed from scratch, and the groups that stay still move.
- **Keyed items were never recycled.** `items(…, key = { … })` uses the app's keys, so sliding the window removed groups in front of the kept ones and moved all of them.
- In Clive's recycler, the queue received released keys in the order of a hash table rather than by index, so which item takes which group could differ from the player, and slides by several items could move more groups than shown.

## Tombstones (now)

Items without keys are keyed by their index, and items with keys by the app's key:

1. **Groups run from a base to the end of the window.** The groups below the window's first item are tombstones: no content and no widgets.
2. **The base stays put while the window slides forward.** A leaving item keeps its group and loses its content, so nothing is removed in front of the kept groups and nothing moves.
3. **Scrolling back costs nothing either.** An item re-entering the window is composed inside its tombstone; one leaving the end has its group removed from the end.
4. **The base jumps to the window's start** when the window is empty, starts before the base, the data shrank to the base, or there would be more than `max(8 × window size, 64)` tombstones (`max(2 × window size, 4)` in the player). Dropping the tombstones moves the kept groups once.

What it costs: every entering item is composed from scratch, since no group is handed over. In exchange the kept items are never touched, and each tombstone is an empty group of a few dozen bytes. See **3. Tombstones** for the rules in detail and **1. Load Window** for why the window keeps its size.

## What to watch in the player

- **Items by index**: blue is the load window, red the items entering it.
- **Key recycling** and **Tombstones**: the first row is the groups in the order Compose leaves them after its walk, before removing the gone ones (they are last). `C:5` is the group with key C holding item 5, `7` an item keyed by its index, `#7` one keyed by the app's key, `t7` a tombstone. Blue cells hold window items; red ones were moved.
- The second row says what happened to each group: `–` kept as is, `moved`, `new`, `gone`, `reused` (a recycled group now holding another item), `emptied` (an item became a tombstone), `refilled` (a tombstone got its item back).
- Step 1 is the basic trade: three moves and one reuse against one tombstone. Steps 5–8 show the queue falling behind after the window shrinks. Steps 8 and 9 reach the tombstone limit: the base jumps and the new approach moves once. Steps 11–14 are a fling, a jump back, and the interval leaving and re-entering the window. The last three steps repeat the slides with keyed items.
- The log gives each step's moves, reused groups, and items composed and disposed, then totals: without keys, recycling moves 24 groups and tombstones 3, while tombstones compose 8 more items, the 8 that recycling reused.
