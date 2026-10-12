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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandTargetsTest {

    @Test
    void readOnlyMarkerAndTargetsAgreeForEveryCommandClass() {
        // The switch in CommandTargets is exhaustive by compilation; this checks
        // that the two declarations of "mutating" never drift apart.
        for (Class<?> c : Command.class.getPermittedSubclasses()) {
            boolean marked = ReadOnlyCommand.class.isAssignableFrom(c);
            Command<?> instance = Instances.dummy(c);
            boolean mutating = CommandTargets.of(instance).isMutating();
            if (marked) {
                assertThat(mutating).as(c.getSimpleName() + " is marked read-only yet has targets").isFalse();
            } else if (c != RenderPreviewCommand.class) {
                assertThat(mutating).as(c.getSimpleName() + " is unmarked yet has no targets").isTrue();
            }
        }
    }

    @Test
    void bufferEditsNameTheirDocumentOrTheActiveOne() {
        assertThat(CommandTargets.of(new SetContentCommand(Path.of("/p/a.dita"), "x")))
                .isEqualTo(new CommandTargets(CommandTargets.Scope.BUFFER, List.of(Path.of("/p/a.dita")), null, false));
        assertThat(CommandTargets.of(new ReplaceSelectionCommand("x")).activeDocument()).isTrue();
        assertThat(CommandTargets.of(new SaveCommand(null, false)).scope()).isEqualTo(CommandTargets.Scope.FILES);
    }

    @Test
    void treeCommandsCarryRootAndNamedFiles() {
        CommandTargets t = CommandTargets.of(new ExtractConrefCommand("a.dita", "id", "reuse/w.dita", true));
        assertThat(t.scope()).isEqualTo(CommandTargets.Scope.TREE);
        assertThat(t.files()).containsExactly(Path.of("a.dita"), Path.of("reuse/w.dita"));
        assertThat(t.root()).isNull();
        CommandTargets r = CommandTargets.of(new RenameKeyCommand("a", "b", "/proj", false));
        assertThat(r.allPaths()).containsExactly(Path.of("/proj"));
    }

    @Test
    void previewWithoutOutputIsARead() {
        assertThat(CommandTargets.of(new RenderPreviewCommand("a.dita", null, null, null)).isMutating()).isFalse();
        assertThat(CommandTargets.of(new RenderPreviewCommand("a.dita", "out.html", null, null)).files())
                .containsExactly(Path.of("out.html"));
        assertThat(CommandTargets.of(new OpenProjectCommand("p")).isMutating()).isTrue();
    }

    @Test
    void outputPathsWinOverInputsForFormatting() {
        assertThat(CommandTargets.of(new FormatCommand(Path.of("in.xml"), null, Path.of("out.xml"))).files())
                .containsExactly(Path.of("out.xml"));
        assertThat(CommandTargets.of(new FormatCommand(Path.of("in.xml"), null, null)).files())
                .containsExactly(Path.of("in.xml"));
    }
}
