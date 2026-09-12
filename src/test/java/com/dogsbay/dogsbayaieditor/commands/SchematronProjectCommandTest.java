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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

class SchematronProjectCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @BeforeEach
    void schema() throws Exception {
        // ISO Schematron: every <topic> must have a <title>
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
    void unionContextMatchesAllAlternatives() throws Exception { // A10 reproduction
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="p | entry">
                  <report test="contains(., 'X')">hit</report>
                </rule>
              </pattern>
            </schema>
            """);
        write("doc.xml", "<topic><p>X</p><table><row><entry>X</entry></row></table></topic>");

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:doc.xml",
                dir.resolve("rules.sch").toString()));

        // Both the <p> and the <entry> contain 'X' → a union context must fire on both.
        assertThat(r.findings()).hasSize(2);
    }

    @Test
    void reportsFailedAssertions() throws Exception {
        write("good.xml", "<topic><title>Has a title</title></topic>");
        write("bad.xml", "<topic><body/></topic>"); // no title -> fails the rule

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:*.xml",
                dir.resolve("rules.sch").toString()));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.file()).endsWith("bad.xml");
            assertThat(f.message()).contains("must have a title");
        });
    }

    @Test
    void resolvesSvrlLocationToTheElementStartLine() throws Exception {
        // rule fires on <p> (must have @id); the offending <p> starts on line 3
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="p">
                  <assert test="@id">A paragraph needs an id.</assert>
                </rule>
              </pattern>
            </schema>
            """);
        write("doc.xml", "<topic>\n  <body>\n    <p>no id here</p>\n  </body>\n</topic>");

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:doc.xml",
                dir.resolve("rules.sch").toString()));

        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.message()).contains("needs an id");
            assertThat(f.line()).isEqualTo(3); // <p> start tag is on line 3
        });
    }

    @Test
    void reportsFiredReportsNotJustFailedAssertions() throws Exception {
        // A <report> fires when its test is TRUE. Idiomatic Schematron uses these
        // for "flag this pattern", so they must surface like failed assertions.
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="p">
                  <report test="b">Do not use b for emphasis.</report>
                </rule>
              </pattern>
            </schema>
            """);
        write("clean.xml", "<topic><body><p>plain text</p></body></topic>");
        write("bold.xml", "<topic><body><p>see <b>here</b></p></body></topic>");

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:*.xml",
                dir.resolve("rules.sch").toString()));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.file()).endsWith("bold.xml");
            assertThat(f.message()).contains("Do not use b");
            assertThat(f.test()).isEqualTo("b");
        });
    }

    @Test
    void surfacesAssertionsAndReportsTogether() throws Exception {
        // Both a failed <assert> and a fired <report> in one file -> two findings.
        write("rules.sch", """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern>
                <rule context="topic">
                  <assert test="title">A topic must have a title.</assert>
                </rule>
                <rule context="p">
                  <report test="b">Do not use b for emphasis.</report>
                </rule>
              </pattern>
            </schema>
            """);
        write("both.xml", "<topic><body><p>see <b>here</b></p></body></topic>"); // no title + has <b>

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:both.xml",
                dir.resolve("rules.sch").toString()));

        assertThat(r.findings()).hasSize(2);
        assertThat(r.findings()).extracting(SchematronFinding::message)
            .anySatisfy(m -> assertThat(m).contains("must have a title"))
            .anySatisfy(m -> assertThat(m).contains("Do not use b"));
    }

    @Test
    void cleanWhenAllFilesSatisfyTheRules() throws Exception {
        write("a.xml", "<topic><title>A</title></topic>");
        write("b.xml", "<topic><title>B</title></topic>");

        BatchResult<SchematronFinding> r = executor.execute(
            new SchematronProjectCommand(dir.toString(), "glob:*.xml",
                dir.resolve("rules.sch").toString()));

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.isClean()).isTrue();
        assertThat(r.findings()).isEmpty();
    }
}
