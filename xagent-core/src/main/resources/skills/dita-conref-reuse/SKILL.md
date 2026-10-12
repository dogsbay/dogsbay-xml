---
name: dita-conref-reuse
description: Reuse content across DITA topics with conref — warehouse a repeated element so it lives in one place, or inline a conref back to literal content. Use when asked to deduplicate repeated notes/steps/warnings, make a block reusable, or remove a content reference. (For broken conref ids use dita-conref-audit; for value reuse use dita-keys.)
---

# Reuse content with conref

`conref` pulls an element (a note, step, warning, paragraph) from one topic into
another by reference, so shared content is authored once. Tools: `extract_conref`
(warehouse an element + conref it back), `inline_conref` (expand a conref to
literal). This is *content* reuse — for *value* reuse (product name, version) use
[[dita-keys]]; for *broken* conref element-ids use [[dita-conref-audit]].

## Warehousing — the judgment

- **Reuse only genuinely shared content** — a safety note, a common prerequisite, a
  boilerplate step. Don't conref content that merely looks similar but should evolve
  independently; over-reuse couples topics that shouldn't be coupled.
- **The warehouse is a real topic** (e.g. `shared/common-notes.dita`,
  `common-steps.dita`) whose only job is to hold reusable elements, each with a
  stable `@id`. Put extracted content there, not scattered.
- **The element needs an `@id`** to be extractable and referable. Give it a
  meaningful, stable id (`backup-warning`, `quiet-room-tip`) — renaming it later
  means [[dita-refactor]]'s `rename_element_id`.

## Process

1. **Identify** the element worth sharing (it has, or gets, an `@id`).
2. **Extract** — `extract_conref <topic> <element-id> --to <warehouse-topic>` moves
   the element into the warehouse and leaves a `conref` in its place. Dry-run first.
3. **Reuse** — other topics reference it via
   `conref="…/warehouse#topic-id/element-id"`; keep the conref'd element's required
   children present (e.g. an empty `<p/>` placeholder where the model demands one).
4. **Inline** when reuse is no longer wanted — `inline_conref` replaces conrefs to a
   target with the content itself (e.g. before deleting the warehouse entry, or when
   a topic must diverge).
5. Verify with `conref_audit` (no broken `#fragment` ids) — see [[dita-conref-audit]].

## Report

What was warehoused (element → warehouse id) or inlined, which topics now reference
it, and the conref-audit result.
