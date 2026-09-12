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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One {@code <subjectdef>} in a DITA subjectScheme map: a controlled subject with
 * a key (DITA subjectdefs may declare multiple whitespace-separated keys), an
 * optional navigation title, and nested narrower subjects (subjects are
 * hierarchical).
 *
 * @param keys     the raw {@code keys} attribute (may be whitespace-separated); may be blank
 * @param navTitle the {@code navtitle} attribute, or null
 * @param children narrower subjects nested under this one
 */
public record SubjectDef(String keys, String navTitle, List<SubjectDef> children) {

    public SubjectDef {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** The individual key tokens declared on this subject (may be empty). */
    public List<String> keyTokens() {
        if (keys == null || keys.isBlank()) {
            return List.of();
        }
        return List.of(keys.trim().split("\\s+"));
    }

    /**
     * This subject's key(s) plus every descendant's — the legal value set when an
     * {@code <enumerationdef>} binds an attribute to this subject (DITA subjects
     * are hierarchical, so any key in the subtree is a legal value).
     */
    public Set<String> flattenKeys() {
        Set<String> out = new LinkedHashSet<>();
        collect(this, out);
        return out;
    }

    private static void collect(SubjectDef d, Set<String> out) {
        out.addAll(d.keyTokens());
        for (SubjectDef c : d.children()) {
            collect(c, out);
        }
    }
}
