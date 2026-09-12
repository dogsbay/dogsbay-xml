---
name: dita-key-scopes
description: Reuse the same DITA key name in more than one place without collisions, using DITA 1.3 key scopes (@keyscope). Use when a key resolves to the wrong target because two parts of a map (or two reused submaps) define the same key, when combining guides into one collection, or when asked to scope keys per section/edition/branch.
---

# DITA 1.3 key scopes (`@keyscope`)

A **key scope** lets the *same bare key name* bind to different definitions in
different parts of a map. `@keyscope="s"` on a map/topicref/topicgroup names a scope:
keys defined inside it bind locally as `key` and externally as `s.key`. Inner scopes
shadow outer ones. This is the mechanism for **reusing a sub-publication many times**
(e.g. several guides in one collection, or a section duplicated per edition) without
their keys clashing. Tools: `list_keys`, `resolve_key` (with a `--scope`), `edit_map`.

## When you need a scope (the symptom)

Without scopes, a map flattens all keys into one namespace and **first-definition
wins**. So if two reused submaps each define `start-here` (or `product-name`,
`intro`…), only one survives and the others are silently shadowed — a key resolves to
the wrong topic. That silent collision is the signal to introduce a scope. (Contrast
[[dita-keys]] `merge_keydefs`, which *removes* a shadowed duplicate you don't want —
here you *keep* both, kept apart by scope.)

## Process

1. **Confirm the collision.** `list_keys <map>` — if a key you expect in several
   places appears once (or resolves to the wrong file), the definitions are colliding
   in one flat namespace.
2. **Scope each reuse.** Put the colliding subtrees under their own `@keyscope`. For a
   collection that maprefs several guides, scope each mapref:
   `edit_map <map> set-attr --ref <selector> --name keyscope --value <scope>`
   (the selector is an `@id` or a 1-based child path like `/2/1`). Dry-run first.
3. **Verify per scope.** Keys now coexist as `scope.key`:
   - `list_keys <map>` shows the fully-qualified names (`userguide.start-here`,
     `podcaster.start-here`).
   - `resolve_key <key> --scope <scope>` resolves a bare key *as referenced from that
     scope* (it walks outward to the root if the scope doesn't define it). Same key
     name, a different binding per scope.

## What scoping fixes (it propagates)

Scoping isn't just `resolve_key`. **Every key consumer becomes scope-aware:**
`list_keys` (qualified names), `where_used` (finds a scoped key's references),
`conref_audit` and the publication-set crawl (resolve scoped targets), and the DITA
preview (renders the scoped binding). So fixing the scope fixes the key *everywhere*
it's used, not just where it's defined.

## Honest limits (say these)

- In-process resolution is **exact** when you name the scope (`--scope`) or when a
  bare key is unique to a single scope.
- Scope a topic *inherits* from where a map **places** it (cross-file) is the engine's
  job at build time; in-process a bare `keyref` in topic content is read in the root
  scope (a unique-scope fallback bridges the common case but can't disambiguate a name
  defined in several scopes).
- Branch filtering ([[dita-branch-filtering]]) auto-generates a key scope per branch
  (via `dvrKeyscope*`), so each filtered variant gets a distinct key binding.

## Report

The collision found (which key, which definitions), the scopes introduced and on which
maprefs/subtrees, and the per-scope resolution (`scope.key → target`) proving the keys
now coexist. For the rest of the key lifecycle (keyify/rename/merge/inline) use
[[dita-keys]].
