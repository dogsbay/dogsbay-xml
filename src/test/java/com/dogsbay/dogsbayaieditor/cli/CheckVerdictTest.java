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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;
import com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef;

/**
 * The verdict names what failed, not what was not read.
 *
 * <p>It used to print the report's note, which is about what could not be looked at. A run that stopped on seven
 * broken links in the HTML said "stopped at output — no pages to check links in book-pdf", and the person reading it
 * concluded the PDF was the problem and reported it as a bug. The note still has its place; it is not the verdict.
 */
class CheckVerdictTest {

    private static final BrokenRef LINK = new BrokenRef("/out/a.html", 3, "href", "gone.html", "there is no gone.html");

    private static ProjectCheck stoppedAtOutput(List<BrokenRef> broken, String note) {
        return new ProjectCheck(null, List.of(new DeliverableBuild("book-html", "html5", "/out", true, List.of())),
                broken, List.of("health", "build", "output"), "output", note,
                List.of("book-html"), List.of("book-pdf"), List.of());
    }

    @Test
    void brokenLinksAreNamedAndNothingElseIs() {
        String verdict = CheckCmd.whatFailed(stoppedAtOutput(
                List.of(LINK, LINK, LINK, LINK, LINK, LINK, LINK), "no pages to check links in book-pdf"));

        assertThat(verdict).isEqualTo("7 links in the built output lead nowhere.");
        // the note belongs on the PDF's own output line: in the verdict it says the PDF was the problem
        assertThat(verdict).doesNotContain("book-pdf").doesNotContain("no pages");
    }

    @Test
    void oneBrokenLinkReadsAsOne() {
        assertThat(CheckCmd.whatFailed(stoppedAtOutput(List.of(LINK), null)))
                .isEqualTo("1 link in the built output leads nowhere.");
    }

    @Test
    void anOutputThatWroteNothingNamesTheDeliverable() {
        assertThat(CheckCmd.whatFailed(stoppedAtOutput(List.of(), "book-pdf wrote nothing")))
                .isEqualTo("book-pdf wrote nothing.");
    }

    @Test
    void aFailedBuildIsNamed() {
        ProjectCheck check = new ProjectCheck(null,
                List.of(new DeliverableBuild("book-pdf", "pdf", "/out", false, List.of()),
                        new DeliverableBuild("book-html", "html5", "/out", true, List.of())),
                List.of("health", "build"), "build", null);

        assertThat(CheckCmd.whatFailed(check))
                .startsWith("book-pdf failed to build")
                .contains("The built output was not read");
    }

    @Test
    void nothingToBuildSaysWhy() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health", "build"), "build",
                "this project declares no deliverables");

        assertThat(CheckCmd.whatFailed(check))
                .startsWith("nothing was built")
                .contains("no deliverables");
    }

    @Test
    void healthFailureSaysTheRestWasNotChecked() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health"), "health", "faults");

        assertThat(CheckCmd.whatFailed(check))
                .contains("the project itself has faults")
                .contains("were not checked");
    }

    @Test
    void aStageNobodyPlannedForStillReadsAsAFailure() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health"), "publish", "the upload failed");

        assertThat(CheckCmd.whatFailed(check))
                .startsWith("stopped at publish")
                .contains("the upload failed");
    }

    // ── what the last line counts ───────────────────────────────────────────

    private static ProjectCheck ready(List<UnresolvedKeyRef> unresolved) {
        return new ProjectCheck(null, List.of(new DeliverableBuild("book-html", "html5", "/out", true, List.of())),
                List.of(), List.of("health", "build", "output"), null, null,
                List.of("book-html"), List.of(), unresolved);
    }

    @Test
    void theVerdictCountsTheWarningsPrintedAboveIt() {
        // Thirteen of these under a line reading "Ready: the project is healthy" is how a reader who reads
        // only the last line misses them (reported 2026-09-29).
        List<UnresolvedKeyRef> thirteen = new java.util.ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            thirteen.add(new UnresolvedKeyRef("collection", "topics/a" + i + ".dita", i, "glossary/term"));
        }

        assertThat(CheckCmd.describeWarnings(ready(thirteen)))
                .isEqualTo("13 key references that resolve to nothing in a deliverable");
    }

    @Test
    void oneOfThemIsSaidInTheSingular() {
        assertThat(CheckCmd.describeWarnings(ready(List.of(
                new UnresolvedKeyRef("book-pdf", "topics/a.dita", 52, "scripting")))))
                .isEqualTo("1 key reference that resolves to nothing in a deliverable");
    }

    @Test
    void aCleanCheckSaysNothingAboutWarnings() {
        assertThat(CheckCmd.describeWarnings(ready(List.of()))).isEmpty();
    }
}
