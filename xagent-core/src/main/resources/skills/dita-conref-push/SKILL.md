---
name: dita-conref-push
description: Audit DITA conref push (conaction) — validate pushbefore/pushafter/pushreplace operations, their mark-sibling pairing, and that the push targets resolve. Use when asked to check conref-push operations, debug content that isn't being pushed into a target, or before publishing content that uses conaction.
---

# DITA conref push (conaction)

Conref *push* is the inverse of conref pull: instead of a topic pulling shared content
in, a source pushes its content **into** a target it references. `@conaction` drives it,
and DITA-OT resolves it late (at build), so problems are easy to miss. The tool:
`conref_push_audit` (inventory + pairing + target resolution).

## The pairing rules (what the audit checks)

- **pushbefore / pushafter** — needs a sibling element with `conaction="mark"` that
  carries the `@conref`/`@conkeyref` identifying *where* to push. A push with no mark
  sibling pushes nowhere.
- **mark** — needs a `pushbefore`/`pushafter` sibling; a mark with nothing to push is
  inert.
- **pushreplace** — carries its **own** `@conref`/`@conkeyref` to the element it
  replaces; no separate mark.
- The push target (`@conref` `file#topic/id`, or `@conkeyref` `key/id`) must **resolve**
  to a real element — broken targets silently drop the push.

## Process

1. **Audit** — `conref_push_audit <root> --root-map <map>` (the map resolves
   `conkeyref` keys): the inventory of mark/pushbefore/pushafter/pushreplace, broken
   **pairing**, and **unresolvable targets**.
2. **Fix pairing** — add the missing `mark` (with the target `@conref`) for an orphan
   push, or the missing push for an orphan mark; give a `pushreplace` its target.
3. **Fix targets** — correct the `@conref` path/id or the `@conkeyref` key so it points
   at a real element in a real file (the audit names the missing id).
4. **Verify with the engine** — conref push only materializes at build; after fixing,
   `validate_deep` (DITA-OT) confirms the pushed content lands where intended.

## Report

The push operations found, the pairing/target problems and how each was fixed, and a
note to deep-validate. Conref push is reuse — for the pull side and warehousing, see
[[dita-conref-reuse]] and [[dita-conref-audit]].
