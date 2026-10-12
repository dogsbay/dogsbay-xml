---
name: dita-conref-audit
description: Audit and repair conref/keyref reuse across a DITA map - find broken conrefs, dangling keys, and duplicated content that should be conreffed. Use when asked to check reuse, fix broken conrefs, or deduplicate repeated content in DITA.
---

# DITA Conref / Keyref Audit

Audit content reuse across the map and repair what is broken.

## Process

1. Map the reuse graph:
   - `grep` for `conref=`, `conkeyref=`, `keyref=` across the map's topics.
   - `xpath` the map for `<keydef>` elements to build the key space.
2. Detect problems:
   - **Broken conrefs**: target file missing, or target `id` not present
     in the target file (verify with `xpath` on the target).
   - **Dangling keyrefs**: keys used in topics but not defined in any
     keydef (or defined more than once with different targets).
   - **Reuse candidates**: identical or near-identical paragraphs, notes,
     or steps appearing in 3+ topics (compare grep hits) that should live
     once in a warehouse topic and be conreffed.
3. Repair with `edit`:
   - Fix conref paths/ids when the intended target is unambiguous.
   - Add missing keydefs to the map when a key's target is obvious from
     usage; otherwise report.
   - For reuse candidates, only restructure when the user asked for
     deduplication: create/extend a warehouse topic, move the canonical
     content there with a stable `id`, and replace duplicates with
     conrefs.
4. Validate every touched file (`xml_validate` with `dtd: true`) before
   finishing.

## Report

List repairs made, ambiguous cases left for a human (with file:line),
and reuse statistics (keys defined/used, conref count, duplicates found).
