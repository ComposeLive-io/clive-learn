// import visualization libraries {
const { Tracer, Array2DTracer, LogTracer, Layout, VerticalLayout } = require('algorithm-visualizer');
// }

// Splitting one window into a range per interval, ported from Clive's live code {
// intervals: the size of each item()/items() call, in order.
// window: [first, last] in the layout's own index space, the one the host reports in.
// Returns one [first, last] per interval in that interval's own indexes, or null if it has none.
function spanning(window, intervals) {
  const ranges = [];
  let intervalStart = 0;
  for (const size of intervals) {
    const first = Math.max(window[0], intervalStart);
    const last = Math.min(window[1], intervalStart + size - 1);
    ranges.push(size === 0 || first > last ? null : [first - intervalStart, last - intervalStart]);
    intervalStart += size;
  }
  return ranges;
}
// }

// The layout {
// LazyColumn {
//   item { Header() }
//   items(20) { Product(it) }
//   item { Loading() }
//   items(searchResults.size) { Result(it) }   // no results yet
//   items(15) { Recommendation(it) }
// }
const intervals = [
  { name: 'item { Header }', size: 1 },
  { name: 'items(20) Product', size: 20 },
  { name: 'item { Loading }', size: 1 },
  { name: 'items(0) Result', size: 0 },
  { name: 'items(15) Recommendation', size: 15 },
];
const itemCount = intervals.reduce((sum, interval) => sum + interval.size, 0);

// Windows the strategy might ask for as the user scrolls, and what each interval should get.
const windows = [
  { window: [0, 9], expect: [[0, 0], [0, 8], null, null, null] },
  { window: [12, 31], expect: [null, [11, 19], [0, 0], null, [0, 9]] },
  { window: [22, 41], expect: [null, null, null, null, [0, 14]] },
];
// }

// define tracer variables {
const grid = new Array2DTracer('Row 0: layout index. Other rows: each interval\'s own index. Blue = window, red = composed');
const logger = new LogTracer('What each interval composes');
Layout.setRoot(new VerticalLayout([grid, logger]));
const rows = [Array.from({ length: itemCount }, (_, i) => i)];
let start = 0;
for (const interval of intervals) {
  rows.push(Array.from({ length: itemCount }, (_, i) => (i >= start && i < start + interval.size ? i - start : '')));
  start += interval.size;
}
grid.set(rows);
logger.println(`${intervals.length} intervals, ${itemCount} items: ${intervals.map(i => `${i.name} (${i.size})`).join(', ')}`);
Tracer.delay();
// }

const format = range => (range ? `${range[0]}..${range[1]}` : 'nothing');
const sizes = intervals.map(interval => interval.size);

for (const { window, expect } of windows) {
  const ranges = spanning(window, sizes);

  // self-check {
  if (ranges.map(format).join() !== expect.map(format).join()) {
    throw new Error(`Window ${format(window)}: expected ${expect.map(format).join(', ')}, got ${ranges.map(format).join(', ')}`);
  }
  // }

  // visualize {
  grid.selectRow(0, window[0], Math.min(window[1], itemCount - 1));
  logger.println(`Window ${format(window)}${window[1] >= itemCount ? ` runs past the last item (${itemCount - 1})` : ''}:`);
  Tracer.delay();
  // }
  let intervalStart = 0;
  ranges.forEach((range, i) => {
    // visualize {
    if (range) {
      for (let j = range[0]; j <= range[1]; j++) grid.patch(i + 1, intervalStart + j);
    }
    logger.println(`  ${intervals[i].name}: ${format(range)}`);
    Tracer.delay();
    // }
    intervalStart += sizes[i];
  });
  // visualize {
  intervalStart = 0;
  ranges.forEach((range, i) => {
    if (range) {
      for (let j = range[0]; j <= range[1]; j++) grid.depatch(i + 1, intervalStart + j);
    }
    intervalStart += sizes[i];
  });
  grid.deselectRow(0, window[0], Math.min(window[1], itemCount - 1));
  // }
}
