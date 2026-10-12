---
name: dita-deliverables
description: Work with DITA-OT deliverables — the project's defined outputs (root map + DITAVAL filter + transtype + params). Use when asked to validate or build specific outputs, add/change a deliverable, understand a project's publication set, or deep-validate beyond static checks.
---

# DITA deliverables and publishing

A **deliverable** is one publishable output, declared in a DITA-OT **project file**
(`project.{json,xml,yaml}` — the standard name; never rename it): a root map, an
optional DITAVAL filter, a transtype, an output dir, and publication params. Tools:
`get_project`, `validate_deliverables`, `validate_deep`, `build_deliverables`.

## Key facts

- **The project file is the source of truth**, and DITA teams own it — edit it
  field-preservingly (don't reformat or invent a new format). One project usually
  ships several deliverables (e.g. full guide; a beginner guide filtered for mac and
  for windows; a PDF).
- **DITAVAL = conditional filtering.** A deliverable's `.ditaval` decides which
  `@platform`/`@audience`/`@props`-tagged content is kept. A "mac" build and a
  "windows" build are the *same* topics, filtered differently — validate and build
  each, don't assume one covers the others. (Govern the allowed condition values with
  [[dita-subject-schemes]].)
- **Publication params have three forms** — `value` (literal, passed as-is), `href`
  (resolved to a URI relative to the project file), `path` (resolved to an absolute
  filesystem path, e.g. `args.cssroot`). A relative path put in `value` won't
  resolve — choose `href`/`path` for resource params.

## Process

1. **Understand the set** — `get_project` lists the deliverables (root map, ditaval,
   transtype, params, source file). To see what each one actually publishes, and how
   two outputs differ, read `deliverables[].ships` from `project_graph`; it also
   reports keys that resolve in some deliverables and not others
   ([[dita-project-graph]]).
2. **Static validate per output** — `validate_deliverables` validates each
   deliverable's publication set (unfiltered: DITAVAL is not applied) and reports
   each invalid file once with the deliverables it breaks; report which *outputs* an
   error affects.
3. **Deep validate when needed** — `validate_deep` runs DITA-OT preprocessing to
   catch what static checks can't: broken keyref/conref **after** key resolution and
   filtering, map/topic resolution. Requires DITA-OT. Do this before declaring a
   deliverable publish-ready.
4. **Build** — `build_deliverables` produces the outputs (applying each ditaval +
   params). Build one, or all.

## Report

The deliverables and which outputs are affected by each finding, the deep-validation
result, and what was built. To restructure the maps a deliverable points at, use
[[dita-map-editing]].
