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

/**
 * One key definition found in a DITA map: any element carrying a
 * {@code @keys} attribute (keydef, keyed topicref, ...).
 *
 * @param source      the map file the definition appears in
 * @param line        1-based line of the defining element's start tag
 * @param keys        the raw (possibly space-separated) @keys value
 * @param href        the @href value, or null for definition-only keys
 * @param keywordText text of topicmeta/keywords/keyword, or null
 * @param linkText    text of topicmeta/linktext, or null
 * @param scope       the dotted {@code @keyscope} path this definition sits in
 *                    within its own file ({@code ""} = the file's root scope);
 *                    {@link KeySpace} combines it with the mapref scope chain
 * @param subjectScheme true when the definition is a {@code subjectdef} in a
 *                    {@code subjectScheme} map — a controlled-value key, not a
 *                    content key (so it is exempt from the unused-key audit)
 */
public record KeyDefinition(File source, int line, String keys, String href,
                            String keywordText, String linkText, String scope,
                            boolean subjectScheme) {

    public KeyDefinition {
        scope = scope == null ? "" : scope;
    }

    /** Back-compat constructor for callers that don't track the subjectScheme origin. */
    public KeyDefinition(File source, int line, String keys, String href,
                         String keywordText, String linkText, String scope) {
        this(source, line, keys, href, keywordText, linkText, scope, false);
    }

    /** Back-compat constructor for callers that don't track scope (root scope). */
    public KeyDefinition(File source, int line, String keys, String href,
                         String keywordText, String linkText) {
        this(source, line, keys, href, keywordText, linkText, "", false);
    }
}
