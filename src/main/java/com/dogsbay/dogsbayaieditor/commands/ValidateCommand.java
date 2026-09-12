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
import com.dogsbay.dogsbayaieditor.commands.results.ValidationResult;

/**
 * Validate an XML document.
 *
 * @param file      the XML file to validate
 * @param schema    optional schema path (XSD, DTD, or RNG). null = well-formedness only.
 * @param catalogs  optional list of XML catalog files for entity resolution
 */
public record ValidateCommand(
    Path file,
    Path schema,
    List<Path> catalogs
) implements Command<ValidationResult>, ReadOnlyCommand {

    /** Convenience: validate well-formedness only. */
    public ValidateCommand(Path file) {
        this(file, null, List.of());
    }

    /** Convenience: validate against schema, no catalogs. */
    public ValidateCommand(Path file, Path schema) {
        this(file, schema, List.of());
    }
}
