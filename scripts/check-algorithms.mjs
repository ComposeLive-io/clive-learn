// Runs every algorithms/<Category>/<Algorithm>/code.js the way the player does, in a fresh Node
// process each, and fails if one throws (including its own self-checks) or records no commands.
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readHierarchy } from './build-api.mjs';

const runner = path.join(path.dirname(fileURLToPath(import.meta.url)), 'run-algorithm.cjs');

// The tracers library stops at 1,000,000 commands; a visualization this long is unwatchable anyway.
const MAX_COMMANDS = 20000;

let failed = 0;
for (const category of readHierarchy()) {
  for (const algorithm of category.algorithms) {
    const name = `${category.name} / ${algorithm.name}`;
    if (!algorithm.files.some(file => file.name === 'code.js')) {
      console.error(`FAIL ${name}: no code.js`);
      failed++;
      continue;
    }
    if (!algorithm.files.some(file => file.name === 'README.md')) {
      console.error(`FAIL ${name}: no README.md`);
      failed++;
      continue;
    }
    try {
      const output = execFileSync(process.execPath, [runner, path.join(algorithm.dir, 'code.js')], {
        encoding: 'utf-8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      const commands = Number(output.trim().split('\n').pop());
      if (!(commands > 0)) throw new Error('recorded no commands');
      if (commands > MAX_COMMANDS) throw new Error(`recorded ${commands} commands, more than ${MAX_COMMANDS}`);
      console.log(`ok   ${name}: ${commands} commands`);
    } catch (error) {
      console.error(`FAIL ${name}: ${(error.stderr || error.message).trim()}`);
      failed++;
    }
  }
}
if (failed) {
  console.error(`${failed} visualization(s) failed`);
  process.exit(1);
}
