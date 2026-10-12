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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * One key from a root map's key space.
 *
 * @param name         the key name
 * @param definedIn    absolute path of the defining map
 * @param line         1-based line of the defining element
 * @param href         the keydef's href as written, or null
 * @param resolvedPath absolute path the href resolves to (relative to the
 *                     defining map), or null when absent/missing
 * @param text         the key's keyword text (or linktext), or null
 * @param scope        the key's {@code @keyscope} path ({@code ""} = root scope);
 *                     {@code name} is the fully-qualified (scope-prefixed) key
 */
public record KeyInfo(
    String name,
    String definedIn,
    int line,
    String href,
    String resolvedPath,
    String text,
    String scope
) {
    public KeyInfo {
        scope = scope == null ? "" : scope;
    }

    /** Back-compat constructor (root scope). */
    public KeyInfo(String name, String definedIn, int line, String href,
                   String resolvedPath, String text) {
        this(name, definedIn, line, href, resolvedPath, text, "");
    }
}
