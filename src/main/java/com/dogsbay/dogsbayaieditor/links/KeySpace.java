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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The effective key space of a DITA root map: key name → definition.
 *
 * <p>Built by walking the root map and its submaps breadth-first (root map
 * first), collecting every element with {@code @keys}. The <b>first</b>
 * definition of a key wins — an approximation of DITA key precedence that is
 * correct for the common case (root-map keydefs overriding submap keydefs).
 *
 * <p><b>Key scopes</b> ({@code @keyscope}, DITA 1.3) are modeled: keys are
 * stored under their fully-qualified name (the dotted scope path + the key
 * name), and {@link #resolve(String, String)} performs the outward-walking
 * lookup. {@link #resolveLenient(String, String)} additionally falls back to a
 * key's unique scoped definition for callers (closure crawl, conref audit,
 * where-used, preview) that know a key name but not its exact authoring scope —
 * scope inherited from a map's placement of a topic (cross-file) is not modeled
 * in-process, so a bare {@code keyref} in topic content carries the root scope.
 *
 * <p>Cycle-safe; uses {@link LinkExtractor}'s hardened parsing throughout
 * (no DTD fetching, works offline).
 */
public final class KeySpace {

    private final Map<String, KeyDefinition> keys;
    private final File rootMap;
    /** Bare key name → its definition when that name is defined in exactly ONE
     *  scope (root included); names defined in several scopes are absent. Computed
     *  once at construction so the lenient fallback is O(1), not an entrySet scan. */
    private final Map<String, KeyDefinition> uniqueByBareName;

    private KeySpace(File rootMap, Map<String, KeyDefinition> keys) {
        this.rootMap = rootMap;
        this.keys = keys;
        this.uniqueByBareName = computeUniqueByBareName(keys);
    }

    private static Map<String, KeyDefinition> computeUniqueByBareName(
            Map<String, KeyDefinition> keys) {
        Map<String, KeyDefinition> unique = new java.util.HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, KeyDefinition> e : keys.entrySet()) {
            String bare = bareNameOf(e.getKey());
            if (ambiguous.contains(bare)) {
                continue;
            }
            KeyDefinition prev = unique.putIfAbsent(bare, e.getValue());
            if (prev != null && prev != e.getValue()) {
                unique.remove(bare);              // defined in >1 scope → not unique
                ambiguous.add(bare);
            }
        }
        return unique;
    }

    /** An empty key space (no context map). */
    public static KeySpace empty() {
        return new KeySpace(null, Collections.emptyMap());
    }

    /**
     * Builds the key space for a root map. Missing/unreadable maps yield an
     * empty space rather than throwing.
     */
    public static KeySpace fromRootMap(File rootMap) {
        return fromRootMap(rootMap, null);
    }

    /**
     * Builds the key space with conditional filtering: subtrees the
     * exclusion rejects (DITAVAL-excluded keydefs, profiled keyword
     * variants) don't contribute — filtering happens before key
     * resolution, as in DITA-OT.
     */
    public static KeySpace fromRootMap(File rootMap, ElementExclusion exclusion) {
        // Keys are stored under their fully-qualified name: the @keyscope path joined
        // with "." + the key name (bare for the root scope) — DITA 1.3 key scopes.
        Map<String, KeyDefinition> collected = new LinkedHashMap<>();
        if (rootMap == null || !rootMap.isFile()) {
            return new KeySpace(rootMap, collected);
        }

        Deque<QueueEntry> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new QueueEntry(rootMap, ""));

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.removeFirst();
            File map = entry.map();
            String canon = LinkExtractor.canonical(map);
            // Dedup per (map, scope-prefix): the same submap reused under two scopes
            // contributes to both, while a cycle within one scope is still cut. The
            // '\n' separator can't occur in a canonical path or a scope name, so
            // distinct (map, scope) pairs never collide into one key.
            if (!visited.add(canon + '\n' + entry.prefix()) || !map.isFile()) {
                continue;
            }

            LinkExtractor.ExtractionResult result = LinkExtractor.extract(map, exclusion);

            for (KeyDefinition def : result.keyDefinitions()) {
                String effScope = combine(entry.prefix(), def.scope());
                for (String name : def.keys().split("\\s+")) {
                    if (!name.isEmpty()) {
                        collected.putIfAbsent(combine(effScope, name), def); // first wins
                    }
                }
            }

            // Enqueue submaps, accumulating the scope prefix down the mapref chain.
            for (LinkExtractor.Submap sub : result.submaps()) {
                if (sub.targetPath() != null) {
                    queue.addLast(new QueueEntry(new File(sub.targetPath()),
                            combine(entry.prefix(), sub.scope())));
                }
            }
        }
        return new KeySpace(rootMap, collected);
    }

    /** Join two dotted scope/name segments, skipping empties: ("a","b") → "a.b". */
    private static String combine(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b == null ? "" : b;
        }
        return b == null || b.isEmpty() ? a : a + "." + b;
    }

    private record QueueEntry(File map, String prefix) {}

    /**
     * Canonical paths of every map reachable from {@code rootMap} via
     * mapref/.ditamap references, the root itself included. A keydef in a
     * map outside this closure contributes nothing to the root map's key
     * space.
     */
    public static Set<String> mapsInClosure(File rootMap) {
        // LinkedHashSet: iteration order is the BFS (first-definition-wins)
        // order, which merge-keydefs relies on.
        Set<String> visited = new java.util.LinkedHashSet<>();
        if (rootMap == null || !rootMap.isFile()) {
            return visited;
        }
        Deque<File> queue = new ArrayDeque<>();
        queue.add(rootMap);
        while (!queue.isEmpty()) {
            File map = queue.removeFirst();
            String canon = LinkExtractor.canonical(map);
            if (!visited.add(canon) || !map.isFile()) {
                continue;
            }
            for (Reference ref : LinkExtractor.extract(map).references()) {
                if (ref.targetPath() == null) {
                    continue;
                }
                if ("mapref".equals(ref.element())
                        || ref.targetPath().toLowerCase().endsWith(".ditamap")) {
                    queue.addLast(new File(ref.targetPath()));
                }
            }
        }
        return visited;
    }

    /** The definition for a (fully-qualified, or root-scope bare) key name, or null. */
    public KeyDefinition resolve(String keyName) {
        return keyName == null ? null : keys.get(keyName);
    }

    /**
     * Resolve {@code key} as referenced from within scope {@code fromScope} (a dotted
     * path, or {@code null}/{@code ""} for the root). Tries the innermost scope first
     * and walks outward to the root — so a key defined in an inner scope shadows an
     * outer same-name key, and {@code scope.key} qualified references resolve from the
     * root. Returns the first match, or null.
     */
    public KeyDefinition resolve(String key, String fromScope) {
        String qualified = resolveQualifiedName(key, fromScope);
        return qualified == null ? null : keys.get(qualified);
    }

    /**
     * The fully-qualified name that {@code key} resolves to from {@code fromScope}
     * (the outward walk of {@link #resolve(String, String)}), or null. The scope it
     * was actually found in is {@link #scopeOf} of the result — which may be an
     * outer scope, not {@code fromScope}.
     */
    public String resolveQualifiedName(String key, String fromScope) {
        if (key == null) {
            return null;
        }
        String scope = fromScope == null ? "" : fromScope;
        while (true) {
            String qualified = combine(scope, key);
            if (keys.containsKey(qualified)) {
                return qualified;
            }
            if (scope.isEmpty()) {
                return null;
            }
            int dot = scope.lastIndexOf('.');
            scope = dot < 0 ? "" : scope.substring(0, dot);
        }
    }

    /** The scope path of a qualified key name ({@code "a.b"} for {@code "a.b.k"},
     *  {@code ""} for a root-scope key). */
    public static String scopeOf(String qualifiedName) {
        if (qualifiedName == null) {
            return "";
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    /** The bare key name of a qualified key name ({@code "k"} for {@code "a.b.k"}). */
    private static String bareNameOf(String qualifiedName) {
        if (qualifiedName == null) {
            return "";
        }
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    /**
     * Best-effort resolution for callers that have a key name but not its exact
     * authoring scope. First tries the spec-correct {@link #resolve(String, String)}
     * (outward walk from {@code fromScope}); if that misses <em>and</em> the call is
     * from the root context, and {@code key} is a <em>bare</em> name defined in
     * exactly one scope anywhere in the space, returns that unique definition.
     * Ambiguous bare names (defined in several scopes), already-qualified names
     * (containing a {@code .}), and calls from a non-root {@code fromScope} get no
     * fallback — only the exact result, or null.
     *
     * <p>This bridges the cross-file gap: a topic placed under a {@code @keyscope}
     * in the map authors its {@code keyref}s bare, but in-process we see the topic's
     * own (root) scope only. When the key is unique to one scope, the bare reference
     * can only mean that one definition, so resolving to it is safe for inclusion
     * (closure crawl), verification (conref audit), and rendering (preview). The
     * fallback is gated on a root {@code fromScope}: a reference that genuinely sits
     * in an inner scope already had its outward walk, so guessing across an isolated
     * sibling scope would be wrong, not merely permissive.
     */
    public KeyDefinition resolveLenient(String key, String fromScope) {
        KeyDefinition exact = resolve(key, fromScope);
        if (exact != null) {
            return exact;
        }
        if (key == null || key.indexOf('.') >= 0) {
            return null;                          // already qualified — no guessing
        }
        if (fromScope != null && !fromScope.isEmpty()) {
            return null;                          // a real inner scope: don't cross siblings
        }
        return uniqueByBareName.get(key);         // unique scope-only key, or null
    }

    /** Scope-aware, best-effort variant of {@link #resolveHrefFile(String)}. */
    public File resolveHrefFileLenient(String key, String fromScope) {
        return hrefFileOf(resolveLenient(key, fromScope));
    }

    /**
     * The file a key's href points at, resolved against the defining map's
     * directory; null when the key has no href or it is an external URL.
     */
    public File resolveHrefFile(String keyName) {
        return hrefFileOf(resolve(keyName));
    }

    /** Scope-aware variant of {@link #resolveHrefFile(String)}. */
    public File resolveHrefFile(String key, String fromScope) {
        return hrefFileOf(resolve(key, fromScope));
    }

    private static File hrefFileOf(KeyDefinition def) {
        if (def == null || def.href() == null || def.href().isEmpty()) {
            return null;
        }
        String href = def.href();
        int hash = href.indexOf('#');
        if (hash >= 0) {
            href = href.substring(0, hash);
        }
        if (href.isEmpty() || href.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return null;
        }
        File f = new File(href);
        if (!f.isAbsolute() && def.source() != null) {
            f = new File(def.source().getParentFile(), href);
        }
        return f;
    }

    /** All key name → definition entries (insertion order). */
    public Map<String, KeyDefinition> entries() {
        return Collections.unmodifiableMap(keys);
    }

    /**
     * {@link #entries()} plus a <em>bare alias</em> for every key that is unique to a
     * single scope (so a bare {@code keyref} authored cross-file under a {@code
     * @keyscope} still finds it). A bare name that is already a root-scope key is left
     * as-is. This is the same unique-scope rule as {@link #resolveLenient}, exposed for
     * consumers (e.g. preview) that materialise the whole key space by name.
     */
    public Map<String, KeyDefinition> entriesWithBareAliases() {
        Map<String, KeyDefinition> out = new LinkedHashMap<>(keys);
        uniqueByBareName.forEach(out::putIfAbsent);   // adds scope-only unique names
        return Collections.unmodifiableMap(out);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public File getRootMap() {
        return rootMap;
    }
}
