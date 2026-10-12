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

package com.dogsbay.dogsbayaieditor.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.undo.AbstractUndoableEdit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.xml.DogsBayDocument;

/**
 * Which open buffers Format Project has to save before it reads the files on disk.
 *
 * <p>The question used to be {@code DogsBayDocument.hasChangedOnDisk()}, which means "someone wrote this
 * file" — and formatting a file makes that true. So every file the format changed was skipped by the very
 * refresh meant to bring its buffer back in step: the refresh did nothing at all, and the reader met "changed
 * by another process" instead (reported 2026-09-30).
 */
class FormatProjectSaveTest {

    @TempDir Path dir;

    private DogsBayDocument opened(String name) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, "<concept id=\"c\"><title>T</title></concept>");
        return new DogsBayDocument(file.toUri().toURL());
    }

    /** A change manager with one edit in it, which is what an unsaved buffer looks like. */
    private static ChangeManager edited() {
        ChangeManager changes = new ChangeManager();
        changes.addEdit(new AbstractUndoableEdit() {
            private static final long serialVersionUID = 1L;
        });
        return changes;
    }

    @Test
    void aBufferWithUnsavedEditsIsSaved() throws Exception {
        assertThat(FormatProjectAction.needsSaving(opened("a.dita"), edited())).isTrue();
    }

    @Test
    void aFileRewrittenOnDiskWithACleanBufferIsNot() throws Exception {
        // The regression this replaced: the timestamp moved and nobody typed a thing. Asking the file would
        // say "changed"; asking the undo history says what is true, which is that there is nothing to save.
        DogsBayDocument document = opened("b.dita");
        Path file = dir.resolve("b.dita");
        Files.writeString(file, "<concept id=\"c\">\n  <title>T</title>\n</concept>");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + 10_000));
        assertThat(document.hasChangedOnDisk()).as("the file did change underneath us").isTrue();

        assertThat(FormatProjectAction.needsSaving(document, new ChangeManager())).isFalse();
    }

    @Test
    void aSavedBufferIsNotSavedAgain() throws Exception {
        // markSave() is what the save path must call; without it the buffer still reports unsaved edits and
        // the refresh skips the very file the reader had just asked to save.
        ChangeManager changes = edited();
        changes.markSave();

        assertThat(FormatProjectAction.needsSaving(opened("c.dita"), changes)).isFalse();
    }

    @Test
    void aReadOnlyBufferIsNotOffered() throws Exception {
        // Save All excludes these before it writes anything; offering one only produces a failure dialog.
        DogsBayDocument document = opened("d.dita");
        assumeTrue(dir.resolve("d.dita").toFile().setReadOnly(), "the filesystem allows read-only files");

        assertThat(FormatProjectAction.needsSaving(document, edited())).isFalse();
    }

    @Test
    void aBufferWithNowhereToBeSavedIsNotOffered() {
        assertThat(FormatProjectAction.needsSaving(
                new DogsBayDocument("<concept id=\"c\"><title>T</title></concept>"), edited())).isFalse();
    }

    @Test
    void nothingAtAllIsNotSomethingToSave() {
        assertThat(FormatProjectAction.needsSaving(null, edited())).isFalse();
        assertThat(FormatProjectAction.needsSaving(null, null)).isFalse();
    }
}
