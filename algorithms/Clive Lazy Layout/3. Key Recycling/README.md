# Key Recycling

As the load window slides, the item leaving it hands its Compose key to the item entering it, so Compose moves an existing composition instead of disposing one and composing another. This is what makes a window of steady size cheap to scroll.

## The problem

Live code composes every item of the window inside `key(…) { … }`. If the key were the item's index, scrolling by one would dispose the composition at the top and create a new one at the bottom: state, effects and widgets torn down and built again, and all of it sent across the bridge.

## The rule

For items without keys of their own, keys are handed out by a recycler. Before each composition of the window:

1. **Give back.** Every index outside the new window returns its key to a pool.
2. **Hand out**, for each index in the window, in order:
   - an index that already had a key keeps it;
   - otherwise it takes a key from the pool;
   - otherwise it gets a brand new key.

When the window `0..7` slides to `1..8`, the set of keys is unchanged and only one key changes index. Compose sees a **move** of an existing group and recomposes it with the data at its new index. The host receives a move of one child and the properties that changed.

What the window size does to the cost:

| Window          | Keys                         | Compose                           |
|-----------------|------------------------------|-----------------------------------|
| slides          | move from leaving to entering| moves groups, nothing new         |
| grows           | pool is empty, new keys      | composes new groups from scratch  |
| shrinks         | go to the pool, unused       | disposes their groups             |

This is why the load window holds its size steady (see **1. Load Window**).

A key that sits in the pool through a composition is not emitted, so its group is disposed. A key taken from the pool later starts a fresh composition. The pool saves key numbers, not compositions.

Which pooled key goes to which entering index is not part of the contract: the order depends on how the map of current keys is iterated. The player hands them out in index order.

## Items with keys

`items(…, key = { … })` gives items keys of the app's own, and those bypass the recycler. A keyed item's composition follows its key when items are inserted, removed or moved around it, and the host keeps the scroll position on it. Unkeyed items are identified by position. When the data changes, each position keeps its composition and recomposes with the new data at it.

## What to watch in the player

- The top row is each item's key (`·` = not composed). **Blue** is the window, **red** marks indexes whose key changed in this step.
- The second row is the pool.
- The log counts moves, compositions from scratch and disposals for each step.
