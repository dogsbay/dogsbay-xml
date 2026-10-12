---
name: dita-keys
description: Manage a DITA key space — replace hardcoded strings with keyrefs (keyify), define/rename/merge/inline keys, and resolve a key's value. Use when asked to keyify product names or other repeated literals, clean up duplicate or stale key definitions, or rename/retire a key across a project.
---

# Manage the DITA key space

Keys centralize values (product names, versions, URLs) and link targets so they
change in one place. The tools: `keyify`, `list_keys`, `resolve_key`,
`create_keydef`, `rename_key`, `merge_keydefs`, `inline_key`.

## Keyify — the judgment call

`keyify` replaces a hardcoded literal with `<… keyref="key"/>`. **What to keyify is
a judgment, not a sweep:**

- **Do** keyify the literal in running prose, titles, and shortdescs.
- **Do not** keyify inside `<codeblock>`, `<cmdname>`, `<filepath>`, `<userinput>`,
  shell commands, or example output — those are literal by design; replacing them
  breaks copy-paste.
- A version/URL/extension is usually a key (`product-version`, `download-url`,
  `project-extension`); a one-off proper noun usually is not.
- Confirm the key exists first with `list_keys`; if it doesn't, `create_keydef` it
  (a text key holds its value in `<keyword>`).

For a repetitive keyify across many files, drive it in code mode, applying the same
"prose yes / code no" rule per occurrence.

## Process

1. **Survey** with `list_keys` — the defined keys, their values, and which are
   undefined (dangling keyref) or unused. Fix typo'd keyrefs against the real key.
2. **Resolve** a value with `resolve_key <key>` (respects key-space precedence:
   first definition in the map closure wins).
3. **Keyify** the safe occurrences (above); leave code/paths.
4. **Tidy:**
   - `merge_keydefs <root-map>` removes **shadowed duplicate** keydefs (the same key
     defined twice in the closure — the loser is dead weight that drifts stale).
   - `rename_key <old> <new>` renames a key and **every** keyref/conkeyref in one
     reference-safe pass.
   - `inline_key <key>` is the escape hatch — replace keyrefs with the resolved
     literal when a key is no longer wanted.
5. Preview destructive ops with their dry-run, then apply.

## Report

The keys touched (defined/renamed/merged/inlined), the occurrences keyified vs
deliberately skipped (and why — code/paths), and any dangling keyrefs fixed. Link
[[dita-conref-reuse]] for content (not value) reuse.
