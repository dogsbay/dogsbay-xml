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
 * One outbound reference found in an XML/DITA file.
 *
 * <p>Two kinds:
 * <ul>
 *   <li><b>Path references</b> ({@code href}, {@code conref}, {@code src},
 *       xi:include): {@link #targetPath()} is the canonical path of the
 *       referenced file; {@link #fragment()} holds anything after {@code #}.</li>
 *   <li><b>Key references</b> ({@code keyref}, {@code conkeyref}):
 *       {@link #targetPath()} is null; {@link #keyName()} is the key, and for
 *       {@code conkeyref="key/elemId"} {@link #fragment()} is the element id.</li>
 * </ul>
 *
 * @param source     the file containing the reference
 * @param line       1-based line of the referencing element's start tag
 * @param element    local name of the referencing element
 * @param attribute  the attribute carrying the reference (href, conref, ...)
 * @param rawValue   the attribute value exactly as written
 * @param targetPath canonical path of the referenced file, or null for key refs
 * @param fragment   fragment / element-id part, or null
 * @param scope      the dotted {@code @keyscope} path this reference is authored
 *                   within <em>in its own file</em> ({@code ""} = the file's root
 *                   scope). Used to resolve a {@code keyref}/{@code conkeyref} in
 *                   the right scope. Note: scope inherited from a map's placement
 *                   of the file (cross-file) is not captured here — see
 *                   {@link KeySpace#resolveLenient(String, String)}.
 */
public record Reference(File source, int line, String element, String attribute,
                        String rawValue, String targetPath, String fragment, String scope) {

    public Reference {
        scope = scope == null ? "" : scope;
    }

    /** True for keyref/conkeyref references (resolved via a key space). */
    public boolean isKeyReference() {
        return "keyref".equals(attribute) || "conkeyref".equals(attribute);
    }

    /** The key name for key references (first path segment of the value). */
    public String keyName() {
        if (!isKeyReference()) {
            return null;
        }
        int slash = rawValue.indexOf('/');
        return slash >= 0 ? rawValue.substring(0, slash) : rawValue;
    }
}
