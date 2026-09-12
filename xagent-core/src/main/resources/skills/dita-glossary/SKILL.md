---
name: dita-glossary
description: Govern a DITA glossary (glossentry, abbreviated-form) — audit the term inventory, fix abbreviated-form/term references that don't resolve to a glossary entry, and find unused glossentries. Use when asked to check terminology consistency, repair undefined glossary references, or clean up an unused glossary.
---

# DITA glossary & terminology (glossentry / abbreviated-form)

A `<glossentry>` defines a term (its `<glossterm>` and surface forms); content links to
it by key — `<abbreviated-form keyref="…"/>` pulls the short form, `<term keyref="…">`
links the term. The tool: `glossary_audit` (inventory + undefined references + unused
entries).

## Key facts

- **Glossentries are keyed.** A `<keydef keys="waveform" href="glossary/g-waveform.dita"/>`
  binds the key `waveform` to the glossentry; `<abbreviated-form keyref="waveform"/>`
  then resolves through that key. So `glossary_audit` needs the **rootMap** to resolve
  references — without it you get the inventory only.
- **`abbreviated-form` MUST resolve to a glossentry** (it expands the term's short
  form); a `term` *may* keyref any key, so only a truly undefined key is wrong there.

## Process

1. **Survey** — `glossary_audit <root> --root-map <map>`: the glossentry inventory,
   **undefined** references (an `abbreviated-form`/`term` keyref that doesn't resolve to
   a glossary entry), and **unused** glossentries (defined but never referenced).
2. **Fix undefined references** — either the keyref is a typo (correct it to the real
   key), the glossentry is missing (create it), or the keydef is missing (add it so the
   key resolves). An undefined `abbreviated-form` renders nothing useful.
3. **Decide on unused entries** — an unused glossentry is dead weight *or* a term you
   meant to use; either reference it from the content that discusses the term, or
   remove it. Don't blindly delete — a glossary entry can be intentionally
   forward-looking.
4. **Consistency** — the same concept should have one glossentry, referenced
   everywhere, not be re-explained inline per topic.

## Report

The undefined references (and how each was fixed — typo / missing entry / missing
keydef), the unused entries and your decision, and any duplicate definitions. For the
keys that bind glossentries, see [[dita-keys]]; for content reuse generally,
[[dita-conref-reuse]].
