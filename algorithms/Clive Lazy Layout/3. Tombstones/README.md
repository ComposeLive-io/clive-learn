# Tombstones

As the load window slides forward, the items that leave its front stay behind as empty Compose groups, called tombstones, so that the groups of the items that stay in the window are never moved. Sliding the window then costs only the items entering and leaving it.

## The problem

Live code composes each item of the window inside `key(…) { … }`. An item without a key of its own is keyed by its index, so item 7 is the same group whichever window it is in. Entering items are composed from scratch and leaving ones are disposed; the items that stay keep their compositions.

That alone isn't enough, because of how Compose reconciles keyed groups. Going through the new groups in order, it **moves** every group that was there before whenever a group that is gone came before it, and removes the gone groups only at the end. When the window `0..24` slides to `1..25`, item 0's group is gone and sits in front of all the others, so all 24 items that stay are moved, on every slide. Inserting a group moves nothing.

## The rule

1. **Groups run from a base to the end of the window.** Live code emits one keyed group per index from `base` to the window's last item. The groups below the window's first item are tombstones: no content and no widgets, so the host still gets just the window's items, in index order.
2. **The base stays put while the window slides forward.** An item leaving the front keeps its group and loses its content, and the next item is appended at the end. No group is removed in front of the kept ones, so nothing moves.
3. **Scrolling back costs nothing either.** An item re-entering the window gets its content again inside its tombstone, composed from scratch. One that leaves the end has its group removed from the end, which moves nothing.
4. **The base jumps to the window's start** when keeping it makes no sense:
   - the window is empty;
   - the window starts before the base (the groups in between are inserted, which moves nothing);
   - the data shrank to the base or below it;
   - there would be more than `max(8 × window size, 64)` tombstones. Dropping them costs one slide with moves, once per that many scrolled items. A jump farther than that keeps no item, so it moves nothing.
5. **Tombstones and items share one `key` call site.** A Compose group's identity includes where `key` was called, so an item turning into a tombstone at a different call site would get a new group and bring the moves back.

The base is updated only once a composition is applied, so a composition that is thrown away leaves it as it was.

## Items with keys

`items(…, key = { … })` gives items keys of the app's own, used instead of the index. A keyed item's composition follows its key when items are inserted, removed or moved around it, and the host keeps the scroll position on it. Tombstones work the same way for keyed items. Unkeyed items are identified by index: when the data changes, each index keeps its composition and recomposes with the data now at it.

## Why the window size still matters

Every entering item is composed from scratch, whatever the window size. A window that keeps its size costs what scrolling brings into view, a window that grows composes many items at once, and one that shrinks throws away items the user may scroll back to. That is why the load window holds its size steady (see **1. Load Window**).

## What to watch in the player

- Row 0 is the interval's groups with tombstones, row 1 the same scroll without them. Numbers are composed items, `t` is a tombstone and a blank cell has no group.
- **Blue** is the window, **red** marks items composed in this step. They are the same in both rows: tombstones change what moves, not what is composed.
- The log gives each step's groups and tombstone count, and the moves with and without tombstones. Steps 1, 2, 5, 6 and 8 move four groups without tombstones and none with them. Step 7 drops 64 tombstones and moves as many groups as it would without them.
