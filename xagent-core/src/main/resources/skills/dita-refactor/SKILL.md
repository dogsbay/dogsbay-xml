---
name: dita-refactor
description: Reference-safe refactoring across a DITA project — rename/move a file, redirect references, rename an element id, safely delete a file, or split a topic, rewriting every href/keyref/conref so nothing dangles. Use when asked to rename or move topics, consolidate or redirect links, rename an id, delete a topic, or break up an overgrown topic.
---

# Refactor DITA reference-safely

Renames and deletes in DITA are dangerous because references (`href`, `conref`,
`keyref`, `#fragment`) point across files. These tools rewrite every reference in one
pass: `where_used`, `rename_file`, `retarget`, `rename_element_id`, `delete_file`,
`split_topic`.

## The discipline: look before you leap

1. **Always survey the blast radius first.** `where_used <file>` (or the op's
   `--dry-run`) lists every inbound reference an edit will touch. Never refactor
   blind — review the list, then apply.
2. Apply, then **verify** with `check_links` (and `conref_audit` for element-ids) so
   nothing dangles.

## Picking the right tool

- **`rename_file`** — move/rename a topic *and* rewrite all references to it. The
  default for "rename this topic".
- **`retarget`** — redirect every reference from file A to file B **without** moving
  A (consolidate duplicates onto one canonical topic, or point links at a
  replacement). A stays put; references move.
- **`rename_element_id`** — rename an element's `@id` and every `#fragment`
  (conref/conkeyref/xref) that points at it. Use when an id is wrong or unclear.
- **`delete_file`** — safe delete: it first reports inbound references (so you see
  what would orphan), and can strip the dead map references. **Dry-run first**; if a
  topic is still referenced, fix or retarget those refs before deleting.
- **`split_topic`** — break an overgrown topic into standalone topics (one per
  section) and wire them into the map. Reference-safe; pair with [[dita-map-editing]]
  to place the new topicrefs.

## Process

1. `where_used` / `--dry-run` → read the affected references.
2. Choose the tool above; run with `--dry-run`, eyeball the plan.
3. Apply.
4. `check_links` + `conref_audit` to confirm clean.

## Report

What was renamed/retargeted/deleted/split, the count of references rewritten, and the
post-change link check. For map-structure moves (reordering topicrefs) use
[[dita-map-editing]]; for value reuse use [[dita-keys]].
