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

package com.dogsbay.dogsbayaieditor.plugin.proposals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;

/**
 * Accepting a proposal edited the buffer and recorded the decision in the audit
 * trail, but never wrote the file. The record on disk said "accepted" while the
 * file still carried status="new" and the rev, which is what the Git panel —
 * reading disk — went on reporting. An unsaved decision is also lost outright
 * when the buffer is reloaded, by an agent turn or a branch checkout.
 */
class ProposalSaveTest {

    private static final String MARKED = """
        <?xml version="1.0" encoding="UTF-8"?>
        <topic id="t"><title>T</title><shortdesc status="new" rev="ai:codex">New.</shortdesc>\
        <body><p>Text.</p></body></topic>
        """;

    private static final String ACCEPTED = """
        <?xml version="1.0" encoding="UTF-8"?>
        <topic id="t"><title>T</title><shortdesc>New.</shortdesc>\
        <body><p>Text.</p></body></topic>
        """;

    private static DogsBayDocument openedOn(Path file, String content) throws Exception {
        Files.writeString(file, content);
        DogsBayDocument doc = new DogsBayDocument(file.toUri().toURL());
        doc.load();
        return doc;
    }

    @Test
    @DisplayName("a decided document reaches the disk")
    void theDecisionIsWritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.dita");
        DogsBayDocument doc = openedOn(file, MARKED);
        doc.setText(ACCEPTED);
        AtomicBoolean marked = new AtomicBoolean();

        String failed = ProposalsPlugin.save(doc, () -> { }, () -> marked.set(true));

        assertThat(failed).isNull();
        assertThat(Files.readString(file)).doesNotContain("status=\"new\"").doesNotContain("rev=");
        assertThat(marked).isTrue();   // or the tab keeps claiming unsaved changes
    }

    @Test
    @DisplayName("the view's text is pushed into the model before it is written")
    void theBufferIsFlushedFirst(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.dita");
        DogsBayDocument doc = openedOn(file, MARKED);
        AtomicBoolean flushed = new AtomicBoolean();

        // save() writes the model, and the accepted text lives in the view's
        // buffer until something pushes it across.
        ProposalsPlugin.save(doc, () -> {
            flushed.set(true);
            try {
                doc.setText(ACCEPTED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, () -> { });

        assertThat(flushed).isTrue();
        assertThat(Files.readString(file)).doesNotContain("status=\"new\"");
    }

    @Test
    @DisplayName("a read-only document says so rather than failing silently")
    void readOnlyIsReported(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.dita");
        DogsBayDocument doc = openedOn(file, MARKED);
        // Read-only is the file's own permission, not a flag on the document.
        assertThat(file.toFile().setWritable(false)).isTrue();

        String failed = ProposalsPlugin.save(doc, () -> { }, () -> { });

        assertThat(failed).contains("read-only");
        assertThat(Files.readString(file)).contains("status=\"new\"");
        file.toFile().setWritable(true);   // so @TempDir can clean up
    }

    @Test
    @DisplayName("an untitled document is not an error, it just has nowhere to go")
    void anUntitledDocumentIsLeftAlone() {
        assertThat(ProposalsPlugin.save(null, () -> { }, () -> { })).isNull();
    }

    @Test
    @DisplayName("a failure to write is reported, not swallowed")
    void aFailedWriteIsReported(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.dita");
        DogsBayDocument doc = openedOn(file, MARKED);
        AtomicBoolean marked = new AtomicBoolean();

        String failed = ProposalsPlugin.save(doc, () -> {
            throw new IllegalStateException("the view's edits could not be applied");
        }, () -> marked.set(true));

        assertThat(failed).contains("could not be saved");
        assertThat(failed).contains("the view's edits could not be applied");
        assertThat(marked).isFalse();   // never claim saved when nothing was written
    }
}
