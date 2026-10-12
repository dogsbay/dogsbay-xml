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

import com.dogsbay.dogsbayaieditor.git.actions.DiscardChangesAction;
import com.dogsbay.dogsbayaieditor.git.actions.StageFileAction;
import com.dogsbay.dogsbayaieditor.git.actions.UnstageFileAction;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the bulk (multi-selection) Git actions against a real temporary repository.
 */
public class GitBulkActionsTest {

    private Path tempDir;
    private Git git;
    private GitPanel gitPanel;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("git-bulk-actions-test");
        git = Git.init().setDirectory(tempDir.toFile()).call();

        // These tests assert exact file bytes. Git for Windows sets core.autocrlf=true in
        // the system config, which JGit honours for a fresh repo, so pin it off.
        org.eclipse.jgit.storage.file.FileBasedConfig config =
                (org.eclipse.jgit.storage.file.FileBasedConfig) git.getRepository().getConfig();
        config.setBoolean("core", null, "autocrlf", false);
        config.save();

        for (int i = 1; i <= 5; i++) {
            Files.writeString(tempDir.resolve("f" + i + ".txt"), "original " + i + "\n");
        }
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();

        gitPanel = new GitPanel(git);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
        if (tempDir != null) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** Counts calls to {@link GitPanel#reloadOpenBuffers()}. */
    private static class RecordingGitPanel extends GitPanel {
        int reloadCount;

        RecordingGitPanel(Git git) {
            super(git);
        }

        @Override
        public void reloadOpenBuffers() {
            reloadCount++;
        }
    }

    /** Builds a node for a file in the temp repository. */
    private GitFileNode node(String name, GitFileNode.GitStatus status) {
        return new GitFileNode(new File(tempDir.toFile(), name), status, name);
    }

    /** Overwrites a tracked file so it shows up as modified. */
    private void modify(String name) throws Exception {
        Files.writeString(tempDir.resolve(name), "changed\n");
    }

    private String contentOf(String name) throws Exception {
        return Files.readString(tempDir.resolve(name));
    }

    @Test
    void discardsOnlyTheSelectedFiles() throws Exception {
        for (int i = 1; i <= 5; i++) {
            modify("f" + i + ".txt");
        }

        DiscardChangesAction action = new DiscardChangesAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("f2.txt", GitFileNode.GitStatus.MODIFIED),
                node("f3.txt", GitFileNode.GitStatus.MODIFIED)));

        assertTrue(action.isEnabled());
        action.performDiscard();

        assertEquals("original 1\n", contentOf("f1.txt"), "f1 should be reverted");
        assertEquals("original 2\n", contentOf("f2.txt"), "f2 should be reverted");
        assertEquals("original 3\n", contentOf("f3.txt"), "f3 should be reverted");
        assertEquals("changed\n", contentOf("f4.txt"), "f4 was not selected and must be untouched");
        assertEquals("changed\n", contentOf("f5.txt"), "f5 was not selected and must be untouched");

