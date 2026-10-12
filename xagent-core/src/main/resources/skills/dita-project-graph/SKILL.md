---
name: dita-project-graph
description: See how a whole DITA project connects in one call with project_graph (maps, topics, keys, DITAVALs and every reference between them, per deliverable), and turn it into a page. Use when asked how the project fits together, what a deliverable ships, what a change would affect, what content is dead, why a key resolves differently in one output, or for a relationship map or any other view of the project's structure.
---

# Read a DITA project as a graph

`project_graph` returns the project's **structure**, where every audit returns
**findings**. One call replaces `where_used` run file by file, and the data comes
from the editor's own link and key resolution, so never extract it yourself with
regex, XPath or a script. Tools: `project_graph`, `render_report`, and for
follow-up checks `project_health`.

## Calling it

- `project_graph root=<project>`: every deliverable in the project file, plus
  every other DITA file under the root (so orphans show).
- `map=<root map>` or `deliverable=<name>`: one output only. Use this on a large
  project: the result is smaller and easier to reason about.
- `checks=false`: structure only, skipping DTD validation, the element-id audit
  and the conref push audit. Faster when you only need the edges.

## What comes back

- `deliverables[]`: `name`, `map`, `ditavals`, and `ships`: the node ids that
  deliverable actually publishes. A cross-reference or a reltable cell does not
  make a topic ship; a topicref, a conref target, or a key target reached from them
  does.
- `nodes[]`: `id` (project-relative path, or `key:<name>`), `kind`
  (map, topic, key, ditaval), `type` (the root element, such as task or concept),
  `title`, `missing` (a referenced file that is not there), `ships` (deliverable
  names).
- `edges[]`: `from`, `to`, `kind`, `line`, and where relevant `via`, `fragment`,
  `broken`, `deliverable`.
  - `mapref`, `topicref` (TOC placement), `reltable` (a relationship table cell,
    not a TOC entry), `link` (xref and link), `conref` (`fragment` is the element
    id), `ditavalref`, `profile` (a deliverable's DITAVAL).
  - `keydef`: map → `key:<name>`. `keytarget`: `key:<name>` → the file it resolves
    to, with `via` = the map that made that binding. One key name can have several
    `keytarget` edges with different `via`; which one applies depends on the
    deliverable's key space.
  - `keyref`, `conkeyref`: file → `key:<name>`.
- `issues[]`: `severity`, `rule`, `file`, `line`, `message`. Rules:
  `broken-reference`, `undefined-key`, `key-resolves-inconsistently`,
  `shadowed-key`, `unused-key`, `orphan-topic`, and with checks `invalid-dtd`,
  `broken-element-id`, `conref-push`.

Granularity is the file: elements are not nodes (a conref's element id rides on
the edge), topicref nesting is not modelled, and DITAVAL conditions are not applied
to content.

## Recipes

- **What does this deliverable ship?** Read `deliverables[].ships`. Compare two
  deliverables by set difference, not by guessing from map names.
- **What would this rename or delete affect?** Collect edges whose `to` is the file
  (and `keytarget` edges into it, then the `keyref` edges into those keys) for the
  DITA picture, but take the refactor tool's dry run or `where_used` as the full list:
  images and other non-DITA references are not in the graph. See [[dita-refactor]].
- **Dead content.** Candidates are topics with no `ships`, no inbound edges of any
  kind (a `link` or `reltable` edge still makes a topic reachable) and not `missing`,
  plus `orphan-topic` and `unused-key` issues. Check each with `where_used` and
  confirm with the user before deleting anything.
- **A key that works in one output and not another.** `key-resolves-inconsistently`
  issues name the deliverables where it fails; the `keytarget` edges and their `via`
  show which maps bind it. Fix with [[dita-keys]] or [[dita-key-scopes]].
- **Related-links coverage.** `reltable` edges are the relationships reltables
  generate; see [[dita-reltables]].
- **Is it healthy?** The graph's issues are structural. For the done-gate, run
  `project_health`.

## Making a page for people

- **The standard relationship map:** `render_report source=project_graph
  args={...} output=docs/relationship-map.html`. One offline HTML file.
- **A different view** (a coverage matrix, a per-deliverable table, a diagram of
  one area): build the page from the `project_graph` JSON. You design the
  presentation; the tool supplies the facts. Prefer saving the design as a
  template in `.dogsbay/reports/<name>.html` with one empty
  `<script id="report-data" type="application/json"></script>` that the page reads
  with `JSON.parse`, then render it with `render_report source=project_graph
  template=.dogsbay/reports/<name>.html`. The page can then be regenerated when the
  project changes instead of going stale.
- Keep pages self-contained: inline CSS and JS, no CDN or network, so they open from
  disk.

## Report

What you asked the graph (scope and filters), the answer with counts and the
specific files, keys or edges behind it, and any issues that bear on the question.
