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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;

/**
 * check: health, then the build, then the built output — each stage only when the one before it passed.
 *
 * <p>Every test here works on an unhealthy project or asks for no build, so none of them needs DITA-OT installed. That
 * is not a limitation of the tests: stopping before the build is the behaviour being checked, and a test that reached
 * DITA-OT would be proving the opposite.
 */
class CheckProjectCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    /** A project with a topic whose href points at nothing. */
    private void brokenProject() throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><conbody>"
            + "<p><xref href=\"gone.dita\">Gone</xref></p></conbody></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("project.json"),
            "{ \"deliverables\": [ { \"name\": \"full\", "
            + "\"context\": { \"input\": \"guide.ditamap\" }, "
            + "\"publication\": { \"transtype\": \"html5\" } } ] }");
    }

    private ProjectCheck check(boolean build) throws Exception {
        return exec.execute(new CheckProjectCommand(dir.toString(), null, null, null, null, null, build));
    }

    @Test
    void aProjectWithABrokenReferenceStopsAtHealth() throws Exception {
        brokenProject();

        ProjectCheck check = check(true);

        assertThat(check.stoppedAt()).isEqualTo("health");
        assertThat(check.isClean()).isFalse();
        assertThat(check.health().reuse().brokenReferences()).isNotEmpty();
    }

    @Test
    void aStageThatDidNotRunSaysNothing() throws Exception {
        brokenProject();

        ProjectCheck check = check(true);

        // not "clean" — not looked at
        assertThat(check.ran("health")).isTrue();
        assertThat(check.ran("build")).isFalse();
        assertThat(check.ran("output")).isFalse();
        assertThat(check.builds()).isEmpty();
        assertThat(check.brokenOutputLinks()).isEmpty();
    }

    @Test
    void anUnhealthyProjectNeverReachesTheBuild() throws Exception {
        brokenProject();
        // No DITA-OT: reaching the build stage would throw, so a plain result is proof it stopped before it
        String previous = System.getProperty(DitaOtHome.HOME_PROPERTY);
        System.setProperty(DitaOtHome.HOME_PROPERTY,
                Files.createDirectories(dir.resolve("no-frameworks")).toString());
        try {
            assertThat(check(true).stoppedAt()).isEqualTo("health");
        } finally {
            if (previous == null) {
                System.clearProperty(DitaOtHome.HOME_PROPERTY);
            } else {
                System.setProperty(DitaOtHome.HOME_PROPERTY, previous);
            }
        }
    }

    @Test
    void withoutABuildTheCheckStopsAtHealthAndSaysSo() throws Exception {
        brokenProject();
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc>"
            + "<conbody><p>Nothing points anywhere.</p></conbody></concept>");

        ProjectCheck check = check(false);

        assertThat(check.health().isClean()).isTrue();
        assertThat(check.isClean()).isTrue();
        assertThat(check.ran("build")).isFalse();
        assertThat(check.ran("output")).isFalse();
    }

    /**
     * The whole thing, with a real DITA-OT: health, a build, and the output check over what the build wrote.
     *
     * <p>Opt-in ({@code -Ddita.ot.e2e=1}) because it needs the bundled engine and runs a real build — three seconds for
     * this one-topic project, minutes for a real one — and because the point of the other tests here is that this
     * path is not reached. Run it when the composition changes: it is the only test that proves the output check
     * is pointed at the directory the build actually used, which is the one thing the stages cannot fake between them.
     */
    @Test
    @EnabledIfSystemProperty(named = "dita.ot.e2e", matches = ".+")
    void aHealthyProjectBuildsAndItsOutputIsChecked() throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc>"
            + "<conbody><p>Nothing points anywhere.</p></conbody></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("project.json"),
            "{ \"deliverables\": [ { \"name\": \"full\", "
            + "\"context\": { \"input\": \"guide.ditamap\" }, "
            + "\"publication\": { \"transtype\": \"html5\" } } ] }");

        ProjectCheck check = check(true);

        assertThat(check.ran("output")).as("every stage should have run: %s", check.stages()).isTrue();
        assertThat(check.builds()).isNotEmpty();
        assertThat(check.isClean()).as("stopped at %s", check.stoppedAt()).isTrue();
        // the output check looked at what the build wrote, not at a directory it guessed
        assertThat(Path.of(check.builds().get(0).outputDir())).isDirectory();
    }

    @Test
    void aProjectWithNoDeliverablesYetIsUnfinishedRatherThanFailed() throws Exception {
        // A project that has not declared a deliverable is the state every project is in for its first few edits.
        // Calling that "not ready" made every early lesson explain the message away. It is not ready either: the
        // build stage is dropped, so nothing in the report speaks for a build or an output.
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc>"
            + "<conbody><p>Nothing points anywhere.</p></conbody></concept>");
        Files.writeString(dir.resolve("guide.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Guide</title><topicref href=\"a.dita\"/></map>");

        ProjectCheck check = check(true);

        assertThat(check.stoppedAt()).as("nothing failed").isNull();
        assertThat(check.reason()).contains("no deliverables yet");
        assertThat(check.ran("build")).as("there was nothing to build, so the stage says nothing").isFalse();
        assertThat(check.ran("output")).isFalse();
        assertThat(check.outputsChecked()).isEmpty();
    }

    /**
     * The bundled DITA-OT builds a PDF, in process.
     *
     * <p>It could not: the PDF plugin was trimmed out of the bundle, and the editor's own FOP was 0.20.5 from 2003,
     * whose API modern DITA-OT cannot call. Both are fixed — the plugin is in the bundle without its copy of FOP,
     * and FOP 2.11 is on the editor's classpath, where the in-process toolkit finds it like Saxon and Xerces.
     *
     * <p>A PDF deliverable has no pages for the output stage to read, so the check finishes clean while saying the
     * output was not read — which is the honest answer about a PDF.
     */
    @Test
    @EnabledIfSystemProperty(named = "dita.ot.e2e", matches = ".+")
    void aPdfDeliverableBuilds() throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc><conbody><p>Hello.</p></conbody></concept>");
        Files.writeString(dir.resolve("book.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Book</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("project.json"),
            "{ \"deliverables\": [ { \"name\": \"book-pdf\", "
            + "\"context\": { \"input\": \"book.ditamap\" }, "
            + "\"publication\": { \"transtype\": \"pdf\" } } ] }");

        ProjectCheck check = check(true);

        assertThat(check.isClean()).as("stopped at %s", check.stoppedAt()).isTrue();
        assertThat(check.builds()).singleElement().satisfies(built ->
                assertThat(built.success()).as("%s", built.messages()).isTrue());
        assertThat(check.outputsSkipped()).containsExactly("book-pdf");

        Path pdf = Path.of(check.builds().get(0).outputDir()).resolve("book.pdf");
        assertThat(pdf).exists();
        assertThat(Files.readAllBytes(pdf)).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /**
     * And an output type that really is absent still says so, with the ones that are there.
     */
    @Test
    @EnabledIfSystemProperty(named = "dita.ot.e2e", matches = ".+")
    void anOutputTypeTheBundledEngineDoesNotHaveSaysSo() throws Exception {
        Files.writeString(dir.resolve("a.dita"),
            "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
            + "<concept id=\"a\"><title>A</title><shortdesc>A</shortdesc><conbody><p>Hello.</p></conbody></concept>");
        Files.writeString(dir.resolve("book.ditamap"),
            "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
            + "<map><title>Book</title><topicref href=\"a.dita\"/></map>");
        Files.writeString(dir.resolve("project.json"),
            "{ \"deliverables\": [ { \"name\": \"book-troff\", "
            + "\"context\": { \"input\": \"book.ditamap\" }, "
            + "\"publication\": { \"transtype\": \"troff\" } } ] }");

        ProjectCheck check = check(true);

        assertThat(check.stoppedAt()).isEqualTo("build");
        assertThat(check.builds()).singleElement().satisfies(built -> {
            assertThat(built.success()).isFalse();
            assertThat(built.messages().toString())
                    .contains("cannot build 'troff'")
                    .contains("Available here")
                    .contains("pdf"); // and what it can build
        });
    }

    @Test
    void theStagesAreNamedInTheOrderTheyRun() {
        assertThat(ProjectCheck.STAGES).containsExactly("health", "build", "output");
    }

    @Test
    void aReportOfNothingIsNotAReportThatEverythingIsFine() {
        ProjectCheck nothing = new ProjectCheck(null, null, null, "health", null);

        assertThat(nothing.isClean()).isFalse();
        assertThat(nothing.ran("build")).isFalse();
        assertThat(nothing.brokenOutputLinks()).isEmpty();
        assertThat(nothing.outputsChecked()).isEmpty();
    }

    @Test
    void askingAboutAStageThatDoesNotExistIsAMistakeRatherThanAFalseNo() {
        ProjectCheck check = new ProjectCheck(null, null, List.of("health"), null, null);

        assertThatThrownBy(() -> check.ran("ouput"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a stage");
    }
}
