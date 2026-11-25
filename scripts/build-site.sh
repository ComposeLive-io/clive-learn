#!/usr/bin/env bash
# Builds the static site into build/site:
#   the algorithm-visualizer frontend at the commit pinned in visualizer/UPSTREAM, patched for static
#   hosting, plus the algorithms as static JSON, the recorded Kotlin visualizations, the JS worker
#   and the tracers library.
# The result works from any path (GitHub Pages project site, localhost), since every URL is relative.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
build="$root/build"
upstream="$build/upstream"
site="$build/site"

# shellcheck source=/dev/null
source "$root/visualizer/UPSTREAM"

# 1. Upstream frontend at the pinned commit, patched.
if [[ ! -d "$upstream/.git" ]]; then
  git clone --quiet "$FRONTEND_REPO" "$upstream"
fi
git -C "$upstream" fetch --quiet origin "$FRONTEND_SHA" 2>/dev/null || true
git -C "$upstream" checkout --quiet --force "$FRONTEND_SHA"
git -C "$upstream" clean --quiet -fdx -e node_modules
for patch in "$root"/visualizer/patches/*.patch; do
  git -C "$upstream" apply "$patch"
done
cp "$root/visualizer/home.md" "$upstream/src/files/algorithm-visualizer/README.md"

# 2. Dependencies. node-sass has no binaries for current Node, so its install script is skipped and
#    Dart Sass, whose render() API is compatible, is put in its place.
marker="$upstream/node_modules/.clive-learn-installed"
if [[ ! -f "$marker" || "$(cat "$marker")" != "$FRONTEND_SHA $SASS_VERSION" ]]; then
  (cd "$upstream" && npm ci --ignore-scripts --no-audit --no-fund)
  tmp="$(mktemp -d)"
  (cd "$tmp" && npm pack --silent "sass@$SASS_VERSION" >/dev/null && tar xzf "sass-$SASS_VERSION.tgz")
  rm -rf "$upstream/node_modules/node-sass"
  mv "$tmp/package" "$upstream/node_modules/node-sass"
  rm -rf "$tmp"
  echo "$FRONTEND_SHA $SASS_VERSION" > "$marker"
fi

# 3. Build. Webpack 4 hashes with md4, which OpenSSL 3 only offers through the legacy provider.
#    CI=false keeps upstream's own lint warnings from failing the build.
(cd "$upstream" && \
  NODE_OPTIONS=--openssl-legacy-provider PUBLIC_URL=. CI=false GENERATE_SOURCEMAP=false \
  node node_modules/.bin/react-scripts build)

rm -rf "$site"
cp -R "$upstream/build" "$site"
# CRA 3 emits a service worker that is never registered; leave it out so nothing caches stale pages.
rm -f "$site/service-worker.js" "$site"/precache-manifest.*.js

# 4. Static API: algorithms, JS worker and tracers library.
node "$root/scripts/build-api.mjs" "$site"
mkdir -p "$site/api/tracers/js"
cp "$root/visualizer/worker.js" "$site/api/tracers/js/worker.js"
cp "$root/node_modules/algorithm-visualizer/dist/algorithm-visualizer.umd.js" "$site/api/tracers/js/"
touch "$site/.nojekyll"

echo "Site built in $site"
