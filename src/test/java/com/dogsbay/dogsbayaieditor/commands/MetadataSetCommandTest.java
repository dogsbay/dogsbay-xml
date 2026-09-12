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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.MetadataChange;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/** M4: metadata_set bulk over the headless executor. */
class MetadataSetCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void topic(String name, String prolog) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "<task id=\"t\"><title>T</title>" + prolog + "<taskbody/></task>");
    }

    private void glossentry(String name) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "<glossentry id=\"g\"><glossterm>Clipping</glossterm>"
                + "<glossdef>Distortion.</glossdef></glossentry>");
    }

    @Test
    void bulkFillAddsOnlyWhereMissing() throws Exception {
        topic("topics/a.dita", ""); // no prolog → gets audience
        topic("topics/b.dita",
                "<prolog><metadata><audience type=\"user\"/></metadata></prolog>"); // has it → skip

        BatchResult<MetadataChange> r = exec.execute(new MetadataSetCommand(dir.toString(),
                "root", List.of(new MetadataSetSpec("audience", "administrator", "fill")), false));

        assertThat(r.failed()).isEqualTo(1);  // one file changed
        assertThat(r.passed()).isEqualTo(1);  // one already had it
        assertThat(Files.readString(dir.resolve("topics/a.dita"))).contains("administrator");
        assertThat(Files.readString(dir.resolve("topics/b.dita")))
            .contains("type=\"user\"").doesNotContain("administrator");
    }

    @Test
    void dryRunReportsChangesButWritesNothing() throws Exception {
        topic("topics/a.dita", "");
        String before = Files.readString(dir.resolve("topics/a.dita"));

        BatchResult<MetadataChange> r = exec.execute(new MetadataSetCommand(dir.toString(),
                "root", List.of(new MetadataSetSpec("audience", "administrator", "fill")), true));

        assertThat(r.failed()).isEqualTo(1); // would change
        assertThat(r.findings()).anyMatch(c -> c.field().equals("audience")
                && c.mode().equals("fill") && c.value().equals("administrator"));
        assertThat(Files.readString(dir.resolve("topics/a.dita"))).isEqualTo(before);
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        topic("topics/a.dita", "");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                exec.execute(new MetadataSetCommand(dir.toString(), "root",
                        List.of(new MetadataSetSpec("bogus", "x", "fill")), false)));
    }

    /**
     * A1: a glossentry's content model is {@code (glossterm, glossdef?, prolog?, …)},
     * so an inserted {@code <prolog>} must follow {@code <glossdef>}. The generic
     * topic order placed it before glossdef, producing an invalid glossentry.
     */
    @Test
    void glossentryPrologInsertedAfterGlossdef() throws Exception {
        glossentry("topics/g.dita");

        BatchResult<MetadataChange> r = exec.execute(new MetadataSetCommand(dir.toString(),
                "root", List.of(new MetadataSetSpec("created", "2024-06-01", "fill")), false));

        assertThat(r.failed()).isEqualTo(1); // one file changed
        String out = Files.readString(dir.resolve("topics/g.dita"));
        assertThat(out).contains("2024-06-01");
        // The fix: prolog comes AFTER glossdef (valid), not before it.
        assertThat(out.indexOf("<glossdef")).isLessThan(out.indexOf("<prolog"));
    }

    /**
     * metadata_set is topics (.dita) only — a {@code .ditamap} must be left untouched. Maps
     * have a different metadata model and stamping key-definition maps is rarely intended,
     * so the tool skips them (matching its documented contract).
     */
    @Test
    void mapFilesAreSkipped() throws Exception {
        Path p = dir.resolve("root.ditamap");
        Files.createDirectories(p.getParent());
        String original = "<map><title>R</title><topicref href=\"t.dita\"/></map>";
        Files.writeString(p, original);

        BatchResult<MetadataChange> r = exec.execute(new MetadataSetCommand(dir.toString(),
                "glob:root.ditamap",
                List.of(new MetadataSetSpec("keyword", "audio editing", "append")), false));

        assertThat(r.failed()).isEqualTo(0);                  // nothing changed
        assertThat(Files.readString(p)).isEqualTo(original);  // byte-identical
    }
}
