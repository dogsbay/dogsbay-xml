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

package com.dogsbay.dogsbayaieditor.links.metadata;

import java.util.List;
import java.util.Map;

/**
 * The metadata a single file carries: its root element type, the metadata container
 * ({@code prolog} for topics, {@code topicmeta}/{@code bookmeta} for maps, or
 * {@code none}), the values found per {@link MetadataField}, and source lines.
 *
 * @param rootType   the root element's local name (e.g. {@code concept}, {@code map})
 * @param container  {@code "prolog"}, {@code "topicmeta"}, {@code "bookmeta"}, or {@code "none"}
 * @param anchorLine the line to point a "missing field" finding at (the container's
 *                   line, or the root's), or -1 if unknown
 * @param values     field → values in document order (multi-valued fields keep order)
 * @param fieldLines field → the 1-based line of its first occurrence (present fields only)
 */
public record MetadataSnapshot(String rootType, String container, int anchorLine,
        Map<MetadataField, List<String>> values, Map<MetadataField, Integer> fieldLines) {

    public MetadataSnapshot {
        rootType = rootType == null ? "" : rootType;
        values = values == null ? Map.of() : Map.copyOf(values);
        fieldLines = fieldLines == null ? Map.of() : Map.copyOf(fieldLines);
    }

    /** The empty snapshot — no metadata container present. */
    public static MetadataSnapshot empty() {
        return new MetadataSnapshot("", "none", -1, Map.of(), Map.of());
    }

    /** True when {@code field} has at least one value. */
    public boolean has(MetadataField field) {
        List<String> v = values.get(field);
        return v != null && !v.isEmpty();
    }

    /** The values for {@code field} (empty if absent). */
    public List<String> get(MetadataField field) {
        return values.getOrDefault(field, List.of());
    }

    /** The line to report a finding about {@code field} at: the field's own line if
     *  present, else the container/root anchor (so "missing" opens the prolog). */
    public int lineFor(MetadataField field) {
        return fieldLines.getOrDefault(field, anchorLine);
    }

    /** True when no field carries a value. */
    public boolean isEmpty() {
        return values.values().stream().allMatch(List::isEmpty);
    }
}
