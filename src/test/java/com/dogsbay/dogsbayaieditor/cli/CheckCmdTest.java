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
 * `check` is what replaces a hand-written CI script, so what it prints and what it exits with are the whole contract.
 */
class CheckCmdTest {

    @TempDir Path dir;

    private record Run(int code, String out) {
    }

    private Run run(String... args) {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int code = new CommandLine(new CheckCmd()).execute(args);
            return new Run(code, captured.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(previous);
        }
    }

    private void topic(String body) throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc><conbody>" + body + "</conbody></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");
    }

    @Test
    void aBrokenProjectExitsOneAndSaysWhatFailed() throws Exception {
        topic("<p><xref href=\"gone.dita\">Gone</xref></p>");

        Run run = run(dir.toString());

        assertThat(run.code()).isEqualTo(1);
        assertThat(run.out()).contains("health   NOT CLEAN")
                .contains("gone.dita")
                // the verdict names the fault, not the stage: "stopped at health" told nobody anything
                .contains("Not ready: the project itself has faults")
                .contains("The build and the built output were not checked");
    }

    @Test
    void withoutABuildACleanProjectSaysTheOutputWasNotChecked() throws Exception {
        topic("<p>Nothing points anywhere.</p>");

        Run run = run(dir.toString(), "--no-build");

        assertThat(run.code()).isZero();
        assertThat(run.out()).contains("health   clean")
                .contains("the build was not asked for");
        // it must not claim the output is fine when it never looked
        assertThat(run.out()).doesNotContain("output   clean");
    }

    @Test
    void anOrphanTopicIsNamedEvenThoughTheProjectIsReady() throws Exception {
        topic("<p>Nothing points anywhere.</p>");
        Files.writeString(dir.resolve("forgotten.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"forgotten\"><title>Forgotten</title><shortdesc>F</shortdesc><conbody/></concept>");

        Run run = run(dir.toString(), "--no-build");

        // The commonest mistake in the tutorial: a topic written and never added to the map. It is valid, nothing
        // refers to it, and it simply does not appear in the output — so nothing but this would mention it.
        assertThat(run.code()).isZero();
        assertThat(run.out()).contains("health   clean, with warnings")
                .contains("orphan topic").contains("forgotten.dita")
                .contains("1 orphan topic above: worth knowing");
    }

    @Test
    void aProjectWithNothingWorthKnowingSaysNothingExtra() throws Exception {
        topic("<p>Nothing points anywhere.</p>");

        Run run = run(dir.toString(), "--no-build");

        assertThat(run.out()).contains("health   clean").doesNotContain("with warnings")
                .doesNotContain("worth knowing");
    }


    @Test
    void aProjectWithNoDeliverablesYetIsUnfinishedNotFailed() throws Exception {
        topic("<p>Nothing points anywhere.</p>");

        Run run = run(dir.toString());

        // The first few stages of any project are like this. Calling a correct project "not ready" meant every
        // lesson had to explain the message away.
        assertThat(run.code()).isZero();
        assertThat(run.out()).contains("Project health is clean").contains("no deliverables yet");
        // and it still must not claim anything about a build or an output it never did
        assertThat(run.out()).doesNotContain("Ready:").doesNotContain("output   clean");
    }

    @Test
    void anInvalidFileSaysWhyAndNotJustWhere() throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><nonsense/></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");

        Run run = run(dir.toString(), "--no-build");

        assertThat(run.code()).isEqualTo(1);
        assertThat(run.out()).contains("invalid: ").contains("a.dita");
        // naming the file alone left the reader to open another panel to learn anything
        assertThat(run.out()).contains("nonsense");
    }


    /** A project holding two maps, as a tutorial repository holding several stages does. */
    private void twoMaps() throws Exception {
        Files.createDirectories(dir.resolve("images"));
        Files.write(dir.resolve("images/logo.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc><conbody>"
            + "<p>In the map.</p><image href=\"images/logo.png\"><alt>Logo</alt></image>"
            + "</conbody></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("outside.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"outside\"><title>Outside</title><shortdesc>O</shortdesc><conbody>"
            + "<p><xref href=\"gone.dita\">y</xref></p></conbody></concept>");
        Files.writeString(dir.resolve("other.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Other</title><topicref href=\"outside.dita\"/></map>");
    }

    @Test
    void anImageInTheMapIsNotReadAsXml() throws Exception {
        twoMaps();

        Run run = run(dir.toString(), "--map", dir.resolve("guide.ditamap").toString(), "--no-build");

        // A map's reachable files include the images it shows, and a PNG through the validator comes back
        // "Invalid byte 1 of 1-byte UTF-8 sequence" — which is true of every PNG ever made.
        assertThat(run.out()).doesNotContain("logo.png");
        assertThat(run.code()).isZero();
    }

    @Test
    void askedAboutOneMapItDoesNotReportAnotherMapsFaults() throws Exception {
        twoMaps();

        Run scoped = run(dir.toString(), "--map", dir.resolve("guide.ditamap").toString(), "--no-build");
        assertThat(scoped.out()).doesNotContain("outside.dita");
        assertThat(scoped.code()).isZero();

        // and the same project, asked about all of it, still sees the fault
        Run whole = run(dir.toString(), "--map", "none", "--no-build");
        assertThat(whole.out()).contains("outside.dita").contains("gone.dita");
        assertThat(whole.code()).isEqualTo(1);
    }

}
