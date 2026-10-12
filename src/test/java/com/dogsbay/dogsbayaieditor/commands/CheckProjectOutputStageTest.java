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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;

/**
 * The third stage on its own: reading what a build wrote.
 *
 * <p>Driven with fabricated {@link DeliverableBuild}s pointing at folders written by hand, because the branches that
 * matter most here — output that is broken, output that was never written, output with no pages in it — are the ones a
 * real build will not produce on demand. A project whose deliverable produces a PDF and one whose build wrote nothing
 * both yield no broken links, and the whole point is that neither of them is "clean".
 */
class CheckProjectOutputStageTest {

    @TempDir Path dir;

    private DeliverableBuild built(String name, Path out) {
        return new DeliverableBuild(name, "html5", out == null ? null : out.toString(), true, List.of());
    }

    private ProjectCheck stage(DeliverableBuild... builds) {
        List<String> stages = new ArrayList<>(List.of("health", "build"));
        try {
            return HeadlessExecutor.checkBuiltOutput(null, List.of(builds), stages, dir);
        } catch (CommandException e) {
            throw new AssertionError(e);
        }
    }

    private Path outputWith(String name, String html) throws Exception {
        Path out = Files.createDirectories(dir.resolve(name));
        Files.writeString(out.resolve("index.html"), html);
        return out;
    }

    @Test
    void aBrokenLinkInTheOutputStopsTheCheck() throws Exception {
        ProjectCheck check = stage(built("full", outputWith("full", "<a href=\"gone.html\">Gone</a>")));

        assertThat(check.stoppedAt()).isEqualTo("output");
        assertThat(check.isClean()).isFalse();
        assertThat(check.brokenOutputLinks()).singleElement()
                .satisfies(ref -> assertThat(ref.value()).isEqualTo("gone.html"));
        assertThat(check.outputsChecked()).containsExactly("full");
    }

    @Test
    void outputThatHoldsTogetherIsClean() throws Exception {
        Path out = outputWith("full", "<a href=\"t.html\">T</a>");
        Files.writeString(out.resolve("t.html"), "<p>T</p>");

        ProjectCheck check = stage(built("full", out));

        assertThat(check.isClean()).isTrue();
        assertThat(check.ran("output")).isTrue();
        assertThat(check.outputsChecked()).containsExactly("full");
        assertThat(check.outputsSkipped()).isEmpty();
    }

    @Test
    void aDeliverableWithNoPagesIsStillCheckedForHavingProducedSomething() throws Exception {
        // A PDF deliverable: the folder is there, the PDF is there, and there is not a link in it anyone can follow.
        // "No pages to read" used to end there, which said nothing about whether the PDF existed.
        Path out = Files.createDirectories(dir.resolve("pdf"));
        Files.writeString(out.resolve("guide.pdf"), "%PDF-1.7");

        ProjectCheck check = stage(built("pdf", out));

        assertThat(check.isClean()).isTrue();
        assertThat(check.ran("output")).as("it was examined, so the stage means something").isTrue();
        assertThat(check.outputsSkipped()).containsExactly("pdf");
        assertThat(check.outputsChecked()).as("nothing to link-check").isEmpty();
        assertThat(check.reason()).contains("no pages to check links in pdf");
    }

    @Test
    void aDeliverableThatWroteNothingFailsTheCheck() throws Exception {
        // A build can report success and write nothing — a filter that excluded everything, a transtype that wrote
        // somewhere else. No other stage would notice.
        Files.createDirectories(dir.resolve("empty"));

        ProjectCheck check = stage(built("pdf", dir.resolve("empty")));

        assertThat(check.stoppedAt()).isEqualTo("output");
        assertThat(check.isClean()).isFalse();
        assertThat(check.reason()).contains("pdf").contains("wrote nothing");
    }

    @Test
    void aDeliverableWhoseOnlyFileIsEmptyFailsToo() throws Exception {
        Path out = Files.createDirectories(dir.resolve("pdf"));
        Files.createFile(out.resolve("guide.pdf"));

        ProjectCheck check = stage(built("pdf", out));

        assertThat(check.stoppedAt()).isEqualTo("output");
        assertThat(check.reason()).contains("wrote nothing");
    }

