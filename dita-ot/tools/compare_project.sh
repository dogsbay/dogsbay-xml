#!/usr/bin/env bash
# Build one DITA map with html5 and dogsbay-site and compare them.
# Usage: compare_project.sh <map> <out-dir> [dita parameters...]
# Environment: DITA_OT, PYTHON (default python3),
#   DOGSBAY_SSG (optional: a dogsbay-ssg build.mjs to use instead of the plugin's bundled one)
set -uo pipefail
: "${DITA_OT:?set DITA_OT}"
map=$(realpath "$1"); out=$(realpath -m "$2"); shift 2
# The out-dir is deleted first: refuse one that holds the map's folder, the home folder or the root
case "$(dirname "$map")/" in
  "$out"/*) echo "refusing to delete $out: it contains $map"; exit 2 ;;
esac
if [ "$out" = "/" ] || [ "$out" = "$HOME" ]; then echo "refusing to delete $out"; exit 2; fi
PYTHON=${PYTHON:-python3}
TOOL=$(cd "$(dirname "$0")" && pwd)/compare_outputs.py
rm -rf "$out" && mkdir -p "$out"
ssg=()
if [ -n "${DOGSBAY_SSG:-}" ]; then ssg=(--dogsbay.ssg="$DOGSBAY_SSG"); fi
"$DITA_OT/bin/dita" -i "$map" -f html5 "$@" -o "$out/ref" > "$out/html5.log" 2>&1 || echo "html5 build failed (see html5.log)"
# One build now, not a publish plus a scaffold, a package install and an Astro build:
# the dogsbay-site transtype runs the bundled dogsbay-ssg builder itself.
"$DITA_OT/bin/dita" -i "$map" -f dogsbay-site ${ssg[@]+"${ssg[@]}"} "$@" -o "$out/pub" > "$out/publish.log" 2>&1 \
  || { echo "dogsbay-site build failed (see publish.log)"; exit 2; }
nav=()
if [ -f "$out/ref/index.html" ] && [ -f "$out/pub/site/_dogsbay/nav.js" ]; then
  nav=(--site-nav "$out/pub/site/_dogsbay/nav.js")
fi
"$PYTHON" "$TOOL" "$out/ref" "$out/pub/site" ${nav[@]+"${nav[@]}"} --json "$out/compare.json" | tee "$out/compare.txt"
