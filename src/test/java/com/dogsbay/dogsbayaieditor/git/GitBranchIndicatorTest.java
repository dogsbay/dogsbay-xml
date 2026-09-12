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

package com.dogsbay.dogsbayaieditor.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The branch shown in the status bar was only ever set when a repository was
 * opened, so a branch changed under the editor — by an agent, or a terminal
 * running {@code git checkout} — left the status bar naming the old one, even
 * though the panel beside it had already picked up the new files.
 */
class GitBranchIndicatorTest {

    @TempDir Path dir;

    private Git git;
    private final AtomicReference<String> shown = new AtomicReference<>();

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

    private GitPanel panel() {
        return new GitPanel(git, dir.toFile(), shown::set);
    }

    @Test
    @DisplayName("a refresh names the branch the repository is actually on")
    void refreshNamesTheCurrentBranch() throws Exception {
        GitPanel panel = panel();

        panel.loadGitStatus();

        assertThat(shown.get()).isEqualTo(git.getRepository().getBranch());
    }

    @Test
    @DisplayName("a branch created outside the editor reaches the status bar")
    void aBranchCreatedOutsideTheEditorIsPickedUp() throws Exception {
        GitPanel panel = panel();
        panel.loadGitStatus();
        String before = shown.get();

        // What the agent did: a new branch, without the editor being told.
        git.checkout().setCreateBranch(true).setName("fix/health-check-blockers").call();
        panel.loadGitStatus();   // the auto-refresh tick

        assertThat(before).isNotEqualTo("fix/health-check-blockers");
        assertThat(shown.get()).isEqualTo("fix/health-check-blockers");
    }

    @Test
    @DisplayName("switching back is picked up too")
    void switchingBackIsPickedUp() throws Exception {
        GitPanel panel = panel();
        String original = git.getRepository().getBranch();
        git.checkout().setCreateBranch(true).setName("side").call();
        panel.loadGitStatus();
        assertThat(shown.get()).isEqualTo("side");

        git.checkout().setName(original).call();
        panel.loadGitStatus();

        assertThat(shown.get()).isEqualTo(original);
    }

    @Test
    @DisplayName("changed files do not disturb the branch reported")
    void uncommittedChangesDoNotConfuseIt() throws Exception {
        GitPanel panel = panel();
        git.checkout().setCreateBranch(true).setName("work").call();
        Files.writeString(dir.resolve("a.txt"), "two");
        Files.writeString(dir.resolve("b.txt"), "new");

        panel.loadGitStatus();

        assertThat(shown.get()).isEqualTo("work");
    }

}
