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

package com.dogsbay.dogsbayaieditor.git.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading the tags a repository has.
 *
 * <p>The Git panel could create branches and not tags, so a lesson that wanted "the state at stage 12" used a
 * branch — which moves when you commit on it, and the mark of a moment should not (reported 2026-09-29).
 *
 * <p>The dialogs need a display; what they are built on does not, and this is it.
 */
class TagActionsTest {

    @TempDir Path dir;

    private Git git;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(dir.toFile()).call();
        Files.writeString(dir.resolve("a.txt"), "one");
        git.add().addFilepattern("a.txt").call();
        git.commit().setMessage("Initial commit").call();
    }

    @AfterEach
    void tearDown() {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
    }

    @Test
    void aRepositoryWithNoTagsListsNone() {
        assertThat(CreateTagAction.existingTags(git)).isEmpty();
    }

    @Test
    void tagsAreListedByTheirShortName() throws Exception {
        git.tag().setName("stage-01").setAnnotated(true).setMessage("The first stage").call();
        git.tag().setName("stage-02").setAnnotated(false).call();

        // refs/tags/stage-01 is not what a reader calls it, and both kinds of tag are theirs.
        assertThat(CreateTagAction.existingTags(git)).containsExactlyInAnyOrder("stage-01", "stage-02");
    }

    @Test
    void anExistingNameIsRecognisedSoItIsNotMovedSilently() throws Exception {
        git.tag().setName("stage-01").setAnnotated(true).setMessage("here").call();

        // What the action checks before it creates: a tag marks a fixed point, and JGit would need --force to
        // move it. Refusing beats quietly pointing a name somewhere new.
        assertThat(CreateTagAction.existingTags(git)).contains("stage-01");
    }
}
