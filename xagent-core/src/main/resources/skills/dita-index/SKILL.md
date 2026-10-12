---
name: dita-index
description: Build and audit a DITA index (indexterm) — survey index coverage, fix dangling see/see-also redirects, and add index entries where topics have none. Use when asked to check or improve a project's index, find topics missing index terms, or repair cross-references that point nowhere.
---

# DITA index (indexterm)

Index entries (`<indexterm>`) let readers find content by term; nested indexterms make
sub-entries (`primary > secondary`), and `<index-see>`/`<index-see-also>` redirect from
one term to another. The tool: `index_audit` (inventory + coverage + dangling redirects).

## Process

1. **Survey** — `index_audit <root>` returns the entry inventory with topic
   frequencies, topics carrying **no** index terms (coverage gaps), and **dangling**
   `index-see`/`-also` redirects whose target is no real entry.
2. **Fix dangling redirects first** — a `<index-see>foo</index-see>` is a promise that
   "foo" is itself an index entry. If it isn't, either correct the target to a real
   entry or add the missing entry. A reader who follows a dangling see finds nothing.
3. **Close coverage gaps with judgment** — not every topic needs index terms, but
   reference and concept topics usually benefit. Add `<indexterm>` to the topic's
   prolog (`<metadata><keywords>`) or inline; use a `primary > secondary` nesting only
   when the secondary genuinely subdivides the primary.
4. **Keep terms consistent** — index the *same* concept under the *same* primary term
   across topics (the audit's frequency list surfaces near-duplicates to merge); a
   split term ("logging" vs "log files") fragments the index.

## Report

The coverage gaps and dangling redirects found, what you changed (entries added /
redirects fixed), and any term inconsistencies worth normalizing. Index terms are
metadata — pair with [[dita-metadata]] for the rest of the prolog, and
[[dita-keywords]] for the search vocabulary (a different signal from the index).
