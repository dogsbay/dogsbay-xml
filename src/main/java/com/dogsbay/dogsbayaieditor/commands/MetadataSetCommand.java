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

import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.MetadataChange;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Bulk-apply metadata changes across a scope, field-preserving: locate-or-create
 * the prolog field, set/fill/append/remove the value, leaving all other prolog
 * content and formatting intact (the {@code PrologWriter}). Topics (.dita) only in
 * v1.
 *
 * @param root    the project root
 * @param scope   {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 * @param changes the changes to apply (field + value + mode)
 * @param dryRun  true to report what would change without writing
 */
public record MetadataSetCommand(
    String root,
    String scope,
    List<MetadataSetSpec> changes,
    boolean dryRun
) implements Command<BatchResult<MetadataChange>> {

    public MetadataSetCommand {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
