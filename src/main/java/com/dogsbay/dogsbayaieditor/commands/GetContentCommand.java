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

/**
 * The text of a document in the editor.
 *
 * @param file       the document, or null for the active one
 * @param readClosed read a document that is not open rather than refuse: from its file when agents may edit
 *                   files that are not open (the default), else by opening it, since the edits that follow
 *                   will need it open. The MCP tool's behaviour, so an agent needs no open_document before
 *                   reading and then editing. False for callers that use the refusal to learn that a file is
 *                   not open (the write gate, the ACP file system).
 */
public record GetContentCommand(Path file, boolean readClosed) implements Command<String>, ReadOnlyCommand {

    public GetContentCommand(Path file) {
        this(file, false);
    }
}
