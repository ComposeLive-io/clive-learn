# Clive Learn

Step-by-step visualizations of the algorithms inside **Clive** (Compose Live), for learning how it works.

Pick an algorithm in the menu on the left, then press **Play** or step through it. Every visualization runs its JavaScript code in your browser. Edit the code on the right to try your own scenario, then press **Build**.

## Clive Lazy Layout

How a lazy list built in live code decides which items to compose:

1. **Load Window**: how big the window of composed items is, and how it slides as the user scrolls.
2. **Window Across Intervals**: how one window is split across `item {}` and `items()` calls.
3. **Key Recycling**: why a sliding window moves compositions instead of rebuilding them.

---

Built on [Algorithm Visualizer](https://github.com/algorithm-visualizer/algorithm-visualizer) (MIT). Its [tracer API reference](https://github.com/algorithm-visualizer/algorithm-visualizer/wiki) describes what the code can draw.
