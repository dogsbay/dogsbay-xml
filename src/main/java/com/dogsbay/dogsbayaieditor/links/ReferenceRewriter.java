/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans and applies reference rewrites — the engine under rename/move
 * refactorings. Two-phase by design: {@link #planRenameFile} computes every
 * attribute edit without touching anything (the dry-run / preview model);
 * {@link #apply} executes a plan.
 *
 * <p>Edits are applied <em>textually</em>: the exact
 * {@code attr="oldValue"} (or single-quoted) occurrence is replaced in the
 * file content, so XML formatting, comments, and entity usage outside the
 * edited attribute are untouched — no DOM round-trip. Files are read and
 * written as UTF-8.
 */
public final class ReferenceRewriter {

    /**
     * One attribute rewrite in one file. {@code newAttribute} non-null means
     * the attribute itself is renamed (keyify: {@code href} → {@code keyref};
     * inline: {@code conkeyref} → {@code conref}); rename edits also carry
     * the owning {@code element} so application can target the right
     * occurrence when the same attribute=value appears on other elements
     * that must stay untouched (xref keyref vs ph keyref).
     */
    public record AttributeEdit(File file, int line, String element,
                                String attribute, String oldValue,
                                String newValue, String newAttribute) {

        /** Value-only edit (attribute name unchanged, applied by value). */
        public AttributeEdit(File file, int line, String attribute,
                             String oldValue, String newValue) {
            this(file, line, null, attribute, oldValue, newValue, null);
        }

        /** The attribute name after the edit. */
        public String effectiveAttribute() {
            return newAttribute != null ? newAttribute : attribute;
        }

        /** True when the edit renames the attribute (applied per occurrence). */
        public boolean isRename() {
            return newAttribute != null;
        }
    }

    /**
     * One element to insert before a file's closing root tag
     * (keyify adds a {@code <keydef>} to the chosen map).
     */
    public record ElementInsertion(File file, String elementText) {
    }

    /**
     * One whole-element removal (safe delete strips dead map references).
     * The element is located textually by its {@code attribute="value"}
     * occurrence.
     */
    public record ElementRemoval(File file, int line, String element,
                                 String attribute, String value) {
    }

    /**
     * A computed refactoring: attribute edits, element removals and
     * insertions, and an optional file move or delete.
     */
    public record Plan(List<AttributeEdit> edits, List<ElementRemoval> removals,
                       List<ElementInsertion> insertions,
                       File moveFrom, File moveTo, File deleteTarget,
                       List<String> warnings) {

        /** Edits-and-move plan (no removals, insertions, or delete). */
        public Plan(List<AttributeEdit> edits, File moveFrom, File moveTo,
                    List<String> warnings) {
            this(edits, List.of(), List.of(), moveFrom, moveTo, null, warnings);
        }

        /** Removals-and-delete plan (safe delete). */
        public Plan(List<AttributeEdit> edits, List<ElementRemoval> removals,
                    File moveFrom, File moveTo, File deleteTarget,
                    List<String> warnings) {
            this(edits, removals, List.of(), moveFrom, moveTo, deleteTarget, warnings);
        }
    }

    /** Outcome of applying a plan. */
    public record ApplyResult(int editsApplied, int filesChanged, boolean moved,
                              boolean deleted, List<String> failures) {
    }

    private ReferenceRewriter() {
    }

    // -------------------------------------------------------------------------
    // Planning
    // -------------------------------------------------------------------------

    /**
     * Plans renaming/moving {@code target} to {@code newPath}: every direct
     * path reference (href/conref/src/xi:include) to the target, in maps and
     * topics under {@code root}, gets its value recomputed as a relative
     * path from the referencing file to the new location (fragments
     * preserved). Key-mediated references need no edit — the keydef's href
     * is itself a direct reference and is rewritten.
     *
     * @param index pre-built index over the scope root
     */
    public static Plan planRenameFile(File target, File newPath, ReverseLinkIndex index) {
        List<String> warnings = new ArrayList<>();
        if (newPath.exists()) {
            warnings.add("Destination already exists: " + newPath);
        }
        if (!isSameExtension(target, newPath)) {
            warnings.add("Extension changes from "
                    + extensionOf(target) + " to " + extensionOf(newPath));
        }
        return new Plan(redirectEdits(target, newPath, index), target, newPath, warnings);
    }

    /**
     * Plans retargeting: every reference to {@code from} is rewritten to
     * point at {@code to} instead. Neither file is moved or deleted —
     * {@code to} should already exist.
     */
    public static Plan planRetarget(File from, File to, ReverseLinkIndex index) {
        List<String> warnings = new ArrayList<>();
        if (!to.isFile()) {
            warnings.add("New target does not exist: " + to);
        }
        List<AttributeEdit> edits = redirectEdits(from, to, index);
        if (edits.stream().anyMatch(e -> e.newValue().contains("#"))) {
            warnings.add("Fragment(s) preserved — verify the ids exist in "
                    + to.getName());
        }
        return new Plan(edits, null, null, warnings);
    }

    /** Every direct reference to {@code target}, re-pointed at {@code newPath}. */
    private static List<AttributeEdit> redirectEdits(File target, File newPath,
                                                     ReverseLinkIndex index) {
        List<AttributeEdit> edits = new ArrayList<>();
        for (Reference ref : index.usagesOf(target)) {
            if (ref.source() == null) {
                continue;
            }
            String fragment = ref.fragment() != null ? "#" + ref.fragment() : "";
            String newValue = relativePath(ref.source().getParentFile(), newPath) + fragment;
            if (!newValue.equals(ref.rawValue())) {
                edits.add(new AttributeEdit(ref.source(), ref.line(),
                        ref.attribute(), ref.rawValue(), newValue));
            }
        }
        return edits;
    }

    /**
     * Plans renaming key {@code oldKey} to {@code newKey}: every keydef-style
     * definition (any element whose {@code @keys} token list contains the
     * key, in every {@code .ditamap} under the scanned root — ALL
     * definitions, not just the effective one, so a shadowed duplicate
     * can't silently become live) plus every {@code keyref}/{@code conkeyref}
     * usage, with element-id segments preserved.
     */
    public static Plan planRenameKey(String oldKey, String newKey, ReverseLinkIndex index) {
        List<String> warnings = new ArrayList<>();
        List<AttributeEdit> edits = new ArrayList<>();

        // Definitions: scan the maps the index covers.
        boolean foundDefinition = false;
        boolean newAlreadyDefined = false;
        for (File file : index.indexedFiles()) {
            if (!file.getName().toLowerCase().endsWith(".ditamap")) {
                continue;
            }
            for (KeyDefinition def : LinkExtractor.extract(file).keyDefinitions()) {
                List<String> tokens = new ArrayList<>(
                        java.util.Arrays.asList(def.keys().split("\\s+")));
                if (tokens.contains(newKey)) {
                    newAlreadyDefined = true;
                }
                if (tokens.contains(oldKey)) {
                    foundDefinition = true;
                    String newValue = String.join(" ", tokens.stream()
                            .map(t -> t.equals(oldKey) ? newKey : t)
                            .toList());
                    edits.add(new AttributeEdit(def.source(), def.line(),
                            "keys", def.keys(), newValue));
                }
            }
        }

        // Usages: keyref/conkeyref, element-id segment preserved.
        List<Reference> usages = index.usagesOfKey(oldKey);
        for (Reference ref : usages) {
            if (ref.source() == null) {
                continue;
            }
            String newValue = ref.fragment() != null
                    ? newKey + "/" + ref.fragment() : newKey;
            edits.add(new AttributeEdit(ref.source(), ref.line(),
                    ref.attribute(), ref.rawValue(), newValue));
        }

        if (newAlreadyDefined) {
            warnings.add("Key '" + newKey + "' is already defined — renaming "
                    + "creates a collision (first definition wins)");
        }
        if (!foundDefinition && usages.isEmpty()) {
            warnings.add("Key '" + oldKey + "' has no definitions and no usages "
                    + "under the scanned root");
        } else if (!foundDefinition) {
            warnings.add("Key '" + oldKey + "' is used but not defined in any "
                    + "map under the scanned root");
        }
        return new Plan(edits, null, null, warnings);
    }

    /** Map elements whose removal is the obviously-intended delete cleanup. */
    private static final java.util.Set<String> MAP_REFERENCE_ELEMENTS = java.util.Set.of(
            "topicref", "mapref", "chapter", "appendix", "part",
            "topichead", "topicgroup", "keydef", "glossref", "anchorref");

    /**
     * Plans deleting {@code target}. Map references (topicref/keydef/...)
     * are removable when {@code removeRefs} is true and the element has no
     * reference-bearing descendants; everything else — content reuse
     * (conref), links, images — is reported as a warning and left to the
     * health report, never auto-edited. Keys whose keydef points at the
     * target are warned about: their keyrefs go undefined.
     *
     * @param index pre-built index over the scope root
     */
    public static Plan planDeleteFile(File target, boolean removeRefs, ReverseLinkIndex index) {
        List<String> warnings = new ArrayList<>();
        List<ElementRemoval> removals = new ArrayList<>();
        Map<File, String> contents = new LinkedHashMap<>();

        for (Reference ref : index.usagesOf(target)) {
            if (ref.source() == null) {
                continue;
            }
            String where = ref.source().getName() + ":" + ref.line();
            boolean mapReference = MAP_REFERENCE_ELEMENTS.contains(ref.element());

            if (mapReference && removeRefs) {
                String content = contents.computeIfAbsent(ref.source(), f -> {
                    try {
                        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return null;
                    }
                });
                if (content != null
                        && removeElement(content, ref.attribute(), ref.rawValue()) != null) {
                    removals.add(new ElementRemoval(ref.source(), ref.line(),
                            ref.element(), ref.attribute(), ref.rawValue()));
                } else {
                    warnings.add(where + " — <" + ref.element() + " " + ref.attribute()
                            + "=\"" + ref.rawValue() + "\"> not auto-removable "
                            + "(nested references) — left broken");
                }
            } else {
                warnings.add(where + " — " + describeBreakage(ref, mapReference));
            }
        }

        // Keys defined against the target: their keyrefs go undefined.
        for (File file : index.indexedFiles()) {
            if (!file.getName().toLowerCase().endsWith(".ditamap")) {
                continue;
            }
            for (KeyDefinition def : LinkExtractor.extract(file).keyDefinitions()) {
                File resolved = resolveAgainst(file, def.href());
                if (resolved == null || !sameFile(resolved, target)) {
                    continue;
                }
                for (String key : def.keys().split("\\s+")) {
                    int uses = index.usagesOfKey(key).size();
                    if (uses > 0) {
                        warnings.add("key '" + key + "' becomes undefined ("
                                + uses + " usage" + (uses == 1 ? "" : "s") + ")");
                    }
                }
            }
        }
        return new Plan(List.of(), removals, null, null, target, warnings);
    }

    /**
     * Plans keyify: convert direct references to {@code target} into key
     * references, and add a {@code <keydef>} for {@code key} to
     * {@code mapFile}. Converted: {@code href} on xref/link/image →
     * {@code keyref}; {@code conref} → {@code conkeyref}. Map references
     * (topicref/keydef hrefs) are left direct — navigation hrefs are normal
     * DITA; a count is warned for awareness. Fragments translate as
     * {@code path#topicId/elemId} → {@code key/elemId} and
     * {@code path#topicId} → {@code key}.
     */
    public static Plan planKeyify(File target, String key, File mapFile,
                                  ReverseLinkIndex index, KeySpace keySpace) {
        return planKeyify(target, key, mapFile, index, keySpace, null);
    }

    /**
     * Keyify with reachability context: when {@code rootMap} is given and
     * {@code mapFile} is not in its submap closure, the plan warns that the
     * new key won't resolve until a mapref includes the keydef map.
     */
    public static Plan planKeyify(File target, String key, File mapFile,
                                  ReverseLinkIndex index, KeySpace keySpace,
                                  File rootMap) {
        List<String> warnings = new ArrayList<>();
        addKeyCollisionWarnings(keySpace, key, warnings);
        if (rootMap != null && rootMap.isFile()
                && !KeySpace.mapsInClosure(rootMap)
                        .contains(LinkExtractor.canonical(mapFile))) {
            warnings.add("Keydef map " + mapFile.getName() + " is not included "
                    + "from " + rootMap.getName() + " — the new key won't "
                    + "resolve until a mapref includes it");
        }

        List<AttributeEdit> edits = new ArrayList<>();
        int mapRefsLeft = 0;
        for (Reference ref : index.usagesOf(target)) {
            if (ref.source() == null) {
                continue;
            }
            if (MAP_REFERENCE_ELEMENTS.contains(ref.element())) {
                mapRefsLeft++;
                continue;
            }
            String elemId = elementIdOfFragment(ref.fragment());
            String keyValue = elemId != null ? key + "/" + elemId : key;
            if ("href".equals(ref.attribute())) {
                edits.add(new AttributeEdit(ref.source(), ref.line(), ref.element(),
                        "href", ref.rawValue(), keyValue, "keyref"));
            } else if ("conref".equals(ref.attribute())) {
                edits.add(new AttributeEdit(ref.source(), ref.line(), ref.element(),
                        "conref", ref.rawValue(), keyValue, "conkeyref"));
            } else {
                warnings.add(ref.source().getName() + ":" + ref.line() + " — <"
                        + ref.element() + " " + ref.attribute()
                        + "> not convertible to a key reference — left direct");
            }
        }
        if (mapRefsLeft > 0) {
            warnings.add(mapRefsLeft + " map reference(s) (topicref/keydef) "
                    + "left as direct hrefs");
        }

        String keydef = "<keydef keys=\"" + key + "\" href=\""
                + relativePath(mapFile.getParentFile(), target) + "\"/>";
        return new Plan(edits, List.of(),
                List.of(new ElementInsertion(mapFile, keydef)),
                null, null, null, warnings);
    }

    /**
     * Plans inlining a key: every {@code keyref}/{@code conkeyref} usage is
     * rewritten to the direct path the key resolves to. Text-pulling
     * keyrefs (ph/keyword/term) have no href equivalent and are warned
     * about; the keydef itself stays (health reports it once unused).
     */
    /** Warn when creating a root {@code <keydef keys=key>}: a hard collision if the
     *  key already exists at root, or a softer shadowing risk if it exists only inside
     *  a key scope. Shared by every keydef-creating plan. */
    private static void addKeyCollisionWarnings(KeySpace keySpace, String key,
                                                List<String> warnings) {
        if (keySpace.resolve(key) != null) {
            warnings.add("Key '" + key + "' is already defined — the new "
                    + "keydef creates a collision (first definition wins)");
        } else if (keySpace.resolveLenient(key, "") != null) {
            warnings.add("Key '" + key + "' is already defined inside a key scope "
                    + "— the new root keydef may be shadowed there (DITA 1.3 key scopes)");
        }
    }

    /** Key references that resolve (each in its own authoring scope) to {@code def} —
     *  the scope-correct usage set, vs {@code usagesOfKey}'s literal-name match. */
    private static List<Reference> usagesResolvingTo(KeyDefinition def,
            ReverseLinkIndex index, KeySpace keySpace) {
        List<Reference> out = new ArrayList<>();
        for (Reference ref : index.allReferences()) {
            if (ref.isKeyReference()
                    && def.equals(keySpace.resolveLenient(ref.keyName(), ref.scope()))) {
                out.add(ref);
            }
        }
        return out;
    }

    public static Plan planInlineKey(String key, ReverseLinkIndex index,
                                     KeySpace keySpace) {
        List<String> warnings = new ArrayList<>();
        // Lenient: a bare key that is only defined inside a @keyscope still inlines.
        KeyDefinition def = keySpace.resolveLenient(key, "");
        File target = keySpace.resolveHrefFileLenient(key, "");
        if (def == null) {
            warnings.add("Key '" + key + "' is not defined in the context map");
            return new Plan(List.of(), null, null, warnings);
        }
        if (target == null || !target.isFile()) {
            warnings.add("Key '" + key + "' has no resolvable href target — "
                    + "nothing to inline");
            return new Plan(List.of(), null, null, warnings);
        }

        String topicId = null;                     // parsed only when needed
        List<AttributeEdit> edits = new ArrayList<>();
        // Match usages by the definition they resolve to (scope-aware), not by the
        // literal key string — a bare keyref under @keyscope and a qualified inline
        // request both point at the same def.
        for (Reference ref : usagesResolvingTo(def, index, keySpace)) {
            if (ref.source() == null) {
                continue;
            }
            String relative = relativePath(ref.source().getParentFile(), target);
            boolean isLink = "xref".equals(ref.element()) || "link".equals(ref.element())
                    || "image".equals(ref.element());

            if ("conkeyref".equals(ref.attribute())) {
                if (ref.fragment() == null) {
                    warnings.add(ref.source().getName() + ":" + ref.line()
                            + " — conkeyref without element id — left as is");
                    continue;
                }
                if (topicId == null) {
                    topicId = rootTopicId(target);
                }
                if (topicId == null) {
                    warnings.add(ref.source().getName() + ":" + ref.line()
                            + " — target topic id unknown — left as is");
                    continue;
                }
                edits.add(new AttributeEdit(ref.source(), ref.line(), ref.element(),
                        "conkeyref", ref.rawValue(),
                        relative + "#" + topicId + "/" + ref.fragment(), "conref"));
            } else if (isLink) {
                String newValue = relative;
                if (ref.fragment() != null) {
                    if (topicId == null) {
                        topicId = rootTopicId(target);
                    }
                    if (topicId == null) {
                        warnings.add(ref.source().getName() + ":" + ref.line()
                                + " — target topic id unknown — left as is");
                        continue;
                    }
                    newValue = relative + "#" + topicId + "/" + ref.fragment();
                }
                edits.add(new AttributeEdit(ref.source(), ref.line(), ref.element(),
                        "keyref", ref.rawValue(), newValue, "href"));
            } else {
                warnings.add(ref.source().getName() + ":" + ref.line() + " — <"
                        + ref.element() + " keyref> pulls the key's text — not "
                        + "inlinable, left as is");
            }
        }
        if (edits.isEmpty() && warnings.isEmpty()) {
            warnings.add("Key '" + key + "' has no usages under the scanned root");
        }
        return new Plan(edits, null, null, warnings);
    }

    /**
     * Plans creating a text keydef ("extract variable"): a
     * {@code <keydef>} whose keyword is {@code text} is added to
     * {@code mapFile}. Warns on key collision (against the given key
     * space) and, with {@code rootMap} context, when the map isn't in the
     * root map's closure. The caller replaces its selection with
     * {@code <ph keyref="key"/>} separately.
     */
    public static Plan planCreateKeydef(String key, String text, File mapFile,
                                        KeySpace keySpace, File rootMap) {
        List<String> warnings = new ArrayList<>();
        addKeyCollisionWarnings(keySpace, key, warnings);
        if (rootMap != null && rootMap.isFile()
                && !KeySpace.mapsInClosure(rootMap)
                        .contains(LinkExtractor.canonical(mapFile))) {
            warnings.add("Keydef map " + mapFile.getName() + " is not included "
                    + "from " + rootMap.getName() + " — the new key won't "
                    + "resolve until a mapref includes it");
        }
        String keydef = "<keydef keys=\"" + key + "\"><topicmeta><keywords>"
                + "<keyword>" + text + "</keyword>"
                + "</keywords></topicmeta></keydef>";
        return new Plan(List.of(), List.of(),
                List.of(new ElementInsertion(mapFile, keydef)),
                null, null, null, warnings);
    }

    /**
     * Plans renaming an element id in {@code file}: the {@code id}
     * attribute itself plus every reference fragment that names it —
     * {@code #topicId/oldId} (and, when the old id IS the topic id,
     * {@code #oldId} and {@code #oldId/...}) on href/conref, and
     * {@code key/oldId} on keyref/conkeyref when {@code keySpace} (from a
     * root map) shows the key resolves to this file.
     */
    public static Plan planRenameElementId(File file, String oldId, String newId,
                                           ReverseLinkIndex index, KeySpace keySpace) {
        List<String> warnings = new ArrayList<>();
        List<AttributeEdit> edits = new ArrayList<>();

        String content;
        try {
            content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Cannot read " + file.getName() + ": " + e.getMessage());
            return new Plan(edits, null, null, warnings);
        }
        int[] span = elementSpan(content, "id", oldId);
        if (span == null) {
            warnings.add("No element with id=\"" + oldId + "\" in " + file.getName());
            return new Plan(edits, null, null, warnings);
        }
        if (elementSpan(content, "id", newId) != null) {
            warnings.add("An element with id=\"" + newId + "\" already exists in "
                    + file.getName());
        }
        boolean isTopicId = oldId.equals(rootTopicId(file));
        edits.add(new AttributeEdit(file, lineOf(content, span[0]),
                "id", oldId, newId));

        for (Reference ref : index.usagesOf(file)) {
            if (ref.source() == null || ref.fragment() == null) {
                continue;
            }
            String fragment = ref.fragment();
            String newFragment = null;
            if (isTopicId) {
                if (fragment.equals(oldId)) {
                    newFragment = newId;
                } else if (fragment.startsWith(oldId + "/")) {
                    newFragment = newId + fragment.substring(oldId.length());
                }
            } else {
                int slash = fragment.indexOf('/');
                if (slash >= 0 && fragment.substring(slash + 1).equals(oldId)) {
                    newFragment = fragment.substring(0, slash + 1) + newId;
                }
            }
            if (newFragment == null) {
                continue;
            }
            String raw = ref.rawValue();
            int hash = raw.indexOf('#');
            String newValue = (hash >= 0 ? raw.substring(0, hash) : raw)
                    + "#" + newFragment;
            edits.add(new AttributeEdit(ref.source(), ref.line(),
                    ref.attribute(), raw, newValue));
        }

        // Key-mediated references: keyref/conkeyref "key/oldId" where the
        // key's target is this file.
        if (keySpace != null && !keySpace.isEmpty() && !isTopicId) {
            for (String keyName : keySpace.entries().keySet()) {
                File resolved = keySpace.resolveHrefFile(keyName);
                if (resolved == null || !sameFile(resolved, file)) {
                    continue;
                }
                for (Reference ref : index.usagesOfKey(keyName)) {
                    if (ref.source() == null || !oldId.equals(ref.fragment())) {
                        continue;
                    }
                    edits.add(new AttributeEdit(ref.source(), ref.line(),
                            ref.attribute(), ref.rawValue(), keyName + "/" + newId));
                }
            }
        } else if ((keySpace == null || keySpace.isEmpty()) && !isTopicId) {
            warnings.add("Key-based usages (conkeyref/keyref with element ids) "
                    + "were not checked — provide a root map for full coverage");
        }
        return new Plan(edits, null, null, warnings);
    }

    /**
     * Plans merging duplicate keydefs in a root map's closure: for every
     * key defined more than once, the first (effective) definition wins
     * and the shadowed ones are removed — whole keydef elements when all
     * their keys are shadowed, otherwise just the shadowed tokens are
     * dropped from {@code @keys}.
     *
     * @param onlyKey restrict to one key; null merges all duplicates
     */
    public static Plan planMergeKeydefs(File rootMap, String onlyKey) {
        List<String> warnings = new ArrayList<>();
        List<AttributeEdit> edits = new ArrayList<>();
        List<ElementRemoval> removals = new ArrayList<>();

        // Definitions in first-wins (BFS) order, per key.
        Map<String, List<KeyDefinition>> byKey = new LinkedHashMap<>();
        for (String canon : KeySpace.mapsInClosure(rootMap)) {
            File map = new File(canon);
            if (!map.isFile()) {
                continue;
            }
            for (KeyDefinition def : LinkExtractor.extract(map).keyDefinitions()) {
                for (String token : def.keys().split("\\s+")) {
                    if (!token.isEmpty()) {
                        byKey.computeIfAbsent(token, k -> new ArrayList<>()).add(def);
                    }
                }
            }
        }

        // Shadowed tokens, grouped per keydef instance so a multi-key
        // keydef is edited once (or removed when every token is shadowed).
        Map<KeyDefinition, java.util.Set<String>> shadowedTokens = new LinkedHashMap<>();
        for (Map.Entry<String, List<KeyDefinition>> entry : byKey.entrySet()) {
            String key = entry.getKey();
            if (onlyKey != null && !onlyKey.equals(key)) {
                continue;
            }
            List<KeyDefinition> defs = entry.getValue();
            if (defs.size() < 2) {
                continue;
            }
            KeyDefinition winner = defs.get(0);
            for (KeyDefinition loser : defs.subList(1, defs.size())) {
                shadowedTokens.computeIfAbsent(loser, d -> new java.util.LinkedHashSet<>())
                        .add(key);
                if (!java.util.Objects.equals(winner.href(), loser.href())
                        || !java.util.Objects.equals(winner.keywordText(),
                                loser.keywordText())) {
                    warnings.add("'" + key + "': shadowed definition in "
                            + loser.source().getName() + ":" + loser.line()
                            + " differs from the effective one in "
                            + winner.source().getName() + ":" + winner.line()
                            + " — it was never used in this map's context");
                }
            }
        }
        if (shadowedTokens.isEmpty()) {
            warnings.add(onlyKey != null
                    ? "Key '" + onlyKey + "' has no duplicate definitions under "
                            + rootMap.getName()
                    : "No duplicate key definitions under " + rootMap.getName());
        }

        for (Map.Entry<KeyDefinition, java.util.Set<String>> entry
                : shadowedTokens.entrySet()) {
            KeyDefinition def = entry.getKey();
            List<String> remaining = new ArrayList<>();
            for (String token : def.keys().split("\\s+")) {
                if (!token.isEmpty() && !entry.getValue().contains(token)) {
                    remaining.add(token);
                }
            }
            if (remaining.isEmpty()) {
                removals.add(new ElementRemoval(def.source(), def.line(),
                        "keydef", "keys", def.keys()));
            } else {
                edits.add(new AttributeEdit(def.source(), def.line(),
                        "keys", def.keys(), String.join(" ", remaining)));
            }
        }
        return new Plan(edits, removals, null, null, null, warnings);
    }

    /**
     * Plans renaming a profiling attribute value project-wide:
     * {@code attribute="... oldValue ..."} token lists in every
     * {@code .dita}/{@code .ditamap} under {@code root} (only the matching
     * token changes), plus {@code <prop att="attribute" val="oldValue">}
     * rules in {@code .ditaval} files so filters keep working. A ditaval
     * {@code <prop>} without {@code @att} matches every attribute, so it's
     * warned about instead of edited.
     */
    public static Plan planRenameProfileValue(String attribute, String oldValue,
                                              String newValue, File root)
            throws IOException {
        List<String> warnings = new ArrayList<>();
        List<AttributeEdit> edits = new ArrayList<>();

        java.util.regex.Pattern attrPattern = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(attribute)
                        + "\\s*=\\s*([\"'])(.*?)\\1");
        try (var paths = Files.walk(root.toPath())) {
            for (Path path : paths.filter(p -> {
                        String name = p.toString().toLowerCase();
                        return (name.endsWith(".dita") || name.endsWith(".ditamap"))
                                && !com.dogsbay.dogsbayaieditor.ditaproject.FileSet.inHiddenFolder(root.toPath(), p);
                    }).sorted().toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                java.util.Set<String> seenValues = new java.util.HashSet<>();
                java.util.regex.Matcher m = attrPattern.matcher(content);
                while (m.find()) {
                    String value = m.group(2);
                    List<String> tokens = java.util.Arrays.asList(value.split("\\s+"));
                    if (!tokens.contains(oldValue) || !seenValues.add(value)) {
                        continue;       // replace-all covers repeats of a value
                    }
                    // Map the token, then dedupe (renaming a→b in "a b"
                    // must yield "b", not "b b").
                    java.util.LinkedHashSet<String> mapped = new java.util.LinkedHashSet<>();
                    for (String token : tokens) {
                        if (!token.isEmpty()) {
                            mapped.add(token.equals(oldValue) ? newValue : token);
                        }
                    }
                    String replacement = String.join(" ", mapped);
                    if (mapped.size() < tokens.stream().filter(t -> !t.isEmpty()).count()) {
                        warnings.add(path.toFile().getName() + " — " + attribute
                                + "=\"" + value + "\" already contains '" + newValue
                                + "'; tokens merged to \"" + replacement + "\"");
                    }
                    edits.add(new AttributeEdit(path.toFile(),
                            lineOf(content, m.start()), attribute, value, replacement));
                }
            }
        }

        // DITAVAL rules: <prop att="attribute" val="oldValue" .../>
        java.util.regex.Pattern propPattern =
                java.util.regex.Pattern.compile("<prop\\b[^>]*>");
        try (var paths = Files.walk(root.toPath())) {
            for (Path path : paths.filter(p -> p.toString().toLowerCase().endsWith(".ditaval")
                    && !com.dogsbay.dogsbayaieditor.ditaproject.FileSet.inHiddenFolder(root.toPath(), p))
                    .sorted().toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                java.util.regex.Matcher m = propPattern.matcher(content);
                while (m.find()) {
                    String tag = m.group();
                    String att = attributeValue(tag, "att");
                    String val = attributeValue(tag, "val");
                    if (!oldValue.equals(val)) {
                        continue;
                    }
                    int line = lineOf(content, m.start());
                    if (attribute.equals(att)) {
                        // Per-occurrence rename-style edit (newAttribute set,
                        // same name): a plain value edit would replace-all and
                        // could hit a same-val rule for a DIFFERENT attribute.
                        edits.add(new AttributeEdit(path.toFile(), line, "prop",
                                "val", oldValue, newValue, "val"));
                    } else if (att == null) {
                        warnings.add(path.toFile().getName() + ":" + line
                                + " — <prop val=\"" + oldValue + "\"> without @att "
                                + "applies to ALL attributes — review manually");
                    }
                }
            }
        }

        // Subject-scheme awareness: keep the controlled vocabulary consistent when
        // renaming a governed value, and warn when renaming to a value the scheme
        // doesn't allow (the renamed content would then violate it).
        com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme scheme =
                com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromProjectRoot(root);
        if (scheme.governs(attribute)) {
            if (scheme.allowedValues(attribute).contains(oldValue)) {
                // Renaming a controlled value: also rename its <subjectdef keys=…>.
                renameSubjectKeys(scheme.schemeMaps(), oldValue, newValue, edits);
            } else if (!scheme.allowedValues(attribute).contains(newValue)) {
                warnings.add("“" + newValue + "” is not a controlled value for @"
                        + attribute + " (subject scheme) — the renamed content will violate "
                        + "it; add it to the scheme or choose an allowed value");
            }
        }

        if (edits.isEmpty()) {
            warnings.add("No " + attribute + "=\"…" + oldValue + "…\" occurrences "
                    + "under " + root.getName());
        }
        return new Plan(edits, null, null, warnings);
    }

    /**
     * Rename a controlled value's {@code <subjectdef keys="…oldValue…">} token in
     * the subjectScheme map(s), so the scheme stays consistent with the renamed
     * content/ditavals. Multi-token keys are token-mapped + de-duplicated like
     * profiling values.
     */
    private static void renameSubjectKeys(java.util.Set<String> schemeFiles,
            String oldValue, String newValue, List<AttributeEdit> edits) {
        java.util.regex.Pattern keysPattern = java.util.regex.Pattern.compile(
                "<subjectdef\\b[^>]*\\bkeys\\s*=\\s*([\"'])(.*?)\\1");
        for (String schemeFile : schemeFiles) {
            File f = new File(schemeFile);
            if (!f.isFile()) {
                continue;
            }
            String content;
            try {
                content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.regex.Matcher m = keysPattern.matcher(content);
            while (m.find()) {
                String value = m.group(2);
                List<String> tokens = java.util.Arrays.asList(value.split("\\s+"));
                if (!tokens.contains(oldValue) || !seen.add(value)) {
                    continue;
                }
                java.util.LinkedHashSet<String> mapped = new java.util.LinkedHashSet<>();
                for (String token : tokens) {
                    if (!token.isEmpty()) {
                        mapped.add(token.equals(oldValue) ? newValue : token);
                    }
                }
                edits.add(new AttributeEdit(f, lineOf(content, m.start()), "keys",
                        value, String.join(" ", mapped)));
            }
        }
    }

    /** The value of {@code name="..."} inside a single tag's text, or null. */
    private static String attributeValue(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\b" + name + "\\s*=\\s*([\"'])(.*?)\\1").matcher(tag);
        return m.find() ? m.group(2) : null;
    }

    /** The element-id part of a {@code topicId/elemId} fragment, or null. */
    private static String elementIdOfFragment(String fragment) {
        if (fragment == null) {
            return null;
        }
        int slash = fragment.indexOf('/');
        return slash >= 0 ? fragment.substring(slash + 1) : null;
    }

    /** The id of a topic file's root element, or null. */
    static String rootTopicId(File topic) {
        try {
            String content = Files.readString(topic.toPath(), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<(?:concept|task|topic|reference|glossentry|troubleshooting)"
                            + "[^>]*\\sid=[\"']([^\"']+)[\"']")
                    .matcher(content);
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String describeBreakage(Reference ref, boolean mapReference) {
        String detail = "<" + ref.element() + " " + ref.attribute()
                + "=\"" + ref.rawValue() + "\">";
        if (mapReference) {
            return detail + " left broken (map reference, removal not requested)";
        }
        if ("conref".equals(ref.attribute()) || "conkeyref".equals(ref.attribute())) {
            return detail + " breaks — transcluded content is lost";
        }
        if ("xref".equals(ref.element()) || "link".equals(ref.element())) {
            return detail + " breaks — cross-reference target gone";
        }
        if ("image".equals(ref.element()) || "src".equals(ref.attribute())) {
            return detail + " breaks — image target gone";
        }
        return detail + " breaks";
    }

    private static File resolveAgainst(File map, String href) {
        if (href == null || href.isEmpty()) {
            return null;
        }
        String path = href;
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (path.isEmpty() || path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return null;
        }
        File file = new File(path);
        return file.isAbsolute() ? file : new File(map.getParentFile(), path);
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /** Relative path from {@code fromDir} to {@code to}, forward slashes. */
    static String relativePath(File fromDir, File to) {
        try {
            // Both sides canonical: one canonical and one merely absolute differ
            // wherever the path has a symlink or a Windows short name (RUNNER~1),
            // and the link climbs out to the root and back in by the other spelling.
            Path from = fromDir.getCanonicalFile().toPath();
            Path dest = to.getCanonicalFile().toPath();
            return from.relativize(dest).toString().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return to.getAbsolutePath().replace(File.separatorChar, '/');
        }
    }

    private static boolean isSameExtension(File a, File b) {
        return extensionOf(a).equalsIgnoreCase(extensionOf(b));
    }

    private static String extensionOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    // -------------------------------------------------------------------------
    // Applying
    // -------------------------------------------------------------------------

    /**
     * Applies a plan: rewrites every edit, then performs the move (if any).
     * Each file is rewritten once regardless of how many edits it carries.
     * A file whose expected {@code attr="oldValue"} text can't be found is
     * reported in {@code failures} and skipped — the rest still apply.
     */
    public static ApplyResult apply(Plan plan) {
        List<String> failures = new ArrayList<>();
        int editsApplied = 0;
        int filesChanged = 0;

        Map<File, List<AttributeEdit>> byFile = new LinkedHashMap<>();
        for (AttributeEdit edit : plan.edits()) {
            byFile.computeIfAbsent(edit.file(), f -> new ArrayList<>()).add(edit);
        }

        for (Map.Entry<File, List<AttributeEdit>> entry : byFile.entrySet()) {
            File file = entry.getKey();
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String updated = content;
                int appliedHere = 0;

                // Rename edits (href→keyref etc.) apply per occurrence,
                // element-aware: the same attribute=value on a different
                // element (xref keyref vs ph keyref) must stay untouched.
                for (AttributeEdit edit : entry.getValue()) {
                    if (!edit.isRename()) {
                        continue;
                    }
                    String result = replaceAttributeRename(updated, edit);
                    if (result == null) {
                        failures.add(file + ": could not find <" + edit.element()
                                + " " + edit.attribute() + "=\"" + edit.oldValue() + "\">");
                    } else {
                        updated = result;
                        appliedHere++;
                    }
                }

                // Identical value-only (attr, old, new) edits in one file —
                // e.g. a map's keydef and topicref to the same target — are
                // satisfied by a single replace-all: dedupe, then credit the
                // whole group.
                Map<String, List<AttributeEdit>> groups = new LinkedHashMap<>();
                for (AttributeEdit edit : entry.getValue()) {
                    if (edit.isRename()) {
                        continue;
                    }
                    groups.computeIfAbsent(
                            edit.attribute() + " " + edit.oldValue()
                                    + " " + edit.newValue(),
                            k -> new ArrayList<>()).add(edit);
                }
                for (List<AttributeEdit> group : groups.values()) {
                    AttributeEdit edit = group.get(0);
                    String result = replaceAttribute(updated, edit);
                    if (result == null) {
                        failures.add(file + ": could not find "
                                + edit.attribute() + "=\"" + edit.oldValue() + "\"");
                    } else if (!result.equals(updated)) {
                        updated = result;
                        appliedHere += group.size();
                    }
                }
                if (appliedHere > 0) {
                    Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
                    filesChanged++;
                    editsApplied += appliedHere;
                }
            } catch (IOException e) {
                failures.add(file + ": " + e.getMessage());
            }
        }

        // Element insertions (keyify's keydef), before the closing root tag.
        for (ElementInsertion insertion : plan.insertions()) {
            try {
                File file = insertion.file();
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                int close = content.lastIndexOf("</");
                if (close < 0) {
                    failures.add(file + ": no closing tag to insert before");
                    continue;
                }
                String inserted = content.substring(0, close)
                        + insertion.elementText() + "\n" + content.substring(close);
                Files.writeString(file.toPath(), inserted, StandardCharsets.UTF_8);
                filesChanged++;
                editsApplied++;
            } catch (IOException e) {
                failures.add(insertion.file() + ": " + e.getMessage());
            }
        }

        // Element removals (safe delete), grouped per file like edits.
        Map<File, List<ElementRemoval>> removalsByFile = new LinkedHashMap<>();
        for (ElementRemoval removal : plan.removals()) {
            removalsByFile.computeIfAbsent(removal.file(), f -> new ArrayList<>()).add(removal);
        }
        for (Map.Entry<File, List<ElementRemoval>> entry : removalsByFile.entrySet()) {
            File file = entry.getKey();
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                int removedHere = 0;
                for (ElementRemoval removal : entry.getValue()) {
                    // Line-aware: duplicates can share the exact attribute
                    // value (two keydefs with the same @keys) and the
                    // removal must hit the SHADOWED one, not the first.
                    String result = removeElementAt(content,
                            removal.attribute(), removal.value(), removal.line());
                    if (result == null) {
                        failures.add(file + ": could not remove <" + removal.element()
                                + " " + removal.attribute() + "=\"" + removal.value() + "\">");
                    } else {
                        content = result;
                        removedHere++;
                    }
                }
                if (removedHere > 0) {
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                    filesChanged++;
                    editsApplied += removedHere;
                }
            } catch (IOException e) {
                failures.add(file + ": " + e.getMessage());
            }
        }

        boolean deleted = false;
        if (plan.deleteTarget() != null) {
            try {
                Files.delete(plan.deleteTarget().toPath());
                deleted = true;
            } catch (IOException e) {
                failures.add("delete failed: " + e.getMessage());
            }
        }

        boolean moved = false;
        if (plan.moveFrom() != null && plan.moveTo() != null) {
            try {
                File parent = plan.moveTo().getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Files.move(plan.moveFrom().toPath(), plan.moveTo().toPath());
                moved = true;
            } catch (IOException e) {
                failures.add("move failed: " + e.getMessage());
            }
        }
        return new ApplyResult(editsApplied, filesChanged, moved, deleted, failures);
    }

    /**
     * Replaces every {@code attr="oldValue"} / {@code attr='oldValue'}
     * occurrence with the new value — and the new attribute name, when the
     * edit renames the attribute. Identical occurrences all need the same
     * rewrite (same source dir, same target), so replace-all is correct.
     * Returns null when no occurrence was found.
     */
    public static String replaceAttribute(String content, AttributeEdit edit) {
        boolean found = false;
        String result = content;
        for (char quote : new char[] {'"', '\''}) {
            String needle = edit.attribute() + "=" + quote + edit.oldValue() + quote;
            String replacement = edit.effectiveAttribute() + "=" + quote
                    + edit.newValue() + quote;
            if (result.contains(needle)) {
                result = result.replace(needle, replacement);
                found = true;
            }
        }
        return found ? result : null;
    }

    /**
     * Replaces one {@code attr="oldValue"} occurrence with
     * {@code newAttr="newValue"} — only on the edit's element (when known),
     * preferring the occurrence on the edit's line. Returns null when no
     * matching occurrence exists.
     */
    public static String replaceAttributeRename(String content, AttributeEdit edit) {
        for (char quote : new char[] {'"', '\''}) {
            String needle = edit.attribute() + "=" + quote + edit.oldValue() + quote;
            int best = -1;
            int from = 0;
            int idx;
            while ((idx = content.indexOf(needle, from)) >= 0) {
                boolean elementMatches = edit.element() == null
                        || edit.element().equals(owningTag(content, idx));
                if (elementMatches) {
                    if (lineOf(content, idx) == edit.line()) {
                        best = idx;
                        break;                     // exact line + element: done
                    }
                    if (best < 0) {
                        best = idx;                // first element match as fallback
                    }
                }
                from = idx + 1;
            }
            if (best >= 0) {
                return content.substring(0, best)
                        + edit.effectiveAttribute() + "=" + quote
                        + edit.newValue() + quote
                        + content.substring(best + needle.length());
            }
        }
        return null;
    }

    /** Local name of the tag the attribute at {@code idx} belongs to. */
    private static String owningTag(String content, int idx) {
        int lt = content.lastIndexOf('<', idx);
        if (lt < 0) {
            return null;
        }
        int p = lt + 1;
        while (p < content.length() && !Character.isWhitespace(content.charAt(p))
                && content.charAt(p) != '>' && content.charAt(p) != '/') {
            p++;
        }
        String name = content.substring(lt + 1, p);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    /** 1-based line of {@code idx} in {@code content}. */
    private static int lineOf(String content, int idx) {
        int line = 1;
        for (int i = 0; i < idx && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // -------------------------------------------------------------------------
    // Element removal (safe delete)
    // -------------------------------------------------------------------------

    /** Attributes whose presence in a subtree blocks auto-removal. */
    private static final java.util.regex.Pattern NESTED_REFERENCE = java.util.regex.Pattern
            .compile("[\\s](?:href|conref|conkeyref|keyref|keys|src)\\s*=");

    /**
     * Removes the first element whose start tag carries
     * {@code attribute="value"} (either quote style), including its whole
     * subtree and the line's leading indentation when the line becomes
     * empty. Refuses (returns null) when the subtree contains other
     * reference-bearing attributes — removing those wouldn't be the
     * caller's intent — or when the occurrence isn't found.
     */
    public static String removeElement(String content, String attribute, String value) {
        return removeElementAt(content, attribute, value, -1);
    }

    /**
     * Like {@link #removeElement}, preferring the occurrence on
     * {@code line} — duplicate elements can share the exact attribute
     * value (two keydefs with the same {@code @keys}), and removal by
     * first occurrence would hit the wrong one.
     */
    public static String removeElementAt(String content, String attribute,
                                         String value, int line) {
        int[] span = elementSpanAt(content, attribute, value, line);
        if (span == null) {
            return null;
        }
        int start = span[0];
        int startTagEnd = span[1];
        int end = span[2];
        if (startTagEnd >= 0) {                    // paired: check the subtree
            int close = content.lastIndexOf("</", end - 1);
            String body = content.substring(startTagEnd + 1, close);
            if (NESTED_REFERENCE.matcher(body).find()) {
                return null;                       // subtree carries its own references
            }
        }

        // Tidy: when the element sat on its own line, remove the line.
        int lineStart = start;
        while (lineStart > 0 && (content.charAt(lineStart - 1) == ' '
                || content.charAt(lineStart - 1) == '\t')) {
            lineStart--;
        }
        boolean ownLine = lineStart == 0 || content.charAt(lineStart - 1) == '\n';
        if (ownLine) {
            int lineEnd = end;
            while (lineEnd < content.length() && (content.charAt(lineEnd) == ' '
                    || content.charAt(lineEnd) == '\t')) {
                lineEnd++;
            }
            if (lineEnd < content.length() && content.charAt(lineEnd) == '\r') {
                lineEnd++;
            }
            if (lineEnd < content.length() && content.charAt(lineEnd) == '\n') {
                return content.substring(0, lineStart) + content.substring(lineEnd + 1);
            }
        }
        return content.substring(0, start) + content.substring(end);
    }

    /**
     * Locates the first element whose start tag carries
     * {@code attribute="value"}. Returns {@code {start, startTagEnd, end}}
     * where {@code start} is the {@code <}, {@code end} is just past the
     * element (self-closing or matching close tag), and {@code startTagEnd}
     * is the start tag's {@code >} for paired elements, -1 for
     * self-closing. Null when not found or malformed.
     */
    static int[] elementSpan(String content, String attribute, String value) {
        return elementSpanAt(content, attribute, value, -1);
    }

    /**
     * {@link #elementSpan} preferring the occurrence on {@code line}
     * (1-based; -1 = first occurrence).
     */
    static int[] elementSpanAt(String content, String attribute, String value,
                               int line) {
        int attrIdx = indexOfAttributeAt(content, attribute, value, line);
        if (attrIdx < 0) {
            return null;
        }
        int start = content.lastIndexOf('<', attrIdx);
        if (start < 0 || start + 1 >= content.length()
                || content.charAt(start + 1) == '/' || content.charAt(start + 1) == '!') {
            return null;
        }
        int nameEnd = start + 1;
        while (nameEnd < content.length() && !Character.isWhitespace(content.charAt(nameEnd))
                && content.charAt(nameEnd) != '>' && content.charAt(nameEnd) != '/') {
            nameEnd++;
        }
        String tag = content.substring(start + 1, nameEnd);
        int startTagEnd = findTagEnd(content, start);
        if (startTagEnd < 0) {
            return null;
        }
        if (content.charAt(startTagEnd - 1) == '/') {
            return new int[] {start, -1, startTagEnd + 1};
        }
        int close = findMatchingClose(content, tag, startTagEnd + 1);
        if (close < 0) {
            return null;
        }
        int closeGt = content.indexOf('>', close);
        if (closeGt < 0) {
            return null;
        }
        return new int[] {start, startTagEnd, closeGt + 1};
    }

    private static int indexOfAttribute(String content, String attribute, String value) {
        return indexOfAttributeAt(content, attribute, value, -1);
    }

    /**
     * Index of an {@code attribute="value"} occurrence, preferring the one
     * on {@code line}; the first occurrence when no line matches.
     */
    private static int indexOfAttributeAt(String content, String attribute,
                                          String value, int line) {
        for (char quote : new char[] {'"', '\''}) {
            String needle = attribute + "=" + quote + value + quote;
            int best = -1;
            int from = 0;
            int idx;
            while ((idx = content.indexOf(needle, from)) >= 0) {
                if (line > 0 && lineOf(content, idx) == line) {
                    return idx;
                }
                if (best < 0) {
                    best = idx;
                }
                if (line <= 0) {
                    break;                          // first occurrence is enough
                }
                from = idx + 1;
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    /** Index of the {@code >} closing the tag that starts at {@code start}. */
    private static int findTagEnd(String content, int start) {
        char inQuote = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuote != 0) {
                if (c == inQuote) {
                    inQuote = 0;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Index of the matching {@code </tag} for an element whose start tag
     * ends just before {@code from}, accounting for nested same-tag elements.
     */
    private static int findMatchingClose(String content, String tag, int from) {
        int depth = 1;
        int pos = from;
        while (depth > 0) {
            int open = indexOfStartTag(content, tag, pos);
            int close = content.indexOf("</" + tag, pos);
            if (close < 0) {
                return -1;
            }
            if (open >= 0 && open < close) {
                int openEnd = findTagEnd(content, open);
                if (openEnd < 0) {
                    return -1;
                }
                if (content.charAt(openEnd - 1) != '/') {
                    depth++;                       // self-closing doesn't nest
                }
                pos = openEnd + 1;
            } else {
                depth--;
                if (depth == 0) {
                    return close;
                }
                pos = close + 2 + tag.length();
            }
        }
        return -1;
    }

    private static int indexOfStartTag(String content, String tag, int from) {
        int pos = from;
        while (true) {
            int idx = content.indexOf("<" + tag, pos);
            if (idx < 0) {
                return -1;
            }
            int after = idx + 1 + tag.length();
            if (after >= content.length()) {
                return -1;
            }
            char c = content.charAt(after);
            if (Character.isWhitespace(c) || c == '>' || c == '/') {
                return idx;
            }
            pos = idx + 1;
        }
    }
}
