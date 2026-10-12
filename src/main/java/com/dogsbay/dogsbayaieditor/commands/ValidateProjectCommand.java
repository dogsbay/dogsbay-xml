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

package com.dogsbay.dogsbayaieditor.commands;

import java.nio.file.Path;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Validate every file in a scope, on disk, without opening any document —
 * the project/map-wide counterpart to {@link ValidateCommand}.
 *
 * <p>Each file is validated via the shared catalog-aware
 * {@code DocumentValidator} (DITA topics/maps default to the bundled DITA
 * catalog). Results come back as a compact {@link BatchResult}: counts plus the
 * failing files (capped), so a clean run over a large project is a few numbers
 * rather than a flood.
 *
 * @param root     the project root (workspace folder) to resolve the scope against
 * @param scope    a {@code FileSet} spec: {@code "map:<path>"} (publication set),
 *                 {@code "glob:<pattern>"}, or {@code "root"} / null (all XML-ish)
 * @param catalogs optional explicit catalogs (the bundled DITA catalog is applied
 *                 automatically per DITA file regardless)
 */
public record ValidateProjectCommand(
    String root,
    String scope,
    List<Path> catalogs
) implements Command<BatchResult<FileValidation>>, ReadOnlyCommand {

    /** Validate everything under the root (all XML-ish files). */
    public ValidateProjectCommand(String root) {
        this(root, null, List.of());
    }

    /** Validate a scope with no explicit catalogs. */
    public ValidateProjectCommand(String root, String scope) {
        this(root, scope, List.of());
    }
}
