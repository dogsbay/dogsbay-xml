---
name: dita-metadata-normalize
description: Normalize prolog metadata (audience, product, platform, otherprops, critdates) consistently across all topics in a DITA map — align spelling/casing variants and fill gaps. Use when asked to align, clean up, or bulk-update topic metadata values across a project.
---

# Normalize DITA Topic Metadata

Make `<prolog>` metadata consistent across every topic in a map: one canonical
spelling per value, and required fields present. (To *govern* metadata with a
required-metadata policy + audit, use `dita-metadata`; for controlled-value
vocabularies, `dita-subject-schemes`.)

## Process

1. **Inventory.** Build a table of current values per topic. If the project has a
   required-metadata policy, `metadata_audit` already surfaces missing-required and
   out-of-vocabulary values; otherwise `xpath` the prolog
   (`//prolog//audience/@type`, `metadata/category`, `metadata/othermeta`,
   `critdates`) across the map's topics.
2. **Determine the canonical scheme.**
   - If the user specified target values, use those.
   - Else infer the dominant convention from the inventory and confirm the outliers
     are mistakes (e.g. `admin` vs `administrator` for the same audience).
3. **Apply with `metadata_set`** (field-preserving — it locates-or-creates the
   element in content-model order and leaves all other prolog content + formatting +
   CRLF intact; prefer it over raw `edit`):
   - **Fill gaps:** `metadata_set field=<f> value=<canonical> mode=fill` adds the
     field only where it's absent (across the whole scope in one call).
   - **Normalize an outlier spelling:** scope to the topics that carry the variant
     (a `glob:` or the specific files) and `metadata_set field=<f> value=<canonical>
     mode=set` to overwrite it. Preview any bulk change with `dryRun` first.
   - Never invent semantics — don't assign an audience/category a topic never
     implied; flag those for a human instead.
   - For an irregular shape `metadata_set` can't express, fall back to a script/`edit`.
4. **Validate** each touched topic (`metadata_audit` to re-check the policy, or
   `validate_document`).

## Report

Before/after table of metadata values per topic, files changed, and flagged topics
needing a human decision.
