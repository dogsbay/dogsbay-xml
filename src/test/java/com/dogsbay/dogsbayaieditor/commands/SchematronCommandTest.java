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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Single-document Schematron validation.
 *
 * <p>Added so the legacy Schematron implementation can be retired without losing
 * the one thing it offered that the project-wide command did not: checking the
 * document you are working on. See {@code plans/retire-legacy-schematron.md}.
 *
 * <p>The point of most of these is that a rule must report <em>identically</em>
 * whether it is run over one file or a whole scope — the two commands share an
 * engine, and that equivalence is what makes the removal a pure subtraction.
 */
class SchematronCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    private String schema() {
        return dir.resolve("rules.sch").toString();
    }

    @BeforeEach
    void rules() throws Exception {
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="topic">
                  <assert test="title">A topic must have a title.</assert>
                </rule>
              </pattern>
            </schema>
            """);
    }

    @Test
    void reportsAFailedAssertionInOneDocument() throws Exception {
        write("bad.xml", "<topic><body/></topic>");

        BatchResult<SchematronFinding> r = executor.execute(
                new SchematronCommand(dir.resolve("bad.xml").toString(), schema()));

        assertThat(r.findings()).hasSize(1);
        assertThat(r.findings().get(0).message()).contains("must have a title");
        assertThat(r.total()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void aConformingDocumentIsClean() throws Exception {
        write("good.xml", "<topic><title>T</title></topic>");

        BatchResult<SchematronFinding> r = executor.execute(
                new SchematronCommand(dir.resolve("good.xml").toString(), schema()));

        assertThat(r.findings()).isEmpty();
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.isClean()).isTrue();
    }

    /**
     * Both commands must agree on the same file.
     *
     * <p>Since the refactor there is one engine behind both, so divergence is
     * structurally impossible and this cannot fail on message content — it is a
     * guard against the two paths drifting apart again, plus a real check that a
     * single-file scope resolves to exactly one file.
     */
    @Test
    void agreesWithTheProjectCommandOnTheSameFile() throws Exception {
        write("bad.xml", "<topic><body/></topic>");

        BatchResult<SchematronFinding> single = executor.execute(
                new SchematronCommand(dir.resolve("bad.xml").toString(), schema()));
        BatchResult<SchematronFinding> scoped = executor.execute(
                new SchematronProjectCommand(dir.toString(), "glob:bad.xml", schema()));

        assertThat(single.findings()).hasSameSizeAs(scoped.findings());
        assertThat(single.findings().get(0).message())
                .isEqualTo(scoped.findings().get(0).message());
        assertThat(single.findings().get(0).line())
                .isEqualTo(scoped.findings().get(0).line());
        assertThat(single.findings().get(0).test())
                .isEqualTo(scoped.findings().get(0).test());
    }

    /**
     * Findings carry a source line so the editor can navigate to them.
     *
     * <p>The reported element is deliberately not on line 1: a rule matching the
     * root would be satisfied by any implementation that returned a constant 1 or
     * simply "the first line of the file".
     */
    @Test
    void reportsTheSourceLineOfTheMatchedElement() throws Exception {
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="body">
                  <assert test="p">A body must contain a paragraph.</assert>
                </rule>
              </pattern>
            </schema>
            """);
        // <body> is on line 3 (1-based), with the root and a comment above it.
        write("bad.xml", "<!-- a note -->\n<topic>\n  <body/>\n</topic>");

        BatchResult<SchematronFinding> r = executor.execute(
                new SchematronCommand(dir.resolve("bad.xml").toString(), schema()));

        assertThat(r.findings()).hasSize(1);
        assertThat(r.findings().get(0).line()).isEqualTo(3);
    }

    @Test
    void aMissingDocumentIsRejected() {
        CommandException e = assertThrows(CommandException.class, () -> executor.execute(
                new SchematronCommand(dir.resolve("nope.xml").toString(), schema())));

        assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void aMissingSchemaIsRejected() throws Exception {
        write("good.xml", "<topic><title>T</title></topic>");

        CommandException e = assertThrows(CommandException.class, () -> executor.execute(
                new SchematronCommand(dir.resolve("good.xml").toString(),
                        dir.resolve("nope.sch").toString())));

        assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.FILE_NOT_FOUND);
    }

    /** Malformed XML is reported as a finding, not thrown — one bad file is data. */
    @Test
    void malformedXmlBecomesAFinding() throws Exception {
        write("broken.xml", "<topic><title>unclosed");

        BatchResult<SchematronFinding> r = executor.execute(
                new SchematronCommand(dir.resolve("broken.xml").toString(), schema()));

        assertThat(r.findings()).hasSize(1);
        assertThat(r.findings().get(0).message()).startsWith("could not check:");
        assertThat(r.isClean()).isFalse();
    }
}
