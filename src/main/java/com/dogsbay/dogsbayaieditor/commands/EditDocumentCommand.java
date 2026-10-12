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

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Replace one exact piece of a document's text, leaving the rest of the file alone: the
 * edit an agent makes when it knows what to change. The text to find must appear exactly
 * once, unless {@code all} asks for every occurrence, so an edit never lands somewhere
 * unintended. The document must be open; an agent's edit reaches it as a review proposal,
 * like every other agent edit.
 *
 * @param file    the document, or null for the active one
 * @param find    the text to replace; must match exactly, whitespace included
 * @param replace what to put in its place ("" deletes the text)
 * @param all     replace every occurrence (default: refuse when there is more than one)
 */
public record EditDocumentCommand(
    Path file,
    String find,
    String replace,
    Boolean all
) implements Command<CommandResult> {

    /** An edit of the only occurrence. */
    public EditDocumentCommand(Path file, String find, String replace) {
        this(file, find, replace, null);
    }
}
