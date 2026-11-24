// Writes the algorithms in algorithms/ as the static JSON the patched frontend reads:
//   <site>/api/algorithms.json                    { categories: [{ key, name, algorithms: [{ key, name }] }] }
//   <site>/api/algorithms/<category>/<algo>.json  { algorithm: { categoryKey, categoryName, algorithmKey,
//                                                                algorithmName, files, description } }
// Keys and descriptions are made the way the upstream algorithm-visualizer server makes them.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const algorithmsDir = path.join(root, 'algorithms');

export function createKey(name) {
  return name.toLowerCase().trim().replace(/[^\w \-]/g, '').replace(/ /g, '-');
}

const listFiles = dir => fs.readdirSync(dir).filter(name => !name.startsWith('.')).sort();
const listDirectories = dir => listFiles(dir).filter(name => fs.statSync(path.join(dir, name)).isDirectory());

/** The first paragraph after the README title, as plain text. */
export function getDescription(files) {
  const readme = files.find(file => file.name === 'README.md');
  if (!readme) return '';
  const lines = readme.content.split('\n');
  lines.shift();
  while (lines.length && !lines[0].trim()) lines.shift();
  const paragraph = [];
  while (lines.length && lines[0].trim()) paragraph.push(lines.shift());
  return paragraph.join(' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[`*_]/g, '')
    .trim();
}

export function readHierarchy() {
  return listDirectories(algorithmsDir).map(categoryName => {
    const categoryDir = path.join(algorithmsDir, categoryName);
    return {
      key: createKey(categoryName),
      name: categoryName,
      algorithms: listDirectories(categoryDir).map(algorithmName => {
        const algorithmDir = path.join(categoryDir, algorithmName);
        const files = listFiles(algorithmDir).map(name => ({
          name,
          content: fs.readFileSync(path.join(algorithmDir, name), 'utf-8'),
          contributors: [],
        }));
        return { key: createKey(algorithmName), name: algorithmName, dir: algorithmDir, files };
      }),
    };
  });
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const site = process.argv[2];
  if (!site) throw new Error('Usage: build-api.mjs <site dir>');
  const api = path.join(site, 'api');
  const categories = readHierarchy();

  writeJson(path.join(api, 'algorithms.json'), {
    categories: categories.map(({ key, name, algorithms }) => ({
      key, name, algorithms: algorithms.map(({ key, name }) => ({ key, name })),
    })),
  });
  for (const category of categories) {
    for (const algorithm of category.algorithms) {
      writeJson(path.join(api, 'algorithms', category.key, `${algorithm.key}.json`), {
        algorithm: {
          categoryKey: category.key,
          categoryName: category.name,
          algorithmKey: algorithm.key,
          algorithmName: algorithm.name,
          files: algorithm.files,
          description: getDescription(algorithm.files),
        },
      });
      console.log(`api: ${category.key}/${algorithm.key}`);
    }
  }
}
