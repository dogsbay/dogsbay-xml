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

import com.dogsbay.dogsbayaieditor.git.GitFileNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the wording of the discard confirmation dialog.
 *
 * <p>This is a destructive operation whose only safeguard is that the user reads and
 * understands the prompt, so the text is worth asserting: it must not describe tracked
 * files as new, and must not advise deleting anything Git is tracking.
 */
public class DiscardChangesMessageTest {

    private GitFileNode node(String name, GitFileNode.GitStatus status) {
        return new GitFileNode(new File("/repo/" + name), status, name);
    }

    private String messageFor(GitFileNode... nodes) {
        return new DiscardChangesAction(null, List.of(nodes)).buildConfirmationMessage();
    }

    @Test
    void namesTheFileWhenOnlyOneIsSelected() {
        String message = messageFor(node("a.txt", GitFileNode.GitStatus.MODIFIED));

        assertTrue(message.contains("a.txt"));
        assertTrue(message.contains("cannot be undone"));
    }

    @Test
    void listsTheFilesWhenSeveralAreSelected() {
        String message = messageFor(
                node("a.txt", GitFileNode.GitStatus.MODIFIED),
                node("b.txt", GitFileNode.GitStatus.MODIFIED),
                node("c.txt", GitFileNode.GitStatus.DELETED));

        assertTrue(message.contains("3 files"));
        assertTrue(message.contains("a.txt"));
        assertTrue(message.contains("b.txt"));
        assertTrue(message.contains("c.txt"));
    }

    @Test
    void summarisesRatherThanListingALongSelection() {
        GitFileNode[] many = new GitFileNode[16];
        for (int i = 0; i < many.length; i++) {
            many[i] = node("f" + i + ".txt", GitFileNode.GitStatus.MODIFIED);
        }

        String message = messageFor(many);

        assertTrue(message.contains("16 files"));
        assertTrue(message.contains("... and 6 more"), "should list 10 then summarise");
    }

    @Test
    void separatesWhatIsRevertedFromWhatIsDeleted() {
        String message = messageFor(
                node("a.txt", GitFileNode.GitStatus.MODIFIED),
                node("new.txt", GitFileNode.GitStatus.UNTRACKED));

        // Two different promises; the dialog must not blur them into one.
        assertTrue(message.contains("Back to the last commit:"));
        assertTrue(message.contains("a.txt"));
        assertTrue(message.contains("Deleted (new, never committed):"));
        assertTrue(message.contains("new.txt"));
    }

    @Test
    void aLoneNewFileSaysItIsBeingDeletedAndWhy() {
        String message = messageFor(node("new.txt", GitFileNode.GitStatus.UNTRACKED));

        assertTrue(message.contains("delete the new file"));
        assertTrue(message.contains("never been committed"));
        assertTrue(message.contains("cannot be undone"));
    }

    @Test
    void aNewFileIsNeverDeletedWithoutBeingNamed() {
        GitFileNode[] many = new GitFileNode[16];
        for (int i = 0; i < many.length; i++) {
            many[i] = node("new" + i + ".txt", GitFileNode.GitStatus.UNTRACKED);
        }

        String message = messageFor(many);

        assertTrue(message.contains("Deleted (new, never committed):"));
        // Every one of them, however many. A revert can be recovered from the
        // last commit; a deletion cannot be recovered at all, so "and 6 more"
        // is not good enough for this half of the list.
        for (int i = 0; i < many.length; i++) {
            assertTrue(message.contains("new" + i + ".txt"), "new" + i + ".txt must be named");
        }
        assertFalse(message.contains("more\n"), "the deletions must not be summarised");
    }

    @Test
    void neverDeletesAConflictedFile() {
        String message = messageFor(
                node("a.txt", GitFileNode.GitStatus.MODIFIED),
                node("conflict.txt", GitFileNode.GitStatus.CONFLICT));

        // A conflicted file is tracked and committed; deleting it destroys content.
        assertTrue(message.contains("cannot be reverted"));
        assertFalse(message.contains("Deleted (new"));
    }

    @Test
    void neverDeletesAStagedDeletion() {
        String message = messageFor(
                node("a.txt", GitFileNode.GitStatus.MODIFIED),
                node("gone.txt", GitFileNode.GitStatus.REMOVED));

        assertTrue(message.contains("cannot be reverted"));
        assertFalse(message.contains("Deleted (new"));
    }

    @Test
    void newFilesAreActedOnWhileConflictedOnesAreNot() {
        String message = messageFor(
                node("a.txt", GitFileNode.GitStatus.MODIFIED),
                node("new.txt", GitFileNode.GitStatus.UNTRACKED),
                node("conflict.txt", GitFileNode.GitStatus.CONFLICT));

        assertTrue(message.contains("Deleted (new, never committed):"));
        assertTrue(message.contains("new.txt"));
        assertTrue(message.contains("1 selected file cannot be reverted"));
    }

    @Test
    void countsAppearInTheActionLabel() {
        assertEquals("Discard Changes (2 Files)...",
                new DiscardChangesAction(null, List.of(
                        node("a.txt", GitFileNode.GitStatus.MODIFIED),
                        node("b.txt", GitFileNode.GitStatus.MODIFIED)))
                        .getValue(javax.swing.Action.NAME));

        // Both files are acted on now, so both are counted.
        assertEquals("Discard Changes (2 Files)...",
                new DiscardChangesAction(null, List.of(
                        node("a.txt", GitFileNode.GitStatus.MODIFIED),
                        node("new.txt", GitFileNode.GitStatus.UNTRACKED)))
                        .getValue(javax.swing.Action.NAME));

        // Only new files: it says what it will actually do.
        assertEquals("Delete New File...",
                new DiscardChangesAction(null, List.of(
                        node("new.txt", GitFileNode.GitStatus.UNTRACKED)))
                        .getValue(javax.swing.Action.NAME));
    }
}
