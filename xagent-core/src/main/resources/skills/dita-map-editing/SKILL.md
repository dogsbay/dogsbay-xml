---
name: dita-map-editing
description: Structurally edit a DITA map with edit_map — add, move, remove, or re-attribute topicrefs, reference-safely and formatting-preservingly. Use when asked to reorganize a map, add/remove a topicref, move a topic between sections or maps, or set topicref attributes (toc, navtitle, etc.).
---

# Edit a DITA map's structure

`edit_map` changes a map's `<topicref>` tree by splicing the source text (formatting,
comments, and untouched attributes survive) — never by re-serializing. Four ops:
`set-attr`, `insert`, `remove`, `move`.

## Addressing

Target a topicref by **selector**: its `@id` (preferred — stable), else a 1-based
**child-path** like `/1/3` (`/` is the map root). Prefer `@id`; child-paths shift if
siblings change. The map root is a valid `parent` (`/`).

## Gotchas the tool can't decide for you

- **`mapref`/`keydef`/`glossref` are leaves** — you don't nest topicrefs *into* a
  referenced submap; only the mapref itself moves. The content model rejects illegal
  nesting, but choose the right parent.
- **`--dry-run` first on every move/remove.** It returns the plan (files that would
  change) and any warnings without writing — read it before committing.
- **Cross-map move** (`--to-map`) rebases the moved subtree's `@href`/`@conref` to
  the destination directory automatically, but **inbound** references to the moved
  topics are only *warned about*, not rewritten — check the warnings.
- **Remove warns, doesn't clean up.** Removing a topicref leaves the topic file on
  disk; if it's still referenced elsewhere the plan says so. To also delete the file
  reference-safely, use [[dita-refactor]]'s `delete_file`.
- **Don't hand-edit the map** for these changes — `edit_map` preserves formatting;
  a manual DOM rewrite reflows the whole file.

## Process

1. Read the current structure (open the map / `get_outline`) and pick selectors.
2. **set-attr** — `set-attr --ref <id> --name <attr> --value <v>` (empty value
   removes it); works for `navtitle`, `toc`, `print`, `processing-role`, `chunk`,
   `collection-type`, `scope`, `type`, `keys`, `keyref`, `href`.
3. **insert** — `insert --parent <id|/> --index <n> --type topicref|topichead|mapref
   --href … --navtitle …` (omit `--index` to append).
4. **remove** — `remove --ref <id>` (dry-run first; heed inbound-ref warnings).
5. **move** — `move --ref <id> --parent <id|/> --index <n>`; add `--to-map <path>`
   to move into another map.
6. After structural change, validate the map (and consider deep-validate via
   [[dita-deliverables]] to catch keyref/conref the static checks can't).

## Report

The ops applied, files changed, any reference warnings (and what you did about
them), and confirmation the map still validates.
