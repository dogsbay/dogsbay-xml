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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;

class ProjectHealthCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();

    private void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void cleanProjectIsHealthy(@TempDir Path dir) throws Exception {
        write(dir, "ok.xml", "<root><a/></root>");

        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), null));

        assertThat(r.isClean()).isTrue();
        assertThat(r.validation().failed()).isZero();
        assertThat(r.reuse().isClean()).isTrue();
    }

    @Test
    void invalidFileMakesHealthUnclean(@TempDir Path dir) throws Exception {
        write(dir, "bad.xml", "<root><a></root>"); // not well-formed

        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), null));

        assertThat(r.isClean()).isFalse();
        assertThat(r.validation().failed()).isEqualTo(1);
        // reuse alone would have reported clean — the validation gap is what the
        // comprehensive health closes.
        assertThat(r.reuse().isClean()).isTrue();
    }

    @Test
    void brokenReferenceMakesHealthUnclean(@TempDir Path dir) throws Exception {
        write(dir, "a.dita",
            "<topic id=\"a\"><title>A</title><body><p><xref href=\"missing.dita\"/></p></body></topic>");

        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), null));

        assertThat(r.isClean()).isFalse();
        assertThat(r.reuse().brokenReferences()).isNotEmpty();
    }

    @Test
    void blankRootMapTreatedAsAbsentNotAMissingFile(@TempDir Path dir) throws Exception {
        write(dir, "ok.xml", "<root/>");
        // an empty-string rootMap must mean "no map", not existingFile("") -> throw
        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), ""));
        assertThat(r.isClean()).isTrue();
    }

    @Test
    void brokenConrefElementIdMakesHealthUnclean(@TempDir Path dir) throws Exception {
        // target file exists, but the conref points at a missing element id —
        // the gap reuse-health + validation both miss, now folded into health.
        write(dir, "common.dita",
            "<topic id=\"c\"><title>C</title><body><p id=\"good\">x</p></body></topic>");
        write(dir, "a.dita",
            "<topic id=\"a\"><title>A</title><body><p conref=\"common.dita#c/badid\"/></body></topic>");

        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), null));

        assertThat(r.isClean()).isFalse();
        assertThat(r.brokenElementIds()).isNotEmpty();
    }

    /**
     * A5: a subjectScheme's enumerationdef binds an attribute to its subject
     * hierarchy via {@code <subjectdef keyref="...">}. That is a controlled-value
     * reference, not a content key reference, so it must not be reported as an
     * undefined key — even when the scheme isn't wired into the root map.
     */
    @Test
    void subjectSchemeEnumerationKeyrefsAreNotUndefinedKeys(@TempDir Path dir) throws Exception {
        write(dir, "root.ditamap",
            "<map><title>R</title><topicref href=\"t.dita\"/></map>");
        write(dir, "t.dita",
            "<topic id=\"t\"><title>T</title><body><p>x</p></body></topic>");
        write(dir, "controlled-values.ditamap",
            "<subjectScheme><enumerationdef><attributedef name=\"platform\"/>"
            + "<subjectdef keyref=\"platform\"/></enumerationdef>"
            + "<subjectdef keys=\"platform\"><subjectdef keys=\"windows\"/></subjectdef>"
            + "</subjectScheme>");

        ProjectHealthReport r = executor.execute(new ProjectHealthCommand(
            dir.toString(), dir.resolve("root.ditamap").toString()));

        assertThat(r.reuse().undefinedKeys())
            .noneMatch(k -> "platform".equals(k.value()));
    }

    /**
     * A5 (unused side): wiring a subjectScheme into the root map brings its
     * controlled-value subjectdef keys into the key space. Those are a governed
     * vocabulary, not content keys an author keyrefs — so they must NOT be
     * reported as unused keys (which would otherwise fail the health gate).
     */
    @Test
    void wiredSubjectSchemeKeysAreNotReportedUnused(@TempDir Path dir) throws Exception {
        write(dir, "root.ditamap",
            "<map><title>R</title>"
            + "<mapref href=\"controlled-values.ditamap\"/>"
            + "<topicref href=\"t.dita\"/></map>");
        write(dir, "t.dita",
            "<topic id=\"t\"><title>T</title><body><p platform=\"windows\">x</p></body></topic>");
        write(dir, "controlled-values.ditamap",
            "<subjectScheme><subjectdef keys=\"platform\">"
            + "<subjectdef keys=\"windows\"/><subjectdef keys=\"mac\"/>"
            + "<subjectdef keys=\"linux\"/></subjectdef>"
            + "<enumerationdef><attributedef name=\"platform\"/>"
            + "<subjectdef keyref=\"platform\"/></enumerationdef></subjectScheme>");

        ProjectHealthReport r = executor.execute(new ProjectHealthCommand(
            dir.toString(), dir.resolve("root.ditamap").toString()));

        assertThat(r.reuse().unusedKeys())
            .noneMatch(k -> java.util.List.of("platform", "windows", "mac", "linux")
                .contains(k.name()));
    }

    // ── the --schematron leg ────────────────────────────────────────────
    //
    // House rules ("every topic needs a shortdesc") are Schematron, not
    // grammar, so before the option a project whose own .sch was failing was
    // reported publish-ready.

    private static final String SHORTDESC_RULE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://purl.oclc.org/dsdl/schematron">
          <pattern id="shortdesc">
            <rule context="topic">
              <assert test="shortdesc">Every topic needs a shortdesc.</assert>
            </rule>
          </pattern>
        </schema>
        """;

    @Test
    void withoutTheSchemaHouseRulesAreNotChecked(@TempDir Path dir) throws Exception {
        write(dir, "no-shortdesc.xml", "<topic id=\"t\"><title>T</title><body/></topic>");
        write(dir, "house.sch", SHORTDESC_RULE);

        ProjectHealthReport r =
            executor.execute(new ProjectHealthCommand(dir.toString(), null));

        assertThat(r.schematron().findings()).isEmpty();
        assertThat(r.isClean()).isTrue();
    }

    @Test
    void aFailingHouseRuleMakesHealthUnclean(@TempDir Path dir) throws Exception {
        write(dir, "no-shortdesc.xml", "<topic id=\"t\"><title>T</title><body/></topic>");
        write(dir, "house.sch", SHORTDESC_RULE);

        ProjectHealthReport r = executor.execute(new ProjectHealthCommand(
            dir.toString(), null, dir.resolve("house.sch").toString()));

        assertThat(r.schematron().findings())
            .extracting("message")
            .containsExactly("Every topic needs a shortdesc.");
        assertThat(r.schematron().findings().get(0).file()).endsWith("no-shortdesc.xml");
        // Nothing else is wrong with this project: the house rule alone is what
        // makes it unclean, which is the whole point of the option.
        assertThat(r.reuse().isClean()).isTrue();
        assertThat(r.validation().isClean()).isTrue();
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void aProjectThatKeepsItsHouseRulesStaysClean(@TempDir Path dir) throws Exception {
        write(dir, "good.xml",
            "<topic id=\"t\"><title>T</title><shortdesc>What it is.</shortdesc><body/></topic>");
        write(dir, "house.sch", SHORTDESC_RULE);

        ProjectHealthReport r = executor.execute(new ProjectHealthCommand(
            dir.toString(), null, dir.resolve("house.sch").toString()));

        assertThat(r.schematron().findings()).isEmpty();
        assertThat(r.isClean()).isTrue();
    }
}
