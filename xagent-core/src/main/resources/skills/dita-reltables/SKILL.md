---
name: dita-reltables
description: Relate DITA topics with a relationship table (or topic-local related-links) — propose and author governed related links from shared subjects, reference-safely. Use when asked to relate topics, build or audit a reltable, add related links, or connect concepts to their tasks and references.
---

# Relate topics with a relationship table

A `<reltable>` in a map defines relationships between topics (classically a
**concept ↔ task ↔ reference** matrix); DITA-OT turns each row into related-links at
output, so related links are governed in **one place** instead of scattered
`<related-links>` in every topic. Tools: `reltable_audit`, `edit_reltable`, grounded
by `keyword_audit`, `xpath_query`, `where_used`, `list_keys`.

## The hard part is deciding what relates — that's the judgment

The link graph (`where_used`/`check_links`) only knows links that *already* exist; it
can't tell you two topics *should* relate. That is the judgment this skill carries:

- **Relate by subject, not by accident.** Group topics that share a subject. Use
  `keyword_audit` (its co-occurrence output is a topic-pair relatedness signal) and
  shared `category`/`subjectref` as the clustering input — don't guess. Generating
  keywords first (`dita-keywords`) sharpens this signal.
- **Use the concept→task→reference idiom.** A concept relates to the tasks that use
  it and the references that document it; put each in its typed column. Topic type
  comes from the root element.
- **Don't duplicate.** Skip pairs already linked inline (`where_used`) or already
  covered by an existing reltable row (`reltable_audit`).
- **reltable vs related-links.** Use a **reltable** for map-wide relationships; use
  topic-local **`<related-links><link>`** only for genuine one-offs (author those
  with `edit`, no command needed).
- **Prefer keyrefs** for cell targets when the project uses keys (`list_keys`).

## Process

1. **Inventory** — `xpath_query "//concept|//task|//reference"` (or list project
   files) and collect title/shortdesc/`category` per topic.
2. **Cluster** — run `keyword_audit` for the co-occurrence pairs; group topics by
   shared keywords/category into relationship sets with concept/task/reference roles.
3. **De-dup** — `reltable_audit` (existing coverage + the links it already generates)
   and `where_used`; drop pairs already related.
4. **Propose to the user** — relatedness is an editorial call; confirm the rows
   before writing.
5. **Author** — `edit_reltable`: `create-table --columns concept,task,reference` (if
   none), then `add-row` and `add-target --row R --col C --href|--keyref …`. Preview
   with dry-run. (Or the editor's **Project ▸ Edit Relationship Tables…** grid.)
6. **Confirm** — `reltable_audit` shows the new related-links and flags any broken /
   map-targeting / type-mismatched cell; `validate` the map.

## Report

The rows added (with the subject that motivated each), the related-links now
generated, any pairs deliberately skipped (already linked), and audit findings.
Pairs with [[dita-keywords]] (the clustering signal) and [[dita-subject-schemes]]
(governed categories).
