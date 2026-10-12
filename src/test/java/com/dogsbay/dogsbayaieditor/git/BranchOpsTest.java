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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Merging and deleting branches, against a real repository. */
class BranchOpsTest {

    @TempDir Path dir;

    private Git git;
    private String main;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(dir.toFile()).call();
        commit("a.txt", "one", "Initial commit");
        main = git.getRepository().getBranch();
    }

    @AfterEach
    void tearDown() {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
    }

    private void commit(String name, String content, String message) throws Exception {
        Files.writeString(dir.resolve(name), content);
        git.add().addFilepattern(name).call();
        git.commit().setMessage(message).call();
    }

    /** A branch with one commit on it, leaving the caller back on the original. */
    private void branchWithWork(String branch, String file, String content) throws Exception {
        git.checkout().setCreateBranch(true).setName(branch).call();
        commit(file, content, "Work on " + branch);
        git.checkout().setName(main).call();
    }

    @Test
    @DisplayName("a branch ahead of this one fast-forwards")
    void aBranchAheadFastForwards() throws Exception {
        branchWithWork("feature", "b.txt", "work");

        BranchOps.MergeReport report = BranchOps.merge(git, "feature");

        assertThat(report.outcome()).isEqualTo(BranchOps.MergeOutcome.FAST_FORWARD);
        assertThat(report.into()).isEqualTo(main);
        assertThat(dir.resolve("b.txt")).exists();
    }

    @Test
    @DisplayName("diverged histories are joined by a merge commit")
    void divergedHistoriesMerge() throws Exception {
        branchWithWork("feature", "b.txt", "work");
        commit("c.txt", "meanwhile", "Work on " + main);

        BranchOps.MergeReport report = BranchOps.merge(git, "feature");

        assertThat(report.outcome()).isEqualTo(BranchOps.MergeOutcome.MERGED);
        assertThat(dir.resolve("b.txt")).exists();
        assertThat(dir.resolve("c.txt")).exists();
    }

    @Test
    @DisplayName("merging something already contained changes nothing")
    void alreadyContainedIsSaidSo() throws Exception {
        branchWithWork("feature", "b.txt", "work");
        BranchOps.merge(git, "feature");

        BranchOps.MergeReport again = BranchOps.merge(git, "feature");

        assertThat(again.outcome()).isEqualTo(BranchOps.MergeOutcome.ALREADY_UP_TO_DATE);
    }

    @Test
    @DisplayName("a conflict is reported with the files it is in")
    void conflictsNameTheirFiles() throws Exception {
        git.checkout().setCreateBranch(true).setName("feature").call();
        commit("a.txt", "theirs", "Change on feature");
        git.checkout().setName(main).call();
        commit("a.txt", "ours", "Change on " + main);

        BranchOps.MergeReport report = BranchOps.merge(git, "feature");

        assertThat(report.outcome()).isEqualTo(BranchOps.MergeOutcome.CONFLICTS);
        assertThat(report.conflicts()).containsExactly("a.txt");
    }

    @Test
    @DisplayName("a merge over uncommitted work is refused before it starts")
    void uncommittedWorkBlocksTheMerge() throws Exception {
        branchWithWork("feature", "b.txt", "work");
        Files.writeString(dir.resolve("a.txt"), "edited but not committed");

        String blocker = BranchOps.mergeBlocker(git, "feature");

        assertThat(blocker).contains("uncommitted changes");
        // Nothing was touched: the branch is still unmerged.
        assertThat(BranchOps.isMerged(git, "feature")).isFalse();
    }

    @Test
    @DisplayName("a branch cannot be merged into itself, nor can one that does not exist")
    void nonsenseMergesAreRefused() throws Exception {
        assertThat(BranchOps.mergeBlocker(git, main)).contains("cannot be merged into itself");
        assertThat(BranchOps.mergeBlocker(git, "nope")).contains("no branch called");
        assertThat(BranchOps.mergeBlocker(git, null)).contains("No branch");
    }

    @Test
    @DisplayName("a clean tree on a real branch is not blocked")
    void aGoodMergeIsNotBlocked() throws Exception {
        branchWithWork("feature", "b.txt", "work");

        assertThat(BranchOps.mergeBlocker(git, "feature")).isNull();
    }

    @Test
    @DisplayName("a merged branch deletes without force")
    void aMergedBranchDeletesCleanly() throws Exception {
        branchWithWork("feature", "b.txt", "work");
        BranchOps.merge(git, "feature");
        assertThat(BranchOps.isMerged(git, "feature")).isTrue();

        BranchOps.delete(git, "feature", false);

        assertThat(BranchOps.localBranches(git)).doesNotContain("feature");
    }

    @Test
    @DisplayName("an unmerged branch refuses a plain delete and needs force")
    void anUnmergedBranchNeedsForce() throws Exception {
        branchWithWork("feature", "b.txt", "work");
        assertThat(BranchOps.isMerged(git, "feature")).isFalse();

        assertThatThrownBy(() -> BranchOps.delete(git, "feature", false)).isInstanceOf(Exception.class);

        BranchOps.delete(git, "feature", true);
        assertThat(BranchOps.localBranches(git)).doesNotContain("feature");
    }

    @Test
    @DisplayName("the branch you are on cannot be deleted")
    void theCurrentBranchIsProtected() throws Exception {
        assertThat(BranchOps.deleteBlocker(git, main)).contains("branch you are on");
        assertThat(BranchOps.deleteBlocker(git, "nope")).contains("no branch called");
    }

    @Test
    @DisplayName("branch names come back without refs/heads")
    void namesAreShort() throws Exception {
        branchWithWork("fix/health-check-blockers", "b.txt", "work");

        assertThat(BranchOps.localBranches(git)).contains(main, "fix/health-check-blockers");
    }
}
