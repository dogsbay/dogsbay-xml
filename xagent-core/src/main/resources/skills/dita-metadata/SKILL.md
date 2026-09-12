---
name: dita-metadata
description: Author, govern, and audit DITA prolog/topicmeta metadata (author, audience, category, keywords, prodinfo, critdates, othermeta) — add required fields, check topics against a required-metadata policy, and bulk-fill what's missing. Use when asked to add/author metadata, enforce a metadata policy, audit metadata, or make a project's metadata complete/consistent.
---

# Author and Govern DITA Metadata

DITA metadata lives in a topic's `<prolog>` (and a map's `<topicmeta>`): author,
audience, category, keywords, prodinfo, critdates, othermeta, data. This skill
covers **authoring** it correctly and **governing** it with a required-metadata
policy. (For aligning inconsistent *values* across a corpus, use
`dita-metadata-normalize`; for controlled values, `dita-subject-schemes`.)

## Authoring a field

Add metadata field-preservingly with **`metadata_set`** — it locates-or-creates the
`<prolog>`/wrapper and the element, in DITA content-model order, leaving everything
else (and CRLF line endings) untouched:

- modes: `fill` (only if absent — the safe default), `set` (overwrite), `append`
  (add to a list like keyword), `remove`.
- `field` is the element name (`audience`, `category`, `keyword`, `created`,
  `author`, …); `othermeta`/`data` take `name=value`.
- always preview a bulk change with `dryRun` first.

For one open topic, the editor's document tools / Metadata panel also work; reserve
raw `edit` for irregular cases `metadata_set` can't express.

## Governing with a policy

A **required-metadata policy** (in the project's `.dogsbay/config.xml`, or an
explicit policy file) declares what topics must carry — e.g. "every task has a
`<created>` date", "audience recommended everywhere", "reference topics must not
have an `<author>`", with optional allowed-values or a pattern.

## Process

1. **Audit.** Run **`metadata_audit`** (whole project, or `map:`/`glob:` scope). It
   reports per file: missing-required (error), missing-recommended (warning),
   forbidden present, and out-of-vocabulary values. No policy ⇒ nothing flagged.
2. **Fix the required gaps.** Use `metadata_set` to backfill — e.g.
   `metadata_set field=created value=2025-06-01 mode=fill` stamps a created date on
   every topic that lacks one (preview with `dryRun` first). Recommended-only
   warnings are optional.
3. **Don't invent semantics.** Backfill structural/known values (dates, a supplied
   audience); never guess an audience or category a topic never implied — flag those
   for a human.
4. **Re-audit** until the required findings are clean. `project_health` includes the
   metadata leg, so "done" means required metadata is present.
5. **Export for CI (optional).** `metadata_export_schematron` compiles the policy to
   ISO Schematron to run the same checks in DITA-OT / oXygen / a build pipeline.

## Report

End with: the policy in force, the audit findings before/after (errors vs
warnings), files changed and what was filled, and any topic flagged for a human
decision (a semantic value that couldn't be safely inferred).
