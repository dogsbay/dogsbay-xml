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

package com.dogsbay.dogsbayaieditor.ditaproject;

import java.nio.file.Path;

/**
 * One DITA-OT publication parameter, as declared in a project file
 * ({@code <param name=... value|href|path=.../>}). The three attribute kinds
 * resolve differently (per the DITA-OT project spec), all relative to the
 * project file:
 *
 * <ul>
 *   <li>{@link Kind#VALUE} — a literal string / relative value, passed as-is;</li>
 *   <li>{@link Kind#HREF} — a resource that resolves to an absolute URI;</li>
 *   <li>{@link Kind#PATH} — a parameter requiring an absolute system path
 *       (e.g. {@code args.cssroot}), expanded from a project-relative path.</li>
 * </ul>
 *
 * @param name  the parameter name (e.g. {@code "nav-toc"}); never null
 * @param value the raw declared value (literal, or a project-relative href/path)
 * @param kind  which attribute carried the value
 */
public record Param(String name, String value, Kind kind) {

    public enum Kind { VALUE, HREF, PATH }

    public Param {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("param name is required");
        }
        if (kind == null) {
            kind = Kind.VALUE;
        }
    }

    /** A literal {@code value} param. */
    public static Param value(String name, String value) {
        return new Param(name, value, Kind.VALUE);
    }

    /**
     * The value resolved against the project-file directory: {@code VALUE} is
     * returned unchanged; {@code HREF}/{@code PATH} are resolved relative to
     * {@code base} and returned as an absolute path string. A null/blank value
     * is returned as-is.
     */
    public String resolve(Path base) {
        if (value == null || value.isBlank() || kind == Kind.VALUE || base == null) {
            return value;
        }
        return base.resolve(value.trim()).normalize().toAbsolutePath().toString();
    }
}
