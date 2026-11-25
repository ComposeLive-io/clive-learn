// Runs every algorithms/<Category>/<Algorithm>/ visualization and fails if one throws (including its
// own self-checks) or records no commands. A code.js runs the way the player runs it, in a fresh Node
// process each; a code.kt is compiled and run on the JVM by Gradle.
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readHierarchy } from './build-api.mjs';
import { kotlinAlgorithms, recordKotlin } from './kotlin.mjs';

const runner = path.join(path.dirname(fileURLToPath(import.meta.url)), 'run-algorithm.cjs');

// The tracers library stops at 1,000,000 commands; a visualization this long is unwatchable anyway.
const MAX_COMMANDS = 20000;

const categories = readHierarchy();
const recorded = recordKotlin(kotlinAlgorithms(categories));

let failed = 0;
for (const category of categories) {
  for (const algorithm of category.algorithms) {
    const name = `${category.name} / ${algorithm.name}`;
    const codes = algorithm.files.filter(file => file.name === 'code.js' || file.name === 'code.kt');
    if (codes.length !== 1) {
      console.error(`FAIL ${name}: needs one code.js or code.kt, has ${codes.map(file => file.name).join(', ') || 'none'}`);
      failed++;
      continue;
    }
    if (!algorithm.files.some(file => file.name === 'README.md')) {
      console.error(`FAIL ${name}: no README.md`);
      failed++;
      continue;
    }
    try {
      let commands;
      if (codes[0].name === 'code.kt') {
        const result = recorded.get(algorithm.dir);
        if (result.error) throw new Error(result.error);
        commands = JSON.parse(result.commands).length;
      } else {
        const output = execFileSync(process.execPath, [runner, path.join(algorithm.dir, 'code.js')], {
          encoding: 'utf-8',
          stdio: ['ignore', 'pipe', 'pipe'],
        });
        commands = Number(output.trim().split('\n').pop());
      }
      if (!(commands > 0)) throw new Error('recorded no commands');
      if (commands > MAX_COMMANDS) throw new Error(`recorded ${commands} commands, more than ${MAX_COMMANDS}`);
      console.log(`ok   ${name}: ${commands} commands (${codes[0].name})`);
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
