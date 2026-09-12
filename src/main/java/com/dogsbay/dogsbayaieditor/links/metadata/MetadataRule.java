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

/**
 * One required-metadata rule: "topics of type {@code topicType} must/should/must-not
 * carry {@code field}", with optional value constraints. A {@code null} topic type
 * applies to every topic.
 *
 * @param topicType     the root element name the rule applies to (e.g. {@code task}),
 *                      or null/blank for any topic
 * @param field         the governed metadata field
 * @param presence      required (error if missing), recommended (warning), or
 *                      forbidden (error if present)
 * @param allowedValues if non-empty, the value must be one of these
 * @param pattern       if non-null, the value must match this regex
 */
public record MetadataRule(String topicType, MetadataField field, Presence presence,
        List<String> allowedValues, String pattern) {

    /** Presence requirement for a field. */
    public enum Presence {
        REQUIRED, RECOMMENDED, FORBIDDEN;

        public static Presence parse(String s) {
            if (s == null) {
                return REQUIRED;
            }
            return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "recommended" -> RECOMMENDED;
                case "forbidden" -> FORBIDDEN;
                default -> REQUIRED;
            };
        }
    }

    public MetadataRule {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    /** True when this rule governs a topic whose root element is {@code topicType}. */
    public boolean appliesTo(String topicType) {
        return this.topicType == null || this.topicType.isBlank()
                || this.topicType.equals(topicType);
    }
}
