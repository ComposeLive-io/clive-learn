# Clive Learn

Step-by-step visualizations of the algorithms inside **Clive** (Compose Live), for learning how it works.

Pick an algorithm in the menu on the left, then press **Play** or step through it. The code on the right is the algorithm and the scenario it plays, and the player highlights the line behind each step. Visualizations written in Kotlin were recorded when the site was built, so editing them has no effect; JavaScript ones run in your browser, so you can edit the code and press **Build** to try your own scenario.

## Clive Lazy Layout

How a lazy list built in live code decides which items to compose:

1. **Load Window**: how big the window of composed items is, and how it slides as the user scrolls.
2. **Window Across Intervals**: how one window is split across `item {}` and `items()` calls.
3. **Tombstones**: how a sliding window keeps Compose from moving the items that stay.

---

Built on [Algorithm Visualizer](https://github.com/algorithm-visualizer/algorithm-visualizer) (MIT). Its [tracer API reference](https://github.com/algorithm-visualizer/algorithm-visualizer/wiki) describes what the code can draw.
