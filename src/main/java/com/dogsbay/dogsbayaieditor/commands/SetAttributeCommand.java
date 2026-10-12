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

import com.dogsbay.dogsbayaieditor.commands.results.AttributeChange;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Set one attribute on one element in every file of a scope.
 *
 * <p>{@code edit_structure} does this properly — splicing the text at the element's boundaries, so the rest of
 * the file keeps its formatting, indentation and line endings — but one document at a time. {@code metadata_set}
 * works across a scope but writes the prolog, through a closed list of DITA metadata fields. So adding
 * {@code xml:lang} to thirty topics had no route that was both: this is the structural editor's step over a file
 * set, with one plan for the whole of it.
 *
 * @param root         the project root
 * @param scope        {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 * @param select       XPath selecting the element in each file, or {@code #id}; {@code /*} for the root element
 * @param name         the attribute to set
 * @param value        the value to set it to
 * @param onlyIfAbsent true to leave a file alone when the element already has the attribute, whatever its value
 * @param dryRun       true to report what would change without writing
 */
public record SetAttributeCommand(
    String root,
    String scope,
    String select,
    String name,
    String value,
    boolean onlyIfAbsent,
    boolean dryRun
) implements Command<BatchResult<AttributeChange>> {}
