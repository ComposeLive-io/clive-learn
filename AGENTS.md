# clive-learn

Material for learning **Clive** (Compose Live): how its runtime, protocol and widgets work. Each
piece explains one mechanism so that someone new to Clive can build an accurate picture of it.

Published at <https://composelive-io.github.io/clive-learn/>.

## Content types

| Type                     | Where                                     | Status |
|--------------------------|-------------------------------------------|--------|
| Algorithm visualizations | `algorithms/<Category>/<Algorithm>/`      | active |

Add a row when a new content type is introduced, with its folder and how it is checked and published.

## This repository is public

The repo and its GitHub Pages site are public; Clive's source is not. In every file, commit message,
PR and issue:

- Explain mechanisms **in your own words**. Do not paste or closely paraphrase Clive source comments,
  KDoc or internal design docs.
- No internal file paths, line numbers, repository or GitLab URLs, tracker issue keys, internal host
  names, or company or team names.
- Public API names (`LazyColumn`, `items(key = …)`, `rememberLazyListState(strategy = …)`) and the
  algorithm itself are fine: they are what is being taught.

## Algorithm visualizations

They run in [algorithm-visualizer](https://github.com/algorithm-visualizer/algorithm-visualizer):
the code is JavaScript run in a browser Web Worker against the tracers library
[`algorithm-visualizer@2.3.9`](https://github.com/algorithm-visualizer/tracers.js)
([API reference](https://github.com/algorithm-visualizer/algorithm-visualizer/wiki)).

### Layout

```
algorithms/<Category>/<N. Algorithm>/
  code.js     the visualization
  README.md   the explanation; its first paragraph becomes the page description
```

The folder names are what the menu shows; keys come from them (lower case, spaces → `-`, other
punctuation dropped). Number the algorithms of a category (`1. `, `2. `) in the order they should be
read, since the menu sorts by name.

### `code.js`

Structure it in this order. The editor folds every line comment that ends in `{` up to its `// }`,
so wrap plumbing in `// name {` … `// }` blocks, and keep the port and the scenario, which are what
the reader came for, under plain comment headers with no `{`. Code samples in comments go in
`/* */`, since a `// … {` line would fold:

1. `// import visualization libraries {`: `require('algorithm-visualizer')`, the only module available.
2. **The port**: the algorithm as plain JS, free of tracer calls, mirroring Clive's behaviour exactly:
   the same arithmetic, rounding (Kotlin `roundToInt` = `Math.round` for positives, `/` on `Int` =
   `Math.floor` for non-negatives), clamping and order of operations. Keep Clive's names for
   concepts the reader will meet in the API.
3. **The scenario**: the inputs, each with an `expect` worked out **by hand**, not by running the port.
4. Tracers and `Layout.setRoot(...)`, then the loop that runs the scenario through the port and draws it.
5. `// self-check {` blocks that `throw` when the port disagrees with `expect`. They make CI prove the
   port; never weaken one to make it pass. Find out which side is wrong.

Drawing conventions: blue (`select`) for the current state (visible range, window), red (`patch`) for
what changed in this step. A `LogTracer` narrates each step in one or two plain sentences. Call
`Tracer.delay()` once per step a reader should pause on. The player highlights the line that made it.
Stay well under 20,000 commands (`npm run check` enforces it).

### `README.md`

1. `# Title` and a one-paragraph summary (the page description).
2. The problem the algorithm solves in Clive and why, for someone who knows Jetpack Compose but not Clive.
3. The rules, numbered, each with the reason for it.
4. Links to related visualizations by name (`see **3. Key Recycling**`).
5. "What to watch in the player": what the colours and rows mean and which steps show what.
6. How an app configures it, if it can, using public API.

### Keeping in sync with Clive

When the Clive algorithm changes, update the port and the hand-worked `expect` values first, then
the drawing and the README. Each visualization reflects Clive's current behaviour, not its history.

## Commands

```
npm ci            install the tracers library used by the checks and the site
npm run check     run every code.js in Node with its self-checks
npm run build     build the site into build/site (clones and builds the upstream frontend)
npm run serve     serve build/site at http://localhost:8080/
npm run api       regenerate only the algorithm JSON in build/site after editing algorithms/
```

## How the site is built

The upstream frontend needs a backend. The site is static instead:

- `visualizer/UPSTREAM` pins the frontend commit. `visualizer/patches/*.patch` make it read the
  algorithms as static JSON relative to the page, use hash routes (`#/<category>/<algorithm>`),
  offer JavaScript only, drop sign-in, gists, Facebook sharing and upstream analytics, and rename
  the home page. `visualizer/home.md` is the home page text.
- `scripts/build-api.mjs` turns `algorithms/` into `api/algorithms.json` and
  `api/algorithms/<category>/<algorithm>.json`. `visualizer/worker.js` runs the code with the tracers
  library served next to it.
- The frontend is Create React App 3: `node-sass` is replaced by Dart Sass and webpack runs with
  `--openssl-legacy-provider`, so it builds on current Node.

To upgrade the frontend: change `FRONTEND_SHA`, run `npm run build`, fix the patch where it no longer
applies (edit `build/upstream`, then `git -C build/upstream diff > visualizer/patches/0001-static-hosting.patch`).

## CI

`.github/workflows/pages.yml`: on every pull request and push to `main` it runs `npm run check` and
builds the site. Pushes to `main` deploy it to GitHub Pages.
