---
name: dita-branch-filtering
description: Produce several filtered variants from one DITA map using DITA 1.3 branch filtering (<ditavalref>), instead of maintaining near-duplicate per-variant deliverables. Use when asked how many variants a map produces, to turn separate per-platform/per-audience deliverables into one branched map, or to validate/build the variants and see which one a problem belongs to.
---

# DITA 1.3 branch filtering (`<ditavalref>`)

A `<ditavalref href="x.ditaval"/>` placed inside a `topicref`/`topicgroup` tells
DITA-OT to **duplicate that subtree and filter each copy** with a different DITAVAL —
so one map yields several variants. Sibling `<ditavalref>`s under one parent fan out
to several branches. A branch is conceptually **a deliverable inlined into the map**.
Tools: `list_branches`, `validate_deep`, `build_deliverables` (and [[dita-deliverables]]).

## When to reach for it

When you'd otherwise maintain **several near-identical deliverables** that differ only
by a DITAVAL (e.g. a `beginner-mac` and a `beginner-windows` output of the same map).
Branch filtering expresses that split *inside one map* — one source, N filtered
variants — so you maintain the structure once. Govern the condition values the
branches filter on with [[dita-subject-schemes]].

## Authoring a branch

`<ditavalref>` carries an optional `<ditavalmeta>` that disambiguates the generated
copies:

```xml
<topicref href="install.dita">
  <ditavalref href="windows.ditaval">
    <ditavalmeta><dvrResourcePrefix>win-</dvrResourcePrefix></ditavalmeta>
  </ditavalref>
  <ditavalref href="macos.ditaval">
    <ditavalmeta><dvrResourcePrefix>mac-</dvrResourcePrefix></ditavalmeta>
  </ditavalref>
</topicref>
```

- `<dvrResourcePrefix>` / `<dvrResourceSuffix>` rename the generated output resources
  (and are how diagnostics get attributed back to a branch — set them).
- `<dvrKeyscopePrefix>` / `<dvrKeyscopeSuffix>` rename the keys each branch defines
  (auto-generated key scopes — see [[dita-key-scopes]]).

Adding the `<ditavalref>` element itself is a map edit (the editor's map editor, or
code mode); `edit_map` set-attr can still tune attributes on existing nodes. The agent
tools below **enumerate, validate, and build** the branches.

## Process

1. **Inventory** — `list_branches <map>` reports each variant, its DITAVAL, and its
   generated key scope. (Zero branches → the map has no `<ditavalref>`.)
2. **Build/validate per variant** — a branch-filtered map is just a map to the engine:
   `validate_deep <map>` and `build_deliverables` expand the branches natively. The
   deliverable-level DITAVAL (if any) and the in-map branch DITAVALs **compose** —
   `args.filter` is global, branch DITAVALs are per-subtree.
3. **Attribute failures to a variant** — after `validate_deep`, a diagnostic whose
   generated file carries a branch's `dvrResourcePrefix`/`Suffix` is tagged
   `[branch: win-]` in the validation results, so you can say *which* platform/audience
   variant a problem affects, not just that something failed.

## The engine boundary (be honest)

In-process the tools **enumerate, preview, validation-scope, and surface** branches.
The actual **subtree duplication, resource renaming, and key renaming are DITA-OT's**
at build — don't claim to have produced the variant bytes from the editor. Per-branch
diagnostic attribution is **best-effort** (by the resource affix); a branch with no
`dvr*` affix can't be attributed that way.

## Report

The variants the map produces (count + each one's DITAVAL/prefix), the per-variant
validation result with branch attribution, and what was built. To restructure the map
that carries the branches, use [[dita-map-editing]]; for the deliverable model branches
extend, [[dita-deliverables]].
