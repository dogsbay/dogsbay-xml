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

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.xml.edit.StructuralEdit;

/**
 * Change a document's structure with ordered steps (insert, delete, replace, move, wrap, unwrap,
 * rename, set or remove an attribute, set an element's text, test), each selecting exactly one
 * element by XPath or {@code #id}; all apply or none do (see {@link StructuralEdit}). The result is
 * validated first and refused if it adds an error. An agent's steps land as review proposals marked
 * from the steps themselves.
 *
 * @param file   the document
 * @param steps  the steps, in order
 * @param dryRun report what the steps would do without writing
 */
public record EditStructureCommand(
    Path file,
    List<StructuralEdit.Step> steps,
    boolean dryRun
) implements Command<CommandResult> {}
