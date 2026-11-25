# Load Window

How Clive's live code decides which items of a `LazyColumn` or `LazyGrid` to compose: everything the host says is on screen, plus half a screen either side, sized once and then slid as the user scrolls.

## Why there is a window

In Clive, live code composes items and sends the resulting widgets to the host app across a bridge. If live code composed only what is on screen, the user would scroll into blank space while the bridge caught up. So live code composes a **window**: the visible items plus a margin either side. Items outside the window are placeholders on the host and are not composed in live code at all.

The work is split by what each side can know:

- **The host** sees the viewport and how big items measured. It reports one thing: the range of items on screen.
- **Live code** knows what an item costs to compose and send. It decides how far to preload around that range.

## The rules

1. **Before any report**, a fixed 30 items are composed from the first visible one. That is enough to fill a phone screen with some to spare. The items composed this way give the host the sizes it needs to send a real report.
2. **Window size** = visible count × (1 + 2 × `viewportsAround`). The default `viewportsAround` is 0.5, so the window is twice the visible count. It is never smaller than the viewport: a window that left out items on screen could never be scrolled to fix itself.
3. **Centred.** The extra items are split evenly above and below the viewport, so scrolling up and scrolling down cost the same. An odd item goes below.
4. **The size grows at once and shrinks only on evidence.** The visible count flickers by one as an item at the edge scrolls in and out (10, 11, 10, ...). A window that followed that count exactly would compose and throw away items on every frame. So the window grows as soon as more is needed, and shrinks only once it wants less than **75%** of its current size, for example after the screen rotates.
5. **A report that reaches the last item is not evidence.** If the visible range ends at the last item, the list is as short as the range, not the screen. So the window does not shrink. When a list is briefly replaced by a single Loading item, the window keeps its size and the real items come back a full screen at once.
6. **Clipped to the list.** The window may run past the end of the list. Only items that exist are composed.

## Why a steady size matters

Every item entering the window is composed from scratch, and every item leaving it is disposed. The items that stay keep their compositions and cost nothing, so a window that keeps its size costs only what scrolling brings into view. A window that grows composes many items at once, and one that shrinks throws away items the user may scroll straight back to. See **3. Tombstones** for how the items that stay are kept from being moved.

## What to watch in the player

- **Blue** cells are on screen, **red** cells were composed from scratch this step, numbers are composed items and `·` are placeholders.
- The chart is the window size after each step. It stays flat while the visible count alternates between 10 and 11 (steps 2 to 7). It drops on rotation (step 8) and holds through the Loading item (step 12).
- The log shows, at every step, how many items were composed and how many disposed.

## Configuring it

```kotlin
// Preload two screens either side instead of half a screen
rememberLazyListState(strategy = ProportionalLoadingStrategy(viewportsAround = 2f))

// Tell the host how big unloaded items are, so it reports an honest visible range from the first frame
LazyColumn { items(1000, estimatedItemHeight = 40.dp) { Row(it) } }
```