        Status status = git.status().call();
        assertEquals(java.util.Set.of("f4.txt", "f5.txt"), status.getModified());
    }

    @Test
    void aNewFileIsDeletedWhileATrackedOneIsReverted() throws Exception {
        modify("f1.txt");
        Files.writeString(tempDir.resolve("new.txt"), "brand new\n");

        DiscardChangesAction action = new DiscardChangesAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("new.txt", GitFileNode.GitStatus.UNTRACKED)));

        assertEquals(1, action.getRevertedFiles().size());
        assertEquals(1, action.getDeletedFiles().size(), "a new file is discarded by deleting it");
        assertTrue(action.getSkippedFiles().isEmpty());

        action.performDiscard();

        assertEquals("original 1\n", contentOf("f1.txt"), "the tracked file reverts");
        assertFalse(Files.exists(tempDir.resolve("new.txt")),
                "a new file has no committed version, so discarding it removes it");
    }

    @Test
    void discardWorksWhenEverythingSelectedIsNew() throws Exception {
        Files.writeString(tempDir.resolve("new1.txt"), "a\n");
        Files.writeString(tempDir.resolve("new2.txt"), "b\n");

        DiscardChangesAction action = new DiscardChangesAction(gitPanel, List.of(
                node("new1.txt", GitFileNode.GitStatus.UNTRACKED),
                node("new2.txt", GitFileNode.GitStatus.UNTRACKED)));

        // Refusing here left no way to get rid of a new file from the editor.
        assertTrue(action.isEnabled());
        assertEquals("Delete New Files (2)...", action.getValue(Action.NAME),
                "deleting a new file is not 'discarding changes', and should not say it is");
        action.performDiscard();

        assertFalse(Files.exists(tempDir.resolve("new1.txt")));
        assertFalse(Files.exists(tempDir.resolve("new2.txt")));
    }

    @Test
    void discardLabelReportsTheFileCount() throws Exception {
        modify("f1.txt");
        modify("f2.txt");

        DiscardChangesAction multi = new DiscardChangesAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("f2.txt", GitFileNode.GitStatus.MODIFIED)));
        assertEquals("Discard Changes (2 Files)...", multi.getValue(Action.NAME));

        DiscardChangesAction single = new DiscardChangesAction(gitPanel,
                node("f1.txt", GitFileNode.GitStatus.MODIFIED));
        assertEquals("Discard Changes...", single.getValue(Action.NAME));

        DiscardChangesAction newFile = new DiscardChangesAction(gitPanel,
                node("new.txt", GitFileNode.GitStatus.UNTRACKED));
        assertEquals("Delete New File...", newFile.getValue(Action.NAME));
    }

    @Test
    void stagesEverySelectedFile() throws Exception {
        modify("f1.txt");
        modify("f2.txt");
        modify("f3.txt");

        StageFileAction action = new StageFileAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("f2.txt", GitFileNode.GitStatus.MODIFIED),
                node("f3.txt", GitFileNode.GitStatus.MODIFIED)));

        assertEquals("Stage 3 Files", action.getValue(Action.NAME));
        action.actionPerformed(null);

        Status status = git.status().call();
        assertEquals(java.util.Set.of("f1.txt", "f2.txt", "f3.txt"), status.getChanged(),
                "all three files should now be staged");
        assertTrue(status.getModified().isEmpty(), "nothing should be left unstaged");
    }

    @Test
    void stagingADeletedFileRecordsTheRemoval() throws Exception {
        Files.delete(tempDir.resolve("f1.txt"));

        new StageFileAction(gitPanel, List.of(node("f1.txt", GitFileNode.GitStatus.DELETED)))
                .actionPerformed(null);

        Status status = git.status().call();
        assertTrue(status.getRemoved().contains("f1.txt"),
                "a deleted file must be staged as a removal");
        assertTrue(status.getMissing().isEmpty());
    }

    @Test
    void unstagesEverySelectedFile() throws Exception {
        modify("f1.txt");
        modify("f2.txt");
        git.add().addFilepattern("f1.txt").addFilepattern("f2.txt").call();
        assertEquals(2, git.status().call().getChanged().size());

        UnstageFileAction action = new UnstageFileAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.STAGED),
                node("f2.txt", GitFileNode.GitStatus.STAGED)));

        assertEquals("Unstage 2 Files", action.getValue(Action.NAME));
        action.actionPerformed(null);

        Status status = git.status().call();
        assertTrue(status.getChanged().isEmpty(), "nothing should remain staged");
        assertEquals(java.util.Set.of("f1.txt", "f2.txt"), status.getModified());
    }

    @Test
    void alreadyStagedFilesAreExcludedFromABulkStage() throws Exception {
        modify("f1.txt");
        git.add().addFilepattern("f1.txt").call();
        String stagedSnapshot = contentOf("f1.txt");
        Files.writeString(tempDir.resolve("f1.txt"), "edited again\n");
        modify("f2.txt");

        StageFileAction action = new StageFileAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.STAGED),
                node("f2.txt", GitFileNode.GitStatus.MODIFIED)));

        // Only f2 is actually stageable, so the label stays singular.
        assertEquals("Stage", action.getValue(Action.NAME));
        assertTrue(action.isEnabled());

        action.actionPerformed(null);

        Status status = git.status().call();
        assertTrue(status.getChanged().contains("f2.txt"), "f2 should have been staged");
        assertTrue(status.getModified().contains("f1.txt"),
                "f1's newer edit must NOT have been staged — it was filtered out");
        assertNotEquals(stagedSnapshot, contentOf("f1.txt"),
                "sanity: f1's working tree really did diverge from its staged snapshot");
    }

    /**
     * Checkout by path with no start point restores from the index, which for a staged
     * file is a no-op and for a staged-then-edited file restores the staged version.
     * Discard promises to revert to the last committed state, so it must use HEAD.
     */
    @Test
    void discardRevertsToHeadNotToTheIndex() throws Exception {
        modify("f1.txt");
        git.add().addFilepattern("f1.txt").call();
        Files.writeString(tempDir.resolve("f1.txt"), "edited after staging\n");

        new DiscardChangesAction(gitPanel, List.of(node("f1.txt", GitFileNode.GitStatus.STAGED)))
                .performDiscard();

        assertEquals("original 1\n", contentOf("f1.txt"),
                "the working tree must go back to the committed content, not the staged one");
        Status status = git.status().call();
        assertFalse(status.getChanged().contains("f1.txt"),
                "the staged change must be dropped too");
        assertFalse(status.getModified().contains("f1.txt"));
    }

    @Test
    void discardOfAStagedOnlyFileIsNotANoOp() throws Exception {
        modify("f2.txt");
        git.add().addFilepattern("f2.txt").call();
        assertTrue(git.status().call().getChanged().contains("f2.txt"));

        new DiscardChangesAction(gitPanel, List.of(node("f2.txt", GitFileNode.GitStatus.STAGED)))
                .performDiscard();

        assertEquals("original 2\n", contentOf("f2.txt"));
        assertTrue(git.status().call().isClean(), "the repository should be clean again");
    }

    /**
     * A discard rewrites files on disk. Any tab holding one is left showing the content
     * that was just thrown away, and saving that tab would write it back — the editor's
     * auto-reload is driven by window focus, which an in-app operation never triggers.
     */
    @Test
    void discardTellsOpenBuffersTheFilesChanged() throws Exception {
        RecordingGitPanel panel = new RecordingGitPanel(git);
        modify("f1.txt");
        modify("f2.txt");

        new DiscardChangesAction(panel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("f2.txt", GitFileNode.GitStatus.MODIFIED))).performDiscard();

        assertEquals("original 1\n", contentOf("f1.txt"), "sanity: the discard happened");
        assertEquals(1, panel.reloadCount,
                "open buffers must be told once that the files changed underneath them");
    }

    @Test
    void stagingDoesNotDisturbOpenBuffers() throws Exception {
        RecordingGitPanel panel = new RecordingGitPanel(git);
        modify("f1.txt");

        new StageFileAction(panel, List.of(node("f1.txt", GitFileNode.GitStatus.MODIFIED)))
                .actionPerformed(null);
        new UnstageFileAction(panel, List.of(node("f1.txt", GitFileNode.GitStatus.STAGED)))
                .actionPerformed(null);

        assertEquals(0, panel.reloadCount,
                "staging and unstaging only touch the index, so open buffers are untouched");
    }

    @Test
    void reloadingOpenBuffersIsSafeWithoutAnEditor() {
        // The package-private test constructor leaves the parent editor null.
        assertDoesNotThrow(() -> gitPanel.reloadOpenBuffers());
    }

    /**
     * Conflicted files land in the skipped bucket alongside untracked ones; the
     * confirmation wording must not lump them together. Wording itself is asserted in
     * {@code DiscardChangesMessageTest}.
     */
    @Test
    void conflictedFilesAreSkippedNotDiscarded() throws Exception {
        modify("f1.txt");

        DiscardChangesAction action = new DiscardChangesAction(gitPanel, List.of(
                node("f1.txt", GitFileNode.GitStatus.MODIFIED),
                node("f2.txt", GitFileNode.GitStatus.CONFLICT)));

        assertEquals(1, action.getRevertedFiles().size());
        assertTrue(action.getDeletedFiles().isEmpty(), "a conflicted file is committed; never delete it");
        assertEquals(1, action.getSkippedFiles().size());
        assertEquals(GitFileNode.GitStatus.CONFLICT, action.getSkippedFiles().get(0).getStatus());
    }
}
