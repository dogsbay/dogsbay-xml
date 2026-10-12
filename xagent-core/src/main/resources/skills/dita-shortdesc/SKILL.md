---
name: dita-shortdesc
description: Write SEO-friendly DITA shortdescs and abstracts — a standalone one-sentence summary that doubles as the link preview and the HTML meta description. Use when asked to add or improve a topic's description/abstract, write shortdescs, fill missing ones, or make descriptions SEO-friendly.
---

# Write SEO-friendly shortdescs & abstracts

A topic's `<shortdesc>` is three things at once: the topic summary, the hover/link
preview wherever it's referenced, and the HTML meta description for search. So it's
worth writing well. Generating it is judgment; writing it is a plain topic edit —
insert/replace `<shortdesc>` **immediately after `<title>`** (after `<titlealts>` if
present, before `<prolog>`/body). For a richer lead use `<abstract>` (which can
*contain* the shortdesc).

## The judgment

- **shortdesc vs abstract.** Default to a single `<shortdesc>` sentence. Use
  `<abstract>` only when the topic needs a longer lead-in; keep a `<shortdesc>` inside
  it for the preview/meta use.
- **SEO discipline.** Lead with the subject/keyword; ≤ ~155 characters (meta-
  description length); active voice; front-load the value. **No filler** — never
  "This topic describes…", "This page is about…". It must read **standalone**, because
  it's the search and link snippet out of context.
- **Topic-type aware.** A **task** states the outcome ("Record a track and set levels
  to avoid clipping."); a **concept** defines what the thing is and why it matters; a
  **reference** says what it lists/specifies.
- **Grounded + consistent.** Summarize the actual content — don't promise what isn't
  there. Use the product **keyref** (`<keyword keyref="product-name"/>`), not a
  hardcoded name, and align terms with the topic's keywords (see [[dita-keywords]]).
- **Don't clobber.** Leave a good existing shortdesc alone; rewrite weak/placeholder
  ones; **fill** topics that have none.

## Process

1. Read the topic (title + body) and identify its single most important point and
   type.
2. Draft a standalone, front-loaded sentence under ~155 chars (or an `<abstract>`
   when warranted), using the product keyref and consistent terminology.
3. Place it right after the title: rewrite a weak/placeholder shortdesc in place, or
   insert one where missing. Validate the topic after.
4. **Per-file vs batch** — polish one open topic, or sweep "topics with no /
   placeholder shortdesc" across the project.

## Report

Per topic: the shortdesc written (and whether it replaced a weak one or filled a
missing one), with a note on length and that it reads standalone. Pairs with
[[dita-keywords]] as the **searchability pair** (snippet + facets); the description
text is also a soft relatedness signal for the related-links/reltable workflow.
