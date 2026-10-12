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

package com.dogsbay.xml.author.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A run of text with uniform inline styling — the unit of the delta model used
 * by text blocks. Immutable; attribute keys are defined in {@link InlineStyle}.
 */
public record InlineRun(String text, Map<String, String> attrs) {

    public InlineRun {
        if (text == null) {
            throw new IllegalArgumentException("run text must not be null");
        }
        attrs = attrs == null || attrs.isEmpty()
                ? Map.of()
                : Map.copyOf(attrs);
    }

    /** Plain, unstyled run. */
    public static InlineRun of(String text) {
        return new InlineRun(text, Map.of());
    }

    /** Run with a single style attribute. */
    public static InlineRun styled(String text, String key, String value) {
        return new InlineRun(text, Map.of(key, value));
    }

    public boolean isPlain() {
        return attrs.isEmpty();
    }

    /** Copy of this run with one attribute added or (value == null) removed. */
    public InlineRun with(String key, String value) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>(attrs);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return new InlineRun(text, copy);
    }
}
