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

import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/** P3: validate_conditions + list_subjects across the headless executor. */
class ValidateConditionsCommandTest {

    @TempDir Path dir;

    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    private void writeProject() throws Exception {
        write("conditions.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="os">
                <subjectdef keys="windows"/>
                <subjectdef keys="mac"/>
                <subjectdef keys="linux"/>
              </subjectdef>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keyref="os"/>
              </enumerationdef>
            </subjectScheme>
            """);
        write("root.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map><title>Root</title>
              <mapref href="conditions.ditamap"/>
              <topicref href="intro.dita"/>
            </map>
            """);
        write("intro.dita", """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
            <topic id="intro">
              <title>Intro</title>
              <body>
                <p platform="mac">ok</p>
                <p platform="macos">typo</p>
              </body>
            </topic>
            """);
    }

    @Test
    void flagsBadProfilingValueInTopicWithLineAndSuggestion() throws Exception {
        writeProject();
        BatchResult<ConditionViolation> r = exec.execute(new ValidateConditionsCommand(
                dir.toString(), "map:root.ditamap", null)); // scheme discovered from the map

        assertThat(r.findings()).hasSize(1);
        ConditionViolation v = r.findings().get(0);
        assertThat(v.attribute()).isEqualTo("platform");
        assertThat(v.value()).isEqualTo("macos");
        assertThat(v.suggestion()).isEqualTo("mac");
        assertThat(v.file()).endsWith("intro.dita");
        assertThat(v.line()).isEqualTo(7); // the platform="macos" line
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void explicitSchemeAlsoWorksAndDitavalIsChecked() throws Exception {
        writeProject();
        write("beta.ditaval", """
            <?xml version="1.0" encoding="UTF-8"?>
            <val>
              <prop att="platform" val="mac" action="include"/>
              <prop att="platform" val="windoze" action="exclude"/>
            </val>
            """);
        // scope=root scans intro.dita (macos) AND beta.ditaval (windoze)
        BatchResult<ConditionViolation> r = exec.execute(new ValidateConditionsCommand(
                dir.toString(), "root", dir.resolve("conditions.ditamap").toString()));

        assertThat(r.findings()).extracting(ConditionViolation::value)
            .contains("macos", "windoze");
        assertThat(r.findings()).filteredOn(v -> v.value().equals("windoze"))
            .allSatisfy(v -> {
                assertThat(v.file()).endsWith("beta.ditaval");
                assertThat(v.suggestion()).isEqualTo("windows");
            });
    }

    @Test
    void mapScopeAlsoChecksProjectDitavals() throws Exception {
        // DITAVALs aren't in a map's closure (they're build-time filters), but their
        // <prop val> values are controlled too — a map: scope must still check them.
        writeProject();
        Files.createDirectories(dir.resolve("filters"));
        Files.writeString(dir.resolve("filters/mac.ditaval"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <val>
              <prop att="platform" val="windoze" action="exclude"/>
            </val>
            """);
        BatchResult<ConditionViolation> r = exec.execute(new ValidateConditionsCommand(
                dir.toString(), "map:root.ditamap", null));

        assertThat(r.findings()).extracting(ConditionViolation::value)
            .contains("macos", "windoze"); // topic element + ditaval prop, both checked
        assertThat(r.findings()).filteredOn(v -> v.value().equals("windoze"))
            .allSatisfy(v -> assertThat(v.file()).endsWith("mac.ditaval"));
    }

    @Test
    void aNarrowedScopeIsStillGovernedByTheProjectsScheme() throws Exception {
        writeProject();

        // This asserted the opposite until 2026-09-28: no map in the scope meant an empty scheme and everything
        // passed. That is how `check .` reported a project ready over a platform="macos" — the scope says which
        // files to look at, not whether the project has controlled values.
        BatchResult<ConditionViolation> r = exec.execute(new ValidateConditionsCommand(
                dir.toString(), "glob:*.dita", null));

        assertThat(r.findings()).anySatisfy(v -> assertThat(v.value()).isEqualTo("macos"));
    }

    @Test
    void listSubjectsReturnsTheVocabulary() throws Exception {
        writeProject();
        List<SubjectDefinition> subjects = exec.execute(
                new ListSubjectsCommand(dir.resolve("conditions.ditamap").toString()));

        assertThat(subjects).hasSize(1);
        assertThat(subjects.get(0).attribute()).isEqualTo("platform");
        // "os" is the category the enumerationdef binds, not a value along it
        assertThat(subjects.get(0).values()).containsExactly("linux", "mac", "windows");
    }

    // ── The scheme has to be found without being asked for ────────────────

    @Test
    void aWholeProjectRunFindsTheSchemeWithoutBeingGivenAMap() throws Exception {
        writeProject();

        // `validate-conditions .` and `check .` are how anyone runs this on a project. Resolving the scheme only
        // from a map: scope meant every value passed and the project reported ready — a check that works only when
        // you already know to ask for it is not a check.
        var result = exec.execute(new ValidateConditionsCommand(dir.toString(), "root", null));

        assertThat(result.findings()).isNotEmpty();
        assertThat(result.findings()).anySatisfy(v -> {
            assertThat(v.attribute()).isEqualTo("platform");
            assertThat(v.value()).isEqualTo("macos");
        });
    }

    @Test
    void aSchemeNoMapPullsInStillGoverns() throws Exception {
        writeProject();
        // the scheme sits in the project but nothing references it: it still says what the values are
        Files.delete(dir.resolve("root.ditamap"));

        var result = exec.execute(new ValidateConditionsCommand(dir.toString(), "root", null));

        assertThat(result.findings()).anySatisfy(v -> assertThat(v.value()).isEqualTo("macos"));
    }

    @Test
    void aProjectWithNoSchemeAtAllReportsNothing() throws Exception {
        writeProject();
        Files.delete(dir.resolve("conditions.ditamap"));

        var result = exec.execute(new ValidateConditionsCommand(dir.toString(), "root", null));

        // no scheme means no controlled values, not that every value is wrong
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void projectHealthCatchesItWithoutAMapArgument() throws Exception {
        writeProject();

        var health = exec.execute(new ProjectHealthCommand(dir.toString(), null, "none",
                java.util.List.of("conditions"), null, false));

        assertThat(health.checked()).contains("conditions");
        assertThat(health.conditions().findings()).anySatisfy(v ->
                assertThat(v.value()).isEqualTo("macos"));
        assertThat(health.isClean()).as("a value the scheme forbids blocks a clean verdict").isFalse();
    }

    @Test
    void anExplicitMapStillWorks() throws Exception {
        writeProject();

        var result = exec.execute(new ValidateConditionsCommand(
                dir.toString(), "map:" + dir.resolve("root.ditamap"), null));

        assertThat(result.findings()).anySatisfy(v -> assertThat(v.value()).isEqualTo("macos"));
    }
}
