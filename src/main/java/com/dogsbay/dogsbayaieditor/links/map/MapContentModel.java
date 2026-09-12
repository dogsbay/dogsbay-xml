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

package com.dogsbay.dogsbayaieditor.links.map;

import java.util.Set;

/**
 * A curated, unit-testable rule set for which map navigation elements may nest
 * under which — DITA's map content model is real but loose, and a full
 * grammar-driven check is heavy. A clear seam to later defer to the DITA grammar
 * plugin: replace {@link #canNest} only.
 *
 * <p>P1 rules: containers ({@code map}/{@code bookmap} root, {@code topicref},
 * {@code topichead}, {@code topicgroup}, and the bookmap divisions) may receive
 * nestable children ({@code topicref}/{@code topichead}/{@code topicgroup}/
 * {@code mapref}/{@code keydef}/…); references that point at another file
 * ({@code mapref}/{@code keydef}/{@code glossref}/{@code anchorref}/{@code navref})
 * are leaves you do not nest into.
 */
public final class MapContentModel {

    private static final Set<String> CONTAINERS = Set.of(
            "map", "bookmap", "topicref", "topichead", "topicgroup",
            "chapter", "appendix", "appendices", "part", "preface", "notices",
            "frontmatter", "backmatter");

    /** Topicref-family children — may nest under any container. */
    private static final Set<String> NESTABLE = Set.of(
            "topicref", "topichead", "topicgroup", "mapref", "keydef",
            "glossref", "anchorref", "navref");

    /** Bookmap structural divisions — insertable, but only at their proper place in a
     *  bookmap (DITA 1.3 book structure), not under arbitrary containers. */
    private static final Set<String> BOOK_DIVISIONS = Set.of(
            "chapter", "appendix", "appendices", "part", "preface", "notices",
            "frontmatter", "backmatter");

    private MapContentModel() {}

    /** True when a {@code childType} element may be nested directly under a
     *  {@code parentType} element. */
    public static boolean canNest(String parentType, String childType) {
        if (parentType == null || childType == null || !CONTAINERS.contains(parentType)) {
            return false;
        }
        if (BOOK_DIVISIONS.contains(childType)) {
            return canNestBookDivision(parentType, childType);
        }
        return NESTABLE.contains(childType);   // topicref-family under any container
    }

    /** Place a bookmap division at its proper spot (book structure, kept loose like P1). */
    private static boolean canNestBookDivision(String parent, String child) {
        return switch (child) {
            case "frontmatter", "backmatter", "part", "appendices", "chapter", "appendix" ->
                "bookmap".equals(parent)
                    // chapters also nest in a part; appendices group <appendix>
                    || ("chapter".equals(child) && "part".equals(parent))
                    || ("appendix".equals(child) && "appendices".equals(parent));
            case "preface", "notices" -> "frontmatter".equals(parent) || "bookmap".equals(parent);
            default -> false;
        };
    }

    /** True when {@code type} may contain navigation children. */
    public static boolean isContainer(String type) {
        return CONTAINERS.contains(type);
    }

    /** True when {@code type} is an element this model knows how to insert/edit. */
    public static boolean isNestable(String type) {
        return NESTABLE.contains(type) || BOOK_DIVISIONS.contains(type);
    }
}
