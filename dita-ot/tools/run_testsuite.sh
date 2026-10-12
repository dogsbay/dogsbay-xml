#!/usr/bin/env bash
# Publish DITA-OT's integration test cases with html5 and dogsbay-site, and compare each pair
# with compare_outputs.py.
#
# Usage: run_testsuite.sh [case ...]        (default: every case)
#
# Environment:
#   DITA_OT       DITA-OT installation with com.dogsbay.publish installed (required)
#   DITA_OT_SRC   DITA-OT source checkout; cases come from src/test/resources (required)
#   OUT           work folder (default: ./testsuite-out)
#   PYTHON        Python with beautifulsoup4 (default: python3)
#   DOGSBAY_SSG   a dogsbay-ssg build.mjs to use instead of the plugin's bundled one (optional)
#
# Each case gets OUT/cases/<name>/{ref,pub,compare.txt,compare.json} and one line in
# OUT/summary.txt. dogsbay-site builds the site itself, with no package install, so the
# cases no longer share an installed node_modules.
set -uo pipefail
: "${DITA_OT:?set DITA_OT}" "${DITA_OT_SRC:?set DITA_OT_SRC}"
OUT=${OUT:-$PWD/testsuite-out}
PYTHON=${PYTHON:-python3}
DITA=$DITA_OT/bin/dita
RES=$DITA_OT_SRC/src/test/resources
TOOL=$(cd "$(dirname "$0")" && pwd)/compare_outputs.py
ssg=()
if [ -n "${DOGSBAY_SSG:-}" ]; then ssg=(--dogsbay.ssg="$DOGSBAY_SSG"); fi

# name | input (relative to the case's src folder) | dita parameters, as the integration tests set them
CASES=(
  "html5|map.ditamap|--args.gen.task.lbl=YES"
  "html5_ditaval|all.ditamap|--validate=no --dita.input.valfile=$RES/html5_ditaval/src/all.ditaval"
  "html5_ditaval_groups|all.dita|--validate=no --dita.input.valfile=$RES/html5_ditaval_groups/src/all.ditaval"
  "html5_nav_toc_full|base.ditamap|"
  "html5_uplevels3_nav_toc_full|maps/above.ditamap|"
  "link_parentchild|03.ditamap|"
  "link_xref_extensiontest|test.ditamap|"
  "title_includes_markup|test.ditamap|"
  "image_extension_mixedcase|testpng.ditamap|"
  "cascade_processingrole|test.ditamap|"
  "bookmap1|bookmap(2)_testdata1.ditamap|"
  "bookmap3|bookmap(2)_testdata3.ditamap|"
  "bookmap5|bookmap(2)_testdata5.ditamap|"
  "bookmap7|bookmap(2)_testdata7.ditamap|"
  "image-scale|test.dita|"
  "lang|lang.ditamap|--validate=false"
  "subjectschema_case|simplemap.ditamap|--filter=$RES/subjectschema_case/src/filter.ditaval"
  "uplevels3|maps/above.ditamap|--generate.copy.outer=3 --outer.control=quiet"
  "uplevelslink|main/uplevel-in-topic.ditamap|"
  "chunk_uplevel|main/chunkup.ditamap|"
  "crawl_map|input.ditamap|"
  "crawl_topic|input.ditamap|"
  "coderef_source|mp.ditamap|"
  "link_foreignshortdesc|main.ditamap|"
  "locktitle|TestingLocktitle.ditamap|"
  "mappull-topicid|reftopicid.ditamap|"
  "reltableHeaders|reltableheader.ditamap|"
  "reltableTextlink|1132.ditamap|"
  "resource_map|map.ditamap|"
  "resource_topic|keys.ditamap|"
  "rng|root.ditamap|"
  "e2e|root.ditamap|"
)

mkdir -p "$OUT/cases"
wanted=" ${*:-} "
run=$OUT/summary.run.txt
: > "$run"
for entry in "${CASES[@]}"; do
  IFS='|' read -r name input params <<< "$entry"
  if [ -n "${*:-}" ] && [[ "$wanted" != *" $name "* ]]; then continue; fi
  W=$OUT/cases/$name
  rm -rf "$W" && mkdir -p "$W"
  src="$RES/$name/src/$input"
  echo "== $name"
  # shellcheck disable=SC2086
  "$DITA" -i "$src" -f html5 $params -o "$W/ref" > "$W/html5.log" 2>&1 || echo "$name: html5 build failed (see html5.log)" >> "$run"
  # shellcheck disable=SC2086
  if ! "$DITA" -i "$src" -f dogsbay-site ${ssg[@]+"${ssg[@]}"} $params -o "$W/pub" > "$W/publish.log" 2>&1; then
    echo "$name: PUBLISH FAILED (see publish.log)" >> "$run"
    continue
  fi
  # A build of a single topic has no DITA-OT table of contents to compare the navigation with
  nav=(); [ -f "$W/ref/index.html" ] && [ -f "$W/pub/site/_dogsbay/nav.js" ] \
    && nav=(--site-nav "$W/pub/site/_dogsbay/nav.js")
  "$PYTHON" "$TOOL" "$W/ref" "$W/pub/site" ${nav[@]+"${nav[@]}"} --json "$W/compare.json" > "$W/compare.txt" 2>&1
  echo "$name: $(tail -2 "$W/compare.txt" | head -1) $(tail -1 "$W/compare.txt")" >> "$run"
done
cat "$run" | tee -a "$OUT/summary.txt"
