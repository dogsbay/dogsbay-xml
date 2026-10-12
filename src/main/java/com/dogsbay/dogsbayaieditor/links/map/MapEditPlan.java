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

package com.dogsbay.dogsbayaieditor.links.map;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The computed result of a map edit: the full new text of each file the edit
 * touches (the map, and for a cross-map move the destination map), plus any
 * reference-safety warnings. Edits are formatting-preserving textual splices, so a
 * plan is just "this file becomes this text" — apply writes them, dry-run returns
 * the plan unwritten (the refactor-command convention).
 */
public record MapEditPlan(List<FileChange> changes, List<String> warnings) {

    /** One file's new content. */
    public record FileChange(File file, String newText) {}

    public MapEditPlan {
        changes = changes == null ? List.of() : List.copyOf(changes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** Write every file change (UTF-8). Returns the files written. */
    public List<File> apply() throws IOException {
        List<File> written = new ArrayList<>();
        for (FileChange c : changes) {
            Files.writeString(c.file().toPath(), c.newText(), StandardCharsets.UTF_8);
            written.add(c.file());
        }
        return written;
    }
}
