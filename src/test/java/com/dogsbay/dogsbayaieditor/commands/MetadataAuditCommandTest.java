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

import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataField;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule.Presence;
import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/** M3: metadata_audit over the headless executor. */
class MetadataAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    /** Tasks need a created date (ISO); concept audience ∈ {admin,user}; ref forbids author. */
    private void writePolicy() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.setMetadataPolicy(new MetadataPolicy(List.of(
                new MetadataRule("task", MetadataField.CREATED, Presence.REQUIRED,
                        List.of(), "\\d{4}-\\d\\d-\\d\\d"),
                new MetadataRule("concept", MetadataField.AUDIENCE, Presence.REQUIRED,
                        List.of("administrator", "user"), null),
                new MetadataRule("reference", MetadataField.AUTHOR, Presence.FORBIDDEN,
                        List.of(), null),
                new MetadataRule(null, MetadataField.KEYWORD, Presence.RECOMMENDED,
                        List.of(), null))));
        c.saveShared(dir);
    }

    private BatchResult<MetadataFinding> audit() throws Exception {
        return exec.execute(new MetadataAuditCommand(dir.toString(), "root", null));
    }

    @Test
    void flagsMissingRequiredForbiddenAndBadValue() throws Exception {
        writePolicy();
        write("task-bad.dita", """
            <task id="t"><title>T</title><taskbody/></task>
            """); // missing required created, missing recommended keyword
        write("concept-bad.dita", """
            <concept id="c"><title>C</title>
              <prolog><metadata><audience type="wizard"/></metadata></prolog><conbody/></concept>
            """); // audience present but not in {administrator,user}; keyword missing
        write("ref-bad.dita", """
            <reference id="r"><title>R</title>
              <prolog><author>Jane</author></prolog><refbody/></reference>
            """); // forbidden author present; keyword missing

        BatchResult<MetadataFinding> r = audit();
        assertThat(r.isClean()).isFalse();
        assertThat(r.findings()).anyMatch(f -> f.field().equals("created")
                && f.severity().equals("error") && f.message().contains("missing required"));
        assertThat(r.findings()).anyMatch(f -> f.field().equals("audience")
                && f.message().contains("wizard") && f.severity().equals("error"));
        assertThat(r.findings()).anyMatch(f -> f.field().equals("author")
                && f.message().contains("not allowed") && f.severity().equals("error"));
        assertThat(r.findings()).anyMatch(f -> f.field().equals("keyword")
                && f.severity().equals("warning")); // recommended → warning
    }

    @Test
    void findingsCarrySourceLinesNotMinusOne() throws Exception {
        writePolicy();
        // out-of-vocabulary audience on its own line; missing required created (task)
        write("concept-bad.dita", """
            <concept id="c"><title>C</title>
              <prolog><metadata>
                <audience type="wizard"/>
              </metadata></prolog><conbody/></concept>
            """);
        write("task-bad.dita", "<task id=\"t\"><title>T</title><taskbody/></task>");

        BatchResult<MetadataFinding> r = audit();
        // present violation → the offending element's line
        assertThat(r.findings()).filteredOn(f -> f.field().equals("audience"))
            .isNotEmpty().allSatisfy(f -> assertThat(f.line()).isGreaterThan(0));
        // missing-required → the prolog/root anchor line (still a real line, not -1)
        assertThat(r.findings()).filteredOn(f -> f.field().equals("created"))
            .isNotEmpty().allSatisfy(f -> assertThat(f.line()).isGreaterThan(0));
    }

    @Test
    void cleanProjectAuditsClean() throws Exception {
        writePolicy();
        write("task-ok.dita", """
            <task id="t"><title>T</title>
              <prolog>
                <critdates><created date="2025-01-01"/></critdates>
                <metadata><keywords><keyword>setup</keyword></keywords></metadata>
              </prolog><taskbody/></task>
            """);
        BatchResult<MetadataFinding> r = audit();
        assertThat(r.isClean()).isTrue();
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void patternMismatchIsFlagged() throws Exception {
        writePolicy();
        write("task-baddate.dita", """
            <task id="t"><title>T</title>
              <prolog><critdates><created date="Jan 2025"/></critdates>
              <metadata><keywords><keyword>x</keyword></keywords></metadata></prolog><taskbody/></task>
            """);
        BatchResult<MetadataFinding> r = audit();
        assertThat(r.findings()).anyMatch(f -> f.field().equals("created")
                && f.message().contains("does not match"));
    }

    @Test
    void projectHealthFoldsInTheMetadataPolicy() throws Exception {
        writePolicy();
        write("task-bad.dita", "<task id=\"t\"><title>T</title><taskbody/></task>");
        var health = exec.execute(new ProjectHealthCommand(dir.toString(), null));
        assertThat(health.metadata().findings()).anyMatch(f -> f.field().equals("created"));
        assertThat(health.isClean()).isFalse(); // required metadata missing → not done
    }

    @Test
    void anyTopicRuleDoesNotFireOnMaps() throws Exception { // B5
        // A required <keyword> with no topic-type means "every topic". A ditamap is
        // not a topic (it carries publication metadata, not topic content keywords),
        // so the rule must flag the topic and leave the keydef map alone.
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.setMetadataPolicy(new MetadataPolicy(List.of(
                new MetadataRule(null, MetadataField.KEYWORD, Presence.REQUIRED,
                        List.of(), null))));
        c.saveShared(dir);
        write("topic-nokeyword.dita", "<concept id=\"c\"><title>C</title><conbody/></concept>");
        write("keydefs.ditamap",
                "<map><title>Keys</title><keydef keys=\"k\"><topicmeta>"
                + "<keyword>v</keyword></topicmeta></keydef></map>");

        BatchResult<MetadataFinding> r = audit();
        assertThat(r.findings()).anyMatch(f -> f.field().equals("keyword")
                && f.file().endsWith("topic-nokeyword.dita"));
        assertThat(r.findings()).noneMatch(f -> f.file().endsWith("keydefs.ditamap"));
    }

    @Test
    void noPolicyAuditsCleanRegardless() throws Exception {
        // no .dogsbay policy written
        write("task.dita", "<task id=\"t\"><title>T</title><taskbody/></task>");
        BatchResult<MetadataFinding> r = audit();
        assertThat(r.isClean()).isTrue();
    }
}
