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

package com.dogsbay.dogsbayaieditor.links.scheme;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resolved, queryable controlled-value vocabulary for one map closure — which
 * profiling attributes are governed and the legal values for each, from the
 * subjectScheme map(s) referenced by a root map. GUI-free dita-core, shaped like
 * {@code KeySpace}: {@link #fromRootMap} discovers + parses + merges; the rest is
 * read-only queries.
 *
 * <p>This is the reusable <strong>controlled-value substrate</strong>: it answers
 * "is this value allowed for this attribute?" generically, so both conditional
 * filtering (profiling attributes) and metadata classification (a later consumer)
 * can validate/complete against it.
 */
public final class SubjectScheme {

    private final Map<String, EnumerationBinding> byAttribute; // last-def-wins per attribute
    private final Set<String> schemeMaps;

    private SubjectScheme(Map<String, EnumerationBinding> byAttribute, Set<String> schemeMaps) {
        this.byAttribute = byAttribute;
        this.schemeMaps = schemeMaps;
    }

    /** Build from a list of bindings (last binding per attribute wins) + the scheme files. */
    static SubjectScheme of(List<EnumerationBinding> bindings, Set<String> schemeMaps) {
        Map<String, EnumerationBinding> m = new LinkedHashMap<>();
        for (EnumerationBinding b : bindings) {
            m.put(b.attribute(), b);
        }
        return new SubjectScheme(Map.copyOf(m), Set.copyOf(schemeMaps));
    }

    /** The empty scheme — governs nothing; every value is "ungoverned". */
    public static SubjectScheme empty() {
        return new SubjectScheme(Map.of(), Set.of());
    }

    /** Discover, parse, and merge the subjectScheme map(s) in {@code rootMap}'s closure. */
    public static SubjectScheme fromRootMap(File rootMap) {
        return SubjectSchemeParser.fromRootMap(rootMap);
    }

    /** Discover, parse, and merge every subjectScheme map under a project root. */
    public static SubjectScheme fromProjectRoot(File projectRoot) {
        return SubjectSchemeParser.fromProjectRoot(projectRoot);
    }

    /** True when an {@code <enumerationdef>} binds a controlled value set to {@code attribute}. */
    public boolean governs(String attribute) {
        return byAttribute.containsKey(attribute);
    }

    /** The legal values for {@code attribute} (flattened subject subtree), or empty if ungoverned. */
    public Set<String> allowedValues(String attribute) {
        EnumerationBinding b = byAttribute.get(attribute);
        return b == null ? Set.of() : b.allowedValues();
    }

    /** True if {@code value} is legal for {@code attribute} — or the attribute is ungoverned. */
    public boolean isAllowed(String attribute, String value) {
        EnumerationBinding b = byAttribute.get(attribute);
        return b == null || b.allowedValues().contains(value);
    }

    /** All enumeration bindings (one per governed attribute). */
    public List<EnumerationBinding> bindings() {
        return List.copyOf(byAttribute.values());
    }

    /** The subjectScheme files that contributed to this scheme. */
    public Set<String> schemeMaps() {
        return schemeMaps;
    }

    /** True when no attribute is governed (no scheme found). */
    public boolean isEmpty() {
        return byAttribute.isEmpty();
    }
}
