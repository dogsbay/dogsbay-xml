---
name: dita-keywords
description: Generate subject keywords for DITA topics from their content and write them into the prolog, reusing the project's existing vocabulary and any governing subject scheme. Use when asked to add or generate keywords, tag topics for search/findability, or enrich metadata to signal related topics.
---

# Generate DITA keywords from content

Keywords live in a topic's `<prolog><metadata><keywords><keyword>` and drive search,
findability, and relatedness. Generating good ones is judgment — the tools handle the
writing: propose terms from the content, then write them with `metadata_set`
(`field=keyword`, `mode=append`, field-preserving). Don't hand-edit the prolog.

## The judgment (what makes this a skill, not a one-off)

- **Consistency over novelty.** First learn the project's *existing* keyword
  vocabulary (`xpath_query "//keyword"` across topics). Reuse the canonical term —
  one spelling, not synonyms ("installation", never also "setup"/"set up"). Adding a
  near-duplicate is worse than adding nothing.
- **Respect governance.** If a subject scheme governs keywords or `category`, prefer
  its controlled values (`list_subjects`); don't invent ungoverned tokens. See
  [[dita-subject-schemes]].
- **Grounded + right-sized.** Only terms the content actually supports — ~3–7
  meaningful subject terms per topic. Not a word cloud; not the obvious product name
  on every topic.
- **`<keyword>` vs `<indexterm>`.** `<keyword>` is for metadata/search/relatedness
  (this skill). `<indexterm>` is for the generated back-of-book index (different
  intent) — both live under `<keywords>`; don't conflate them.

## Process

1. **Survey the vocabulary** — `keyword_audit` reports the project's distinct
   keywords with frequencies, topics with none, and **near-duplicate spellings**
   (the drift to avoid); `list_subjects` adds any scheme-governed terms. This is your
   controlled set. (`xpath_query "//keyword"` is the lower-level alternative.)
2. **Read the topic(s)** — title, shortdesc, body — and pick 3–7 grounded subject
   terms, preferring the existing/governed vocabulary; only coin a new term when the
   subject genuinely isn't covered.
3. **Write** — `metadata_set field=keyword value=<term> mode=append` per term
   (append, so existing keywords are kept). Preview a project-wide pass with
   `dryRun` first. Topics only (`.dita`).
4. **Per-file vs batch** — polish one open topic, or sweep "topics with no keywords"
   across the project (mirror the metadata audit/normalize split).

## Report

Per topic: the keywords added, which reused the existing/governed vocabulary vs were
newly coined (and why), and any synonym/spelling drift you noticed in the existing
set. Keywords are a **relatedness signal** — topics sharing keywords are candidate
related links (feed the reltable / related-links workflow). For descriptions, pair
with [[dita-shortdesc]]; to align *existing* metadata values use
[[dita-metadata-normalize]]; for the prolog model and policy see [[dita-metadata]].
