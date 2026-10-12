---
name: dita-fix-validation
description: Validate DITA topics/maps against their DOCTYPEs and fix every validation error with minimal edits, re-validating until clean. Use when asked to fix DITA validation errors, make topics valid, or clean up a map before a build.
---

# Fix DITA Validation Errors

Validate the requested DITA files and repair them until they are clean.

## Inputs

If the user named specific files or a map, work on those. Given a map,
also process every topic it references (`xpath` on `@href` attributes).
If a catalog file exists (`catalog.xml`, `catalog-dita.xml`, or named by
the user), pass it to every validation call.

## Process

1. For each file, run `xml_validate` with `dtd: true` (and `catalog` when
   available). Collect the `file:line:col: severity: message` issues.
2. Fix errors one file at a time with `edit`, smallest change that
   satisfies the DTD:
   - Misplaced/unknown elements: prefer renaming to the closest valid
     DITA element over deleting content. Common cases: `<p>` inside
     `<ul>` needs `<li>`, steps belong in `<step><cmd>`, inline content
     directly in `<section>` needs a wrapper.
   - Missing required elements (e.g. `<title>`): add a sensible one
     derived from context, marked clearly if you had to guess.
   - Broken IDs/references: fix the reference if the target exists;
     otherwise report it rather than inventing targets.
3. Re-validate after each file. Repeat until every file passes or only
   unfixable issues remain.
4. Never change the meaning of the content. If a fix would require
   rewriting prose or you cannot determine intent, leave the error and
   list it in the report.

## Report

End with: files fixed (with the error count before/after), errors that
remain and why, and whether a `dita_build` is now worth running.
