// Runs one code.js like the in-browser worker does and prints how many commands it recorded.
// Usage: node scripts/run-algorithm.cjs <path to code.js>
process.env.ALGORITHM_VISUALIZER = '1'; // record commands instead of uploading them
const fs = require('node:fs');
const AlgorithmVisualizer = require('algorithm-visualizer');

const code = fs.readFileSync(process.argv[2], 'utf-8');
const fakeRequire = name => ({ 'algorithm-visualizer': AlgorithmVisualizer }[name]);
new Function('require', code)(fakeRequire);
console.log(AlgorithmVisualizer.Commander.commands.length);
