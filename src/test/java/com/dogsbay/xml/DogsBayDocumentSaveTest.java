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

package com.dogsbay.xml;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What {@code save()} writes, and what it records afterwards.
 *
 * <p>Context: {@code save()} writes the document <em>model</em>, not the live
 * editor buffer. The editor's text reaches the model only when
 * {@code DogsBayView.updateModel()} runs {@code Editor.parse()}, and a commit
 * that emptied that method turned every save into a write of stale text —
 * silently discarding whatever had been typed. These tests pin the half of that
 * chain reachable without a running editor; the editor-to-model half needs the
 * editor-level UI harness this project does not yet have.
 */
class DogsBayDocumentSaveTest {

    /** A document opened and loaded, as the editor would have it. */
    private static DogsBayDocument openedOn(Path file, String content) throws Exception {
        Files.writeString(file, content);
        DogsBayDocument doc = new DogsBayDocument(file.toUri().toURL());
        doc.load();   // the constructor does not read the file
        return doc;
    }

    @Test
    void savesWhateverTheModelCurrentlyHolds(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file, "<a>original</a>");

        doc.setText("<a>edited</a>");
        doc.save();

        assertEquals("<a>edited</a>", Files.readString(file),
                "save must write the model's current text");
    }

    @Test
    void aSecondSaveWritesTheSecondEdit(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file, "<a>one</a>");

        doc.setText("<a>two</a>");
        doc.save();
        doc.setText("<a>three</a>");
        doc.save();

        assertEquals("<a>three</a>", Files.readString(file));
    }

    /**
     * Saving must leave the document in step with disk. If it did not, the next
     * external-change check would see its own write as a foreign edit and reload
     * over the buffer.
     */
    @Test
    void savingLeavesTheDocumentInStepWithDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file, "<a>original</a>");

        doc.setText("<a>edited</a>");
        doc.save();

        assertFalse(doc.hasChangedOnDisk(),
                "our own save must not register as an external change");
    }

    @Test
    void savePreservesCrlfLineEndings(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("crlf.xml");
        DogsBayDocument doc = openedOn(file, "<a>\r\n  <b/>\r\n</a>");

        doc.setText(doc.getText().replace("<b/>", "<b>x</b>"));
        doc.save();

        String written = Files.readString(file);
        assertTrue(written.contains("\r\n"), "CRLF endings must survive a save");
        // A doubled CRLF is "\r\n\r\n", which contains no "\n\n" — checking for
        // the latter could not detect the doubling it claimed to.
        assertFalse(written.contains("\r\n\r\n"), "and must not be doubled");
    }
}
