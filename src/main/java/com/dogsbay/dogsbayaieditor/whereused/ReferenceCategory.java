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

package com.dogsbay.dogsbayaieditor.whereused;

import java.util.Set;

import com.dogsbay.dogsbayaieditor.links.Reference;

/**
 * Semantic classification of a reference — what it <em>means</em> for the
 * target, which is what a writer needs to judge rename/move/edit impact:
 *
 * <ul>
 *   <li>{@link #IN_MAPS} — navigation/inclusion from a map (rename breaks
 *       the map)</li>
 *   <li>{@link #CONTENT_REUSE} — transcluded content: conref, conkeyref,
 *       text-pulling keyrefs, XInclude (edits propagate; renames break
 *       the reuse)</li>
 *   <li>{@link #LINKS} — cross-references the reader follows (xref/link,
 *       by href or key)</li>
 *   <li>{@link #IMAGES} — image/media references</li>
 *   <li>{@link #OTHER} — anything else carrying a navigation attribute</li>
 * </ul>
 */
public enum ReferenceCategory {

    IN_MAPS("In maps"),
    CONTENT_REUSE("Content reuse"),
    LINKS("Links"),
    IMAGES("Images"),
    OTHER("Other references");

    private static final Set<String> MAP_ELEMENTS = Set.of(
            "topicref", "mapref", "chapter", "appendix", "part",
            "topichead", "topicgroup", "keydef", "glossref", "anchorref");

    private static final Set<String> LINK_ELEMENTS = Set.of("xref", "link");

    private final String displayName;

    ReferenceCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Classifies a reference by element + attribute semantics. */
    public static ReferenceCategory classify(Reference ref) {
        String element = ref.element() == null ? "" : ref.element();
        String attribute = ref.attribute() == null ? "" : ref.attribute();

        if ("image".equals(element) || "src".equals(attribute)) {
            return IMAGES;
        }
        if (LINK_ELEMENTS.contains(element)) {
            return LINKS;                       // href or keyref — still a link
        }
        if ("conref".equals(attribute) || "conkeyref".equals(attribute)) {
            return CONTENT_REUSE;
        }
        if ("include".equals(element)) {
            return CONTENT_REUSE;               // XInclude transcludes
        }
        if (MAP_ELEMENTS.contains(element)) {
            return IN_MAPS;
        }
        if ("keyref".equals(attribute)) {
            // keyref on ph/keyword/term/etc. pulls the key's text — reuse.
            return CONTENT_REUSE;
        }
        return OTHER;
    }
}
