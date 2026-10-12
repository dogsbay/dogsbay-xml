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
 * Tests for external-change detection.
 *
 * <p>Regression: {@code hasChangedOnDisk()} replaced an {@code isModified()} that
 * recorded the new timestamp as a <em>side effect</em>, so it reported any given
 * external change exactly once — whichever caller asked first consumed it and
 * every later check saw nothing. That silently swallowed reload prompts for
 * buffers with unsaved edits, and the next save then overwrote a change the user
 * was never told about.
 *
 * <p>The contract is now split: asking is free, and a caller that has brought the
 * buffer back in step says so explicitly.
 */
class DogsBayDocumentExternalChangeTest {

    /**
     * Writes {@code content} with a timestamp far enough in the past to be
     * distinguishable — filesystem timestamp granularity would otherwise make two
     * writes in the same test look identical.
     */
    private static Path write(Path file, String content, long ageMillis) throws Exception {
        Files.writeString(file, content);
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis() - ageMillis));
        return file;
    }

    private static DogsBayDocument opened(Path file) throws Exception {
        return new DogsBayDocument(file.toUri().toURL());
    }

    @Test
    void reportsNoChangeForAFreshlyOpenedDocument(@TempDir Path dir) throws Exception {
        DogsBayDocument doc = opened(write(dir.resolve("a.xml"), "<a/>", 10_000));

        assertFalse(doc.hasChangedOnDisk(), "just loaded — nothing has changed since");
    }

    @Test
    void detectsAnExternalWrite(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 10_000);
        DogsBayDocument doc = opened(file);

        write(file, "<a>edited</a>", 0);

        assertTrue(doc.hasChangedOnDisk());
    }

    /** The defect that motivated the split. */
    @Test
    void askingRepeatedlyKeepsReportingTheChange(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 10_000);
        DogsBayDocument doc = opened(file);
        write(file, "<a>edited</a>", 0);

        assertTrue(doc.hasChangedOnDisk(), "first ask");
        assertTrue(doc.hasChangedOnDisk(),
                "second ask — the old isModified() returned false here, so whichever "
                        + "caller asked first swallowed the change for everyone else");
        assertTrue(doc.hasChangedOnDisk(), "and still, however many times we look");
    }

    @Test
    void acknowledgingStopsTheReport(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 10_000);
        DogsBayDocument doc = opened(file);
        write(file, "<a>edited</a>", 0);
        assertTrue(doc.hasChangedOnDisk());

        doc.acknowledgeDiskState();

        assertFalse(doc.hasChangedOnDisk(),
                "the caller declared itself in step — e.g. the user declined a reload");
    }

    @Test
    void aFurtherChangeAfterAcknowledgingIsDetected(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 20_000);
        DogsBayDocument doc = opened(file);
        write(file, "<a>one</a>", 10_000);
        doc.acknowledgeDiskState();

        write(file, "<a>two</a>", 0);

        assertTrue(doc.hasChangedOnDisk(), "acknowledging one change must not deafen us");
    }

    /**
     * load() acknowledges, so a reload does not leave the document still looking
     * externally changed. Nothing else records the timestamp — before this, only
     * the old isModified()'s side effect did.
     */
    @Test
    void reloadingLeavesTheDocumentInStep(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 10_000);
        DogsBayDocument doc = opened(file);
        write(file, "<a>edited</a>", 0);
        assertTrue(doc.hasChangedOnDisk());

        doc.load();

        assertFalse(doc.hasChangedOnDisk(), "we just read the file we were told about");
    }

    /**
     * The reload path must acknowledge even when the content will not parse —
     * Markdown, AsciiDoc, or XML that is momentarily malformed on disk. The text
     * is read before parsing either way, so the document IS in step; treating the
     * parse failure as "never read it" left the change pending forever.
     */
    @Test
    void reloadingUnparseableContentStillAcknowledges(@TempDir Path dir) throws Exception {
        Path file = write(dir.resolve("a.xml"), "<a/>", 10_000);
        DogsBayDocument doc = opened(file);
        write(file, "not < well & formed", 0);
        assertTrue(doc.hasChangedOnDisk());

        assertThrows(Exception.class, doc::load, "malformed content still throws");

        assertFalse(doc.hasChangedOnDisk(),
                "but the text was read, so we are in step — otherwise the change "
                        + "stays pending and the next save overwrites it");
    }
}
