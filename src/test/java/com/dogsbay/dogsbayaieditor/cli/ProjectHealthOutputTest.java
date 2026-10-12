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

package com.dogsbay.dogsbayaieditor.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * A broken project produces hundreds of lines of detail — fifteen files failing
 * one house rule is fifteen lines that say the same thing — so the run ends with
 * the counts, where a terminal leaves you looking, and {@code --summary} prints
 * them alone.
 */
class ProjectHealthOutputTest {

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

    /** Run the subcommand with stdout captured. */
    private static String run(Path dir, String... extraArgs) throws Exception {
        String[] args = new String[extraArgs.length + 1];
        args[0] = dir.toString();
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);

        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            new CommandLine(new ProjectHealthCmd()).execute(args);
        } finally {
            System.setOut(previous);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static Path brokenProject(Path dir) throws Exception {
        for (String name : new String[] {"one.xml", "two.xml", "three.xml"}) {
            Files.writeString(dir.resolve(name), "<topic id=\"t\"><title>T</title><body/></topic>");
        }
        Path schema = dir.resolve("house.sch");
        Files.writeString(schema, SHORTDESC_RULE);
        return schema;
    }

    /** A project whose only fault is a topic nobody put in the map. */
    private static void projectWithAnOrphan(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("topics"));
        Files.writeString(dir.resolve("guide.ditamap"),
                "<map><title>Guide</title><topicref href=\"topics/included.dita\"/></map>");
        for (String name : new String[] { "included", "forgotten" }) {
            Files.writeString(dir.resolve("topics/" + name + ".dita"),
                    "<concept id=\"" + name + "\"><title>" + name + "</title><conbody/></concept>");
        }
    }

    @Test
    void anOrphanTopicIsNamedEvenWhenNothingIsBroken(@TempDir Path dir) throws Exception {
        projectWithAnOrphan(dir);

        String out = run(dir);

        // The summary used to say "no ... orphans" and return before the lines that would have listed them, so a topic
        // left out of the map — the thing people run this to find — was reported as a healthy project.
        assertThat(out).contains("Orphan topics (1");
        assertThat(out).contains("forgotten.dita");
        assertThat(out).doesNotContain("no broken references, keys, orphans");
    }

    @Test
    void anOrphanIsWorthKnowingAndNotAFailure(@TempDir Path dir) throws Exception {
        projectWithAnOrphan(dir);

        String out = run(dir);

        // it is listed and named in the summary, and the project is still valid: whether it should block is a decision
        // for the check that will wrap this, not a change of meaning here
        assertThat(out).contains("Project is valid");
        assertThat(out).contains("1 orphan topic");
        assertThat(out).contains("not treated as failures");
    }

    @Test
    void aProjectWithNothingToSayStillSaysSo(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("topics"));
        Files.writeString(dir.resolve("guide.ditamap"),
                "<map><title>Guide</title><topicref href=\"topics/included.dita\"/></map>");
        Files.writeString(dir.resolve("topics/included.dita"),
                "<concept id=\"included\"><title>Included</title><conbody/></concept>");

        String out = run(dir);

        assertThat(out).contains("Project is healthy");
    }

    @Test
    void theCountsComeLast(@TempDir Path dir) throws Exception {
        Path schema = brokenProject(dir);

        String out = run(dir, "--schematron", schema.toString());

        // The detail is printed by default, and the counts close the run: the
        // end of the output is what a terminal leaves on screen.
        assertThat(out).contains("one.xml").contains("two.xml").contains("three.xml");
        assertThat(out.indexOf("Summary")).isGreaterThan(out.lastIndexOf("one.xml"));
        assertThat(out).contains("House rules");
        assertThat(out).contains("Every topic needs a shortdesc.");
    }

    @Test
    void summaryStopsAtTheCounts(@TempDir Path dir) throws Exception {
        Path schema = brokenProject(dir);

        String out = run(dir, "--schematron", schema.toString(), "--summary");

        assertThat(out).startsWith("Summary");
        assertThat(out).doesNotContain("one.xml");
    }

    /**
     * A capped report keeps the overflow in truncated(), so counting the list
     * would print "200 findings in 250 failing files" — a number that cannot be.
     */
    @Test
    void theCountIsEveryFindingNotJustTheListedOnes(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 260; i++) {
            Files.writeString(dir.resolve("t" + i + ".xml"),
                "<topic id=\"t\"><title>T</title><body/></topic>");
        }
        Path schema = dir.resolve("house.sch");
        Files.writeString(schema, SHORTDESC_RULE);

        String out = run(dir, "--schematron", schema.toString(), "--summary");

        assertThat(out).contains("House rules                 260");
        assertThat(out).doesNotContain("House rules                 200");
        // The tail row holds findings, in a column of findings.
        assertThat(out).contains("… not listed above");
    }

    @Test
    void summaryOmitsOpenProposalDetailToo(@TempDir Path dir) throws Exception {
        Path schema = brokenProject(dir);
        Files.writeString(dir.resolve("proposed.xml"),
            "<topic id=\"p\"><title>T</title><shortdesc>d</shortdesc><body><p>"
            + "<ph status=\"changed\" rev=\"agent\">added</ph>"
            + "<draft-comment>why?</draft-comment></p></body></topic>");

        String out = run(dir, "--schematron", schema.toString(), "--summary");

        assertThat(out).startsWith("Summary");
        assertThat(out).doesNotContain("proposed.xml");
    }

    @Test
    void aHealthyProjectSaysSoWithoutASummaryBlock(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ok.xml"), "<root><a/></root>");

        String out = run(dir);

        assertThat(out).contains("Project is healthy");
        assertThat(out).doesNotContain("Summary");
    }

    /** A project with nothing wrong with it at all. */
    private static void cleanProject(Path dir) throws Exception {
        Files.writeString(dir.resolve("guide.ditamap"),
                "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("a.dita"),
                "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc><conbody/></concept>");
    }

    @Test
    void aRunNarrowedToOneLegDoesNotCallTheProjectValid(@TempDir Path dir) throws Exception {
        cleanProject(dir);

        String out = run(dir, "--include", "reuse");

        // validation never ran, so "valid" would be a claim about something nobody looked at
        assertThat(out).doesNotContain("Project is healthy").doesNotContain("Project is valid");
        assertThat(out).contains("Clean, as far as this run looked: reuse")
                .contains("Nothing here speaks for").contains("validation");
    }

    @Test
    void aFullRunStillSaysTheProjectIsHealthy(@TempDir Path dir) throws Exception {
        cleanProject(dir);

        String out = run(dir);

        assertThat(out).contains("Project is healthy");
    }


    @Test
    void anErrorsOnlyRunDoesNotClaimThereAreNoOrphans(@TempDir Path dir) throws Exception {
        projectWithAnOrphan(dir);

        String out = run(dir, "--severity", "error");

        // --severity error drops orphans before the report is built, so their count is zero whether or not the
        // project has any. This project has one.
        assertThat(out).doesNotContain("no broken references, keys, orphans");
        assertThat(out).contains("No errors").contains("says nothing about orphan topics");
    }

    @Test
    void aRunThatCheckedNothingSaysSoRatherThanSoundingClean(@TempDir Path dir) throws Exception {
        cleanProject(dir);

        // --include names one leg, and --severity error then drops it: the emptiest possible pass
        String out = run(dir, "--include", "format", "--severity", "error");

        assertThat(out).contains("Nothing was checked");
        assertThat(out).doesNotContain("Clean, as far as this run looked: .");
    }


    /** A project whose only fault is a profiling value its subjectScheme does not allow. */
    private static void projectWithABadProfileValue(Path dir) throws Exception {
        Files.writeString(dir.resolve("scheme.ditamap"), """
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="os"><subjectdef keys="mac"/><subjectdef keys="windows"/></subjectdef>
              <enumerationdef><attributedef name="platform"/><subjectdef keyref="os"/></enumerationdef>
            </subjectScheme>
            """);
        Files.writeString(dir.resolve("a.dita"), """
            <!DOCTYPE concept PUBLIC "-//OASIS//DTD DITA Concept//EN" "concept.dtd">
            <concept id="a"><title>A</title><shortdesc>A</shortdesc><conbody>
            <p platform="macos">Only on macOS.</p>
            </conbody></concept>
            """);
        Files.writeString(dir.resolve("guide.ditamap"), """
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map><title>Guide</title><topicref href="a.dita"/></map>
            """);
    }

    @Test
    void theFullReportShowsTheControlledValueThatFailedIt(@TempDir Path dir) throws Exception {
        projectWithABadProfileValue(dir);

        String out = run(dir);

        // check tells people to run this for the full report, and the full report said nothing: it exited 1 over a
        // value it never named, under an empty "Summary".
        assertThat(out).contains("Controlled values (1)")
                .contains("a.dita")
                .contains("@platform=\"macos\"")
                .contains("did you mean");
    }

    @Test
    void theSummaryCountsControlledValues(@TempDir Path dir) throws Exception {
        projectWithABadProfileValue(dir);

        String out = run(dir, "--summary");

        assertThat(out).contains("Summary").contains("Controlled values");
    }

    @Test
    void aProjectWithNoSchemeSaysNothingAboutControlledValues(@TempDir Path dir) throws Exception {
        cleanProject(dir);

        String out = run(dir);

        // no scheme means no controlled values, not a leg reporting zero at everyone
        assertThat(out).doesNotContain("Controlled values");
    }
}
