// import visualization libraries {
const { Tracer, Array1DTracer, ChartTracer, LogTracer, Layout, VerticalLayout } = require('algorithm-visualizer');
// }

// The strategy, ported from Clive's live code
// How many items to compose before the host has reported anything.
const FALLBACK_ITEM_COUNT = 30;
// A window only shrinks once it wants to be smaller than this fraction of its current size.
const SHRINK_THRESHOLD = 0.75;

class ProportionalLoadWindow {
  constructor(viewportsAround = 0.5) {
    this.viewportsTotal = 1 + 2 * viewportsAround; // the viewport plus a margin on each side
    this.size = 0;      // remembered across reports
    this.window = null; // [first, last] in the layout's index space; null until the first report
  }

  // Called when the host reports what is on screen. Works the window out and stores it.
  visibleItemsChanged(firstVisible, lastVisible, itemCount) {
    // A range that reaches the last item is as short as the list, not as the screen.
    const mayShrink = lastVisible < itemCount - 1;
    this.window = this.windowFor(firstVisible, lastVisible, mayShrink);
  }

  windowFor(firstVisible, lastVisible, mayShrink) {
    const visibleCount = Math.max(1, lastVisible - firstVisible + 1);
    const size = this.sizeFor(visibleCount, mayShrink);
    // Everything beyond the viewport, split evenly around it; an odd item goes below.
    const padding = size - visibleCount;
    const first = Math.max(0, firstVisible - Math.floor(padding / 2));
    return [first, first + size - 1]; // may run past the end of the list
  }

  sizeFor(visibleCount, mayShrink) {
    // Never smaller than the viewport.
    this.wanted = Math.max(visibleCount, Math.round(visibleCount * this.viewportsTotal));
    if (this.wanted > this.size || (mayShrink && this.wanted < this.size * SHRINK_THRESHOLD)) {
      this.size = this.wanted;
    }
    return this.size;
  }

  // Called while composing: only reads. Clips the window to the items that exist.
  loadWindow(firstVisible, itemCount) {
    const window = this.window || [firstVisible, firstVisible + FALLBACK_ITEM_COUNT - 1];
    const last = Math.min(window[1], itemCount - 1);
    return window[0] > last ? null : [window[0], last];
  }
}

// The scenario: what the host reports, step by step
// expect: the window composed after the step, worked out by hand. The run fails if the port disagrees.
const steps = [
  { itemCount: 60, firstVisible: 0, expect: [0, 29],
    say: 'First frame. Nothing is laid out yet, so the host has nothing to report: compose 30 items from the first visible one.' },
  { itemCount: 60, report: [0, 9], expect: [0, 19],
    say: 'The host reports 0..9. Ten visible, half a viewport either side: a window of 20.' },
  { itemCount: 60, report: [3, 13], expect: [0, 21],
    say: 'Scrolling. An item is cut at the edge, so 11 are visible: the window grows to 22 at once.' },
  { itemCount: 60, report: [6, 15], expect: [0, 21],
    say: 'Back to 10 visible. 20 is not below 75% of 22, so the size holds.' },
  { itemCount: 60, report: [10, 20], expect: [5, 26],
    say: '11 visible again. Same size, so the window only slides.' },
  { itemCount: 60, report: [14, 23], expect: [8, 29],
    say: 'The visible count keeps alternating 10, 11, 10 ... The window size does not.' },
  { itemCount: 60, report: [18, 28], expect: [13, 34], say: 'Still sliding at size 22.' },
  { itemCount: 60, report: [22, 31], expect: [16, 37], say: 'Still sliding at size 22.' },
  { itemCount: 60, report: [24, 28], expect: [22, 31],
    say: 'The screen rotates: only 5 visible. 10 is below 75% of 22, so the window shrinks.' },
  { itemCount: 60, report: [24, 33], expect: [19, 38],
    say: 'Rotated back: 10 visible. Growing needs no evidence: 20 at once.' },
  { itemCount: 60, report: [50, 59], expect: [45, 59],
    say: 'A fling to the end. The window runs past the last item and is clipped to the list.' },
  { itemCount: 1, expect: null,
    say: 'The data reloads: the list is now one Loading item. The old window 45..64 holds none of it.' },
  { itemCount: 1, report: [0, 0], expect: [0, 0],
    say: 'The host reports 0..0. It reaches the last item, so it is no evidence of a smaller screen: the size stays 20.' },
  { itemCount: 60, expect: [0, 19],
    say: 'The items are back. The window still has size 20, so a screenful is composed at once, before the next report.' },
  { itemCount: 60, report: [0, 9], expect: [0, 19],
    say: 'The host reports 0..9. Same window: nothing to compose, nothing to throw away.' },
];

// define tracer variables {
const chart = new ChartTracer('Window size after each step');
const list = new Array1DTracer('Items: number = composed, · = placeholder, blue = on screen, red = composed this step');
const logger = new LogTracer('What happens');
Layout.setRoot(new VerticalLayout([chart, list, logger]));
chart.set(steps.map(() => 0));
// }

const strategy = new ProportionalLoadWindow(0.5);
const format = range => range ? `${range[0]}..${range[1]}` : 'nothing';
const contains = (range, i) => range !== null && range[0] <= i && i <= range[1];
let composed = null;
let firstVisible = 0;
let lastVisible = 0;
let previousCount = 0;

steps.forEach((step, n) => {
  // logger {
  logger.println(`Step ${n}: ${step.say}`);
  // }
  if (step.report) {
    [firstVisible, lastVisible] = step.report;
    const sizeBefore = strategy.size;
    strategy.visibleItemsChanged(firstVisible, lastVisible, step.itemCount);
    // logger {
    logger.println(`  report ${format(step.report)} of ${step.itemCount}: wants ${strategy.wanted}, size ${sizeBefore} → ${strategy.size}`);
    // }
  }
  const window = strategy.loadWindow(firstVisible, step.itemCount);

  // self-check {
  if (format(window) !== format(step.expect)) {
    throw new Error(`Step ${n}: expected window ${format(step.expect)}, got ${format(window)}`);
  }
  // }

  // Compare with what was composed before. Item keys are recycled, so an item leaving the window
  // hands its composition to one entering it: only the difference in size costs anything.
  let entered = 0;
  let left = 0;
  for (let i = 0; i < Math.max(step.itemCount, previousCount); i++) {
    if (contains(window, i) && !contains(composed, i)) entered++;
    if (contains(composed, i) && !contains(window, i)) left++;
  }

  // visualize {
  list.set(Array.from({ length: step.itemCount }, (_, i) => contains(window, i) ? i : '·'));
  for (let i = 0; i < step.itemCount; i++) if (contains(window, i) && !contains(composed, i)) list.patch(i);
  if (step.report) list.select(firstVisible, lastVisible);
  chart.patch(n, strategy.window ? strategy.size : FALLBACK_ITEM_COUNT);
  // }
  // logger {
  logger.println(`  window ${format(window)}: ${Math.min(entered, left)} moved, ${Math.max(0, entered - left)} composed from scratch, ${Math.max(0, left - entered)} thrown away`);
  // }
  Tracer.delay();
  // visualize {
  chart.depatch(n);
  // }
  composed = window;
  previousCount = step.itemCount;
});
