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

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * Rename an element id: the {@code id} attribute itself plus every
 * reference fragment naming it — {@code #topic/oldId} (and, when the old
 * id is the topic id, {@code #oldId} forms) on href/conref project-wide,
 * and {@code key/oldId} on keyref/conkeyref when a root map is given to
 * resolve keys. Dry-run by default.
 *
 * @param file    the topic containing the element
 * @param oldId   the current id
 * @param newId   the new id
 * @param root    the project root whose references are rewritten
 * @param rootMap optional root map: enables key-mediated fragment rewriting
 * @param apply   true to execute; false returns the plan only
 */
public record RenameElementIdCommand(
    String file,
    String oldId,
    String newId,
    String root,
    String rootMap,
    boolean apply
) implements Command<RefactorResult> {}
