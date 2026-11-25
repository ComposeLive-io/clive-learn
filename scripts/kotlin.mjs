// Records the Kotlin visualizations (algorithms/<Category>/<Algorithm>/code.kt) with Gradle. The
// player can't run Kotlin, so the site replays the commands each one recorded at build time.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const recordedDir = path.join(root, 'build', 'recorded');

/**
 * Compiles and runs every code.kt. Returns, per algorithm directory, either `{ commands }` (the
 * recorded JSON text) or `{ error }`. A visualization that throws is reported with its exception; one
 * that doesn't compile, with Gradle's output.
 */
export function recordKotlin(algorithms) {
  const results = new Map();
  if (!algorithms.length) return results;
  // Recordings left from an earlier run would hide a visualization that no longer compiles.
  fs.rmSync(recordedDir, { recursive: true, force: true });
  const gradlew = path.join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  const gradle = spawnSync(gradlew, ['--quiet', '--continue', 'record'], { cwd: root, encoding: 'utf-8' });
  const gradleOutput = `${gradle.stdout ?? ''}${gradle.stderr ?? ''}${gradle.error?.message ?? ''}`.trim();
  for (const { category, algorithm } of algorithms) {
    const output = path.join(recordedDir, category.name, `${algorithm.name}.json`);
    if (fs.existsSync(output)) {
      results.set(algorithm.dir, { commands: fs.readFileSync(output, 'utf-8') });
    } else if (fs.existsSync(`${output}.error`)) {
      results.set(algorithm.dir, { error: fs.readFileSync(`${output}.error`, 'utf-8').trim() });
    } else {
      results.set(algorithm.dir, { error: `not recorded; Gradle said:\n${gradleOutput}` });
    }
  }
  return results;
}

/** The algorithms of a `readHierarchy()` written in Kotlin. */
export function kotlinAlgorithms(categories) {
  return categories.flatMap(category => category.algorithms
    .filter(algorithm => algorithm.files.some(file => file.name === 'code.kt'))
    .map(algorithm => ({ category, algorithm })));
}
