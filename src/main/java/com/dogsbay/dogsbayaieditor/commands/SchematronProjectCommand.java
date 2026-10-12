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

import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Run a Schematron schema against every file in a scope, on disk — business /
 * style rules ("every {@code <step>} has a {@code <cmd>}", "no hardcoded product
 * name") that DTD/XSD/RelaxNG can't express. Uses ph-schematron (the same engine
 * as xagent's single-file {@code xml_validate}); returns a compact
 * {@link BatchResult} of failed assertions.
 *
 * @param root   the project root to resolve the scope against
 * @param scope  a {@code FileSet} spec: {@code "map:<path>"}, {@code "glob:<pattern>"},
 *               or {@code "root"} / null (all XML-ish)
 * @param schema the {@code .sch} Schematron schema to apply
 */
public record SchematronProjectCommand(
    String root,
    String scope,
    String schema
) implements Command<BatchResult<SchematronFinding>>, ReadOnlyCommand {

    /** Apply the schema to everything under the root (all XML-ish files). */
    public SchematronProjectCommand(String root, String schema) {
        this(root, null, schema);
    }
}
