# clive-learn

Material for learning **Clive** (Compose Live).

**Site:** <https://composelive-io.github.io/clive-learn/>

## Algorithm visualizations

Step-by-step visualizations of Clive's algorithms, played in
[Algorithm Visualizer](https://github.com/algorithm-visualizer/algorithm-visualizer):

- **Clive Lazy Layout**
  1. [Load Window](https://composelive-io.github.io/clive-learn/#/clive-lazy-layout/1-load-window): how many items a lazy list composes, and how that window slides.
  2. [Window Across Intervals](https://composelive-io.github.io/clive-learn/#/clive-lazy-layout/2-window-across-intervals): splitting one window across `item {}` and `items()` calls.
  3. [Tombstones](https://composelive-io.github.io/clive-learn/#/clive-lazy-layout/3-tombstones): how sliding the window keeps Compose from moving the items that stay.

## Run locally

Requires Node 20 or newer and JDK 17 or newer (Gradle runs the Kotlin visualizations; `./gradlew` downloads it).

```
npm ci
npm run check   # run the visualizations and their self-checks
npm run build   # build the site into build/site
npm run serve   # http://localhost:8080/
```

See [AGENTS.md](AGENTS.md) for how to add a visualization and how the site is built.
