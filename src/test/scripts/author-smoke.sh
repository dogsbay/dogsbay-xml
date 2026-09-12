#!/usr/bin/env bash
# Agent-driven end-to-end smoke test of the WYSIWYG Author view.
#
# Channel A of the author-view test strategy: drive the running editor
# through its own command engine (CLI -> HTTP -> EditorExecutor) and assert
# on structured output. Runnable by hand, in CI (under xvfb-run with the
# editor started first), or by an AI agent.
#
# Usage:  src/test/scripts/author-smoke.sh
# Expects: the editor running (./gradlew run), bin/dogsbay-xml on PATH-relative,
#          run from the repository root.
set -euo pipefail

DOGSBAY=bin/dogsbay-xml
FIXTURE=src/test/resources/author/corpus/topics/trimming-audio.dita
WORK=$(mktemp /tmp/author-smoke-XXXX.dita)
trap 'rm -f "$WORK"' EXIT

fail() { echo "FAIL: $1" >&2; exit 1; }

$DOGSBAY status >/dev/null 2>&1 || fail "editor is not running"

cp "$FIXTURE" "$WORK"
$DOGSBAY open "$WORK" >/dev/null || fail "open"
$DOGSBAY author switch "$WORK" | grep -q "Author view active" || fail "switch to author view"

# --- outline reflects the document ---------------------------------------
OUTLINE=$($DOGSBAY author outline "$WORK")
echo "$OUTLINE" | grep -q '"type" : "task"' || fail "outline shows task root"
echo "$OUTLINE" | grep -q 'Trimming Audio'   || fail "outline shows title text"

STEPS_ID=$(echo "$OUTLINE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
def find(n,t):
    if n['type']==t: return n['id']
    for c in n.get('children') or []:
        r=find(c,t)
        if r: return r
print(find(d,'steps') or '')")
[ -n "$STEPS_ID" ] || fail "steps block found in outline"

# --- structural insert + text edit ----------------------------------------
CMD_ID=$($DOGSBAY author insert step --parent "$STEPS_ID" --file "$WORK") || fail "insert step"
[ -n "$CMD_ID" ] || fail "insert returned the new cmd block id"
$DOGSBAY author set-text "$CMD_ID" "Smoke-test command text." --file "$WORK" >/dev/null \
    || fail "set-text"

# --- validation is quiet on the edited document ----------------------------
$DOGSBAY author issues "$WORK" | python3 -c "
import json,sys
issues=json.load(sys.stdin)
errors=[i for i in issues if i['severity']=='ERROR']
assert not errors, f'unexpected errors: {errors}'" || fail "no validation errors"

# --- save and verify on disk ----------------------------------------------
$DOGSBAY save "$WORK" >/dev/null || fail "save"
grep -q "Smoke-test command text." "$WORK"  || fail "edit reached the file"
grep -q 'conref="../shared/common-steps'/ "$WORK" 2>/dev/null \
    || grep -q 'conref="../shared/common-steps' "$WORK" || fail "conref preserved"
grep -q '<x/>\|<x></x>' "$WORK"             || fail "unknown element preserved"
grep -q '<!DOCTYPE task' "$WORK"            || fail "DOCTYPE preserved"
head -5 "$WORK" | grep -q '<?xml'           || fail "XML declaration preserved"
grep -q '^  <title>' "$WORK"                || fail "output is indented"

$DOGSBAY close "$WORK" >/dev/null || true
echo "OK: author view smoke test passed"
