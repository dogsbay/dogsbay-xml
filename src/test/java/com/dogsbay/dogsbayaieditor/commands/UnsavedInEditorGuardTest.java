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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Changes made without the editor never go under a tab with unsaved changes; and which commands need it. */
class UnsavedInEditorGuardTest {

    @TempDir Path root;

    @Test
    void refusesAChangeToAFileOrFolderTheEditorHoldsUnsaved() throws Exception {
        root = root.toRealPath();
        Path held = Files.writeString(root.resolve("held.dita"), "<topic id=\"h\"/>");
        Path free = Files.writeString(root.resolve("free.dita"), "<topic id=\"f\"/>");
        RecordingExecutor recorder = new RecordingExecutor();
        UnsavedInEditorGuard guard = new UnsavedInEditorGuard(recorder, () -> Set.of(held));

        assertThatThrownBy(() -> guard.execute(new SetContentCommand(held, "x")))
                .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.CONFLICT)).hasMessageContaining("unsaved changes");
        assertThatThrownBy(() -> guard.execute(new FormatCommand(root.resolve("sub/../held.dita"), 2, root.resolve("sub/../held.dita"))))
                .as("however the path is spelled").hasMessageContaining("unsaved changes");
        guard.execute(new SetContentCommand(free, "x"));
        guard.execute(new GetContentCommand(held, true));
        assertThat(recorder.calls).hasSize(2).as("a change elsewhere and a read go through");
    }

    @Test
    void needsEditorMatchesTheCommandsTheHeadlessExecutorCannotRun() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/dogsbay/dogsbayaieditor/commands/HeadlessExecutor.java"));
        Matcher m = Pattern.compile("case (\\w+) c -> throw editorRequired\\(\\);").matcher(source);
        List<String> editorOnly = new java.util.ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            if (!source.contains("case " + name + " c when ")) { // a guarded case runs some calls here
                editorOnly.add(name);
            }
        }
        for (String name : editorOnly) {
            assertThat(HeadlessExecutor.needsEditor(Class.forName("com.dogsbay.dogsbayaieditor.commands." + name)))
                    .as(name).isTrue();
        }
        for (Class<?> c : Command.class.getPermittedSubclasses()) {
            if (HeadlessExecutor.needsEditor(c)) {
                assertThat(editorOnly).as(c.getSimpleName() + " is listed as needing the editor").contains(c.getSimpleName());
            }
        }
    }
}
