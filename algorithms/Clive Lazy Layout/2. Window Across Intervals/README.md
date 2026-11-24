# Window Across Intervals

The host sees a lazy list as one run of items, but live code builds it from several `item {}` and `items()` calls. One window in the host's indexes is split into a range for each call. Each call then composes only its own slice.

## Intervals

Every `item {}` or `items(count)` call in a `LazyColumn`, `LazyRow` or `LazyGrid` is one **interval**. On the host each interval is its own widget with its own children, and each item inside it has its own index starting from 0. The host, however, lays them out as one list and reports the visible range in **the layout's own index space**: one run of indexes across every interval in order.

The loading strategy (see **1. Load Window**) also works in that single index space. Its window has to be translated back into each interval's own indexes before anything is composed.

## The rule

Walk the intervals in order, keeping track of where each one starts. For each interval:

1. Intersect the window with the interval's span `[start, start + size - 1]`.
2. If they don't overlap, or the interval is empty, the interval composes nothing.
3. Otherwise, subtract `start` to get the range in the interval's own indexes.

That is all. Two consequences fall out of it:

- **A window running past the end of the list simply stops there.** The strategy never has to know how long the list is. Clipping happens here.
- **The result is a plain value.** Two splits with the same ranges are equal. Composition reads it, so when the window has not moved, nothing recomposes.

When intervals use different strategies (`items(…, strategy = …)`), each strategy produces its own window. Each interval takes its slice from its own strategy's window. All strategies hear the same reports, so their windows are centred on the same viewport and differ only in how far they reach.

## What to watch in the player

- Row 0 is the layout's index; the other rows are the intervals, each showing its own indexes under the layout index they occupy.
- **Blue** marks the window in layout indexes, **red** marks what each interval composes.
- The empty `items(0)` interval never composes anything, and the last window runs past the end of the list and is clipped.
