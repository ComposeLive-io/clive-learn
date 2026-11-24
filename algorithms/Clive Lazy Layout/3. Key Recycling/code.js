// import visualization libraries {
const { Tracer, Array1DTracer, LogTracer, Layout, VerticalLayout } = require('algorithm-visualizer');
// }

// The key recycler, ported from Clive's live code
// Each composed item is wrapped in key(k) { ... }. Compose identifies the item's composition by k,
// so handing k to another index moves the composition there instead of creating a new one.
class KeyRecycler {
  constructor() {
    this.counter = 0;
    this.currentKeys = new Map(); // index -> key
    this.pool = [];               // keys of items that left the window
  }

  // Before composing a window: every item outside it gives its key back.
  loadRangeUpdated(first, last) {
    for (const [index, key] of this.currentKeys) {
      if (index < first || index > last) {
        this.pool.push(key);
        this.currentKeys.delete(index);
      }
    }
  }

  // While composing the window, for each index in order.
  keyFor(index) {
    if (this.currentKeys.has(index)) return this.currentKeys.get(index); // stays put
    const key = this.pool.length > 0 ? this.pool.shift() : this.counter++; // recycled, else new
    this.currentKeys.set(index, key);
    return key;
  }
}

// The scenario
const itemCount = 16;
// expect: [moved, composed from scratch, disposed] for each window, worked out by hand.
const windows = [
  { window: [0, 7], expect: [0, 8, 0], say: 'First window: eight new keys, eight compositions.' },
  { window: [1, 8], expect: [1, 0, 0], say: 'Slide by one: item 0 leaves, item 8 enters with its key. One move.' },
  { window: [4, 11], expect: [3, 0, 0], say: 'Slide by three: three keys move from the top to the bottom.' },
  { window: [4, 13], expect: [0, 2, 0], say: 'The window grows: the pool is empty, so two new keys and two new compositions.' },
  { window: [6, 11], expect: [0, 0, 4], say: 'The window shrinks: four keys go to the pool and their compositions are disposed.' },
  { window: [7, 12], expect: [0, 1, 1], say: 'Slide by one: item 12 takes a pooled key, but that key\'s composition was already disposed, so it is composed again.' },
];

// define tracer variables {
const list = new Array1DTracer('Key of each item (· = not composed). Blue = window, red = key changed');
const pool = new Array1DTracer('Recycle pool');
const logger = new LogTracer('What Compose does');
Layout.setRoot(new VerticalLayout([list, pool, logger]));
list.set(Array.from({ length: itemCount }, () => '·'));
pool.set([]);
Tracer.delay();
// }

const recycler = new KeyRecycler();
let composed = new Map(); // key -> index, from the previous pass

for (const { window, expect, say } of windows) {
  // logger {
  logger.println(`Window ${window[0]}..${window[1]}: ${say}`);
  // }
  recycler.loadRangeUpdated(window[0], window[1]);
  const next = new Map();
  for (let index = window[0]; index <= window[1]; index++) next.set(recycler.keyFor(index), index);

  // Compose compares the keys emitted now with the ones emitted last time.
  let moved = 0;
  let fresh = 0;
  for (const [key, index] of next) {
    if (!composed.has(key)) fresh++;
    else if (composed.get(key) !== index) moved++;
  }
  const disposed = [...composed.keys()].filter(key => !next.has(key)).length;

  // self-check {
  if ([moved, fresh, disposed].join() !== expect.join()) {
    throw new Error(`Window ${window[0]}..${window[1]}: expected moved/fresh/disposed ${expect.join('/')}, got ${moved}/${fresh}/${disposed}`);
  }
  // }

  // visualize {
  const keys = Array.from({ length: itemCount }, (_, i) => (recycler.currentKeys.has(i) ? recycler.currentKeys.get(i) : '·'));
  const before = Array.from({ length: itemCount }, (_, i) => [...composed].find(([, index]) => index === i));
  list.set(keys);
  list.select(window[0], window[1]);
  for (let i = window[0]; i <= window[1]; i++) {
    if (!before[i] || before[i][0] !== keys[i]) list.patch(i);
  }
  pool.set([...recycler.pool]);
  logger.println(`  ${moved} moved, ${fresh} composed from scratch, ${disposed} disposed; pool [${recycler.pool.join(', ')}]`);
  Tracer.delay();
  // }
  composed = next;
}
