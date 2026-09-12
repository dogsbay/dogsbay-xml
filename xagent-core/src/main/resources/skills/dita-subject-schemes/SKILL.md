---
name: dita-subject-schemes
description: Author or repair a DITA subjectScheme that governs conditional-processing attribute values (@platform, @audience, @product, @props, @otherprops), then validate content against it. Use when introducing controlled values, governing conditional content, or when validate_conditions reports controlled-value violations.
---

# Govern DITA Conditional Values with a Subject Scheme

A subjectScheme defines the controlled values a profiling attribute may take.
Once it governs an attribute, any other value is drift — usually a typo like
`platform="macos"` — that silently breaks filtering: a DITAVAL including `mac`
does not match `macos`, so that content vanishes from the build with no error.

## Process

1. **Find the contract.** Survey the project's DITAVAL files (`grep`/`xpath` over
   `*.ditaval` for `<prop att val>`). Those att/val pairs are the team's intended
   vocabulary — derive the controlled set from them, NOT from the values found in
   content (which bake in the typos you're trying to catch).
2. **Author the scheme.** With `edit`, create `controlled-values.ditamap`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
   <subjectScheme>
     <subjectdef keys="os">
       <subjectdef keys="windows"/><subjectdef keys="mac"/><subjectdef keys="linux"/>
     </subjectdef>
     <enumerationdef>
       <attributedef name="platform"/>
       <subjectdef keyref="os"/>
     </enumerationdef>
   </subjectScheme>
   ```
   One `<enumerationdef>` per governed attribute; legal values are the bound
   subject's `keys` plus all descendant `keys` (subjects are hierarchical).
3. **Wire it in.** Add `<mapref href="controlled-values.ditamap"/>` to every
   deliverable's root map, so each publication's closure is governed.
4. **Validate.** Run `validate_conditions` (or `list_subjects` to show the
   vocabulary). It reports each content/DITAVAL value not in the scheme, with a
   near-miss suggestion (`macos` → `mac`).
5. **Fix drift.** Correct each violation — `rename_profile_value` changes a value
   project-wide (DITAVALs included). Re-validate until clean, then a `dita_build`
   confirms the previously-dropped content ships again.

## Notes

- A category subjectdef's own key (e.g. `os`) also counts as an allowed value — harmless.
- `@otherprops` group syntax `group(a b)` is not validated; don't rely on it.
- Schemes parse without a DTD catalog (external DTD loading is disabled).

## Report

End with: the governed attributes and their values, violations found and fixed
(file:line, old → new), and any value you left because intent was unclear.