    @Test
    void aBuildWithNoOutputDirectoryAtAllFailsTheCheckAndSaysWhereItLooked() {
        ProjectCheck check = stage(built("full", dir.resolve("never-written")));

        assertThat(check.stoppedAt()).isEqualTo("output");
        // a deliverable reported as having written nothing, when the file is plainly there, is only arguable if
        // the report keeps the folder it checked to itself
        assertThat(check.outputsSkipped()).singleElement().asString()
                .startsWith("full").contains("never-written");
    }

    @Test
    void aRelativeOutputFolderIsResolvedAgainstTheProjectRoot() throws Exception {
        // A deliverable's output is resolved against the project file that declares it, and that file is relative
        // when the project was opened by a relative path. Reading it as it stands made the answer depend on where
        // the process was started, and a PDF that was plainly there came back as "wrote nothing".
        Path out = Files.createDirectories(dir.resolve("out/book-pdf"));
        Files.writeString(out.resolve("audacity-book.pdf"), "%PDF-1.7 and 227 KB of it");

        ProjectCheck check = stage(new DeliverableBuild("book-pdf", "pdf", "out/book-pdf", true, List.of()));

        assertThat(check.isClean()).as("the file is there: %s", check.reason()).isTrue();
        assertThat(check.outputsSkipped()).containsExactly("book-pdf");
    }

    @Test
    void aDeliverableWithNoOutputDirectoryAtAllIsSaidSo() {
        ProjectCheck check = stage(built("full", null));

        assertThat(check.stoppedAt()).isEqualTo("output");
        assertThat(check.outputsSkipped()).containsExactly("full");
    }

    @Test
    void aPdfAlongsideHtmlDoesNotStopTheHtmlBeingChecked() throws Exception {
        // the case the documentation team asked about: a book-pdf next to a book-html
        Path pdf = Files.createDirectories(dir.resolve("pdf"));
        Files.writeString(pdf.resolve("guide.pdf"), "%PDF-1.7");
        Path html = outputWith("html", "<a href=\"gone.html\">Gone</a>");

        ProjectCheck check = stage(built("book-pdf", pdf), built("book-html", html));

        assertThat(check.outputsChecked()).containsExactly("book-html");
        assertThat(check.outputsSkipped()).containsExactly("book-pdf");
        assertThat(check.brokenOutputLinks()).singleElement()
                .satisfies(ref -> assertThat(ref.value()).isEqualTo("gone.html"));
        assertThat(check.stoppedAt()).isEqualTo("output");
    }

    @Test
    void eachDeliverableIsCheckedInItsOwnFolder() throws Exception {
        Path ok = outputWith("ok", "<a href=\"t.html\">T</a>");
        Files.writeString(ok.resolve("t.html"), "<p>T</p>");
        Path bad = outputWith("bad", "<a href=\"gone.html\">Gone</a>");

        ProjectCheck check = stage(built("ok", ok), built("bad", bad));

        assertThat(check.outputsChecked()).containsExactly("ok", "bad");
        assertThat(check.brokenOutputLinks()).singleElement()
                .satisfies(ref -> assertThat(ref.source()).contains("bad"));
        assertThat(check.stoppedAt()).isEqualTo("output");
    }

    @Test
    void oneDeliverableWithPagesIsEnoughForTheStageToMeanSomething() throws Exception {
        Path pdf = Files.createDirectories(dir.resolve("pdf"));
        Files.writeString(pdf.resolve("guide.pdf"), "%PDF-1.7");
        Path html = outputWith("html", "<p>Nothing points anywhere.</p>");

        ProjectCheck check = stage(built("pdf", pdf), built("html", html));

        assertThat(check.ran("output")).isTrue();
        assertThat(check.outputsChecked()).containsExactly("html");
        assertThat(check.outputsSkipped()).containsExactly("pdf");
        assertThat(check.isClean()).isTrue();
        assertThat(check.reason()).contains("pdf");
    }
}
