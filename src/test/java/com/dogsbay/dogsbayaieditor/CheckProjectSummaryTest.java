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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;

/**
 * The editor's half of "a stage that did not run says nothing".
 *
 * <p>The line under the Project Validation pane is the only thing most people read, so it is the place a skipped stage
 * is most likely to be mistaken for a passed one. "Ready" must never be said on the strength of a clean verdict alone:
 * a project with no deliverables, and one asked not to build, both come back clean.
 */
class CheckProjectSummaryTest {

    /** A clean health report: the summary reads its orphan and unused-key counts. */
    private static com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport health() {
        return new com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport(
                null, null, null, null, null, null, null, null, List.of());
    }


    @Test
    void readyIsOnlySaidWhenSomethingWasActuallyRead() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of("full"), List.of(), List.of());

        assertThat(DogsBayAIEditor.summaryOf(check)).startsWith("Ready").contains("full");
    }

    @Test
    void readyStillCountsTheLinksThatWillBeMissing() {
        // The editor's summary says what the command line's verdict says: "Ready" over thirteen of these,
        // with no number in the last line, is how a reader misses them.
        List<com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef> unresolved = List.of(
                new com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef(
                        "install-variants", "topics/a.dita", 47, "formats/choosing"),
                new com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef(
                        "collection", "topics/b.dita", 12, "glossary/term"));
        ProjectCheck check = new ProjectCheck(health(), List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of("full"), List.of(), unresolved);

        assertThat(DogsBayAIEditor.summaryOf(check))
                .startsWith("Ready")
                .contains("2 key references that resolve to nothing in a deliverable")
                .contains("worth knowing");
    }

    @Test
    void aDeliverableWithNoPagesIsSaidToHaveWrittenAFile() {
        // Two writers of the same fact, and only the command line's had been brought up to date: "No pages to
        // read in book-pdf" left the reader unable to tell a PDF that was written from a build that produced
        // nothing (reported 2026-09-30).
        ProjectCheck check = new ProjectCheck(health(), List.of(), List.of(),
                List.of("health", "build", "output"), null, null,
                List.of("full"), List.of("book-pdf"), List.of());

        assertThat(DogsBayAIEditor.summaryOf(check))
                .startsWith("Ready")
                .contains("book-pdf wrote a file, with no pages to check links in")
                .doesNotContain("No pages to read");
    }

    @Test
    void aRunThatCheckedTheOutputSaysPlainlyWhatItFound() {
        // "the output of html5 holds together" read as a figure of speech. What the output stage
        // checks is links, and this is the wording the tutorial videos quote on every lesson page.
        // outputsChecked is the seventh field: health, builds, brokenOutputLinks, stages,
        // stoppedAt, reason, outputsChecked, outputsSkipped, unresolvedInBuild.
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of("html5"), List.of(),
                List.of());

        String summary = DogsBayAIEditor.summaryOf(check);

        assertThat(summary)
                .contains("Ready:")
                .contains("every link in the pages of html5 leads somewhere");
        assertThat(summary).doesNotContain("holds together");
    }

    @Test
    void aBigProjectCountsItsDeliverablesInsteadOfListingThem() {
        // Stage 26 of the tutorial builds seven, and the pane is the only place their names were ever
        // printed, so this is the sentence that broke first (tutorial team, 2026-10-08).
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(),
                List.of("health", "build", "output"), null, null,
                List.of("full", "beginner-mac", "beginner-windows", "podcaster-linux", "review",
                        "install-variants", "collection"),
                List.of(), List.of());

        String summary = DogsBayAIEditor.summaryOf(check);

        assertThat(summary).contains("every link in the pages of 7 deliverables leads somewhere");
        assertThat(summary).doesNotContain("beginner-mac");
    }

    @Test
    void anOutputStageThatReadNoPagesSaysSoInsteadOfNamingNothing() {
        // Every deliverable skipped, with the output stage run: the pane said "every link in the pages of
        // leads somewhere" — a claim about links, with the deliverables missing out of the middle of it.
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of(), List.of("pdf"), List.of());

        String summary = DogsBayAIEditor.summaryOf(check);

        assertThat(summary).startsWith("Ready").contains("every deliverable wrote something");
        assertThat(summary).doesNotContain("pages of  leads").doesNotContain("every link in");
        assertThat(summary).contains("pdf wrote a file, with no pages to check links in");
    }

    @Test
    void acleanRunThatReadNothingDoesNotClaimTheOutputWasChecked() {
        // a PDF-only project: healthy, built, and not a page anyone could check
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(),
                List.of("health", "build"), null, "no pages to read in pdf", List.of(), List.of("pdf"), List.of());

        String summary = DogsBayAIEditor.summaryOf(check);

        // Asserted against the wording that is actually printed now: the old phrase was removed, so
        // checking for its absence proved nothing.
        assertThat(summary).doesNotContain("Ready").doesNotContain("leads somewhere");
        assertThat(summary).contains("nothing here speaks for the output").contains("pdf");
    }

    @Test
    void aProjectWithNoDeliverablesIsNotReadyAndSaysWhy() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health", "build"), "build",
                "this project declares no deliverables, so there is nothing to build or check");

        String summary = DogsBayAIEditor.summaryOf(check);

        assertThat(summary).startsWith("Not ready").contains("no deliverables");
    }

    @Test
    void aRunWithoutABuildSaysTheOutputWasNotChecked() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health"), null, null);

        assertThat(DogsBayAIEditor.summaryOf(check))
                .doesNotContain("Ready")
                .contains("The build was not run");
    }

    @Test
    void brokenOutputLinksAreCounted() {
        BrokenRef ref = new BrokenRef("/out/index.html", 1, "href", "gone.html", "there is no gone.html");
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of(ref),
                List.of("health", "build", "output"), "output", null, List.of("full"), List.of(), List.of());

        assertThat(DogsBayAIEditor.summaryOf(check)).contains("Not ready: 1 link(s)");
    }

    @Test
    void aStageNameNobodyPlannedForStillReportsNotReady() {
        ProjectCheck check = new ProjectCheck(null, List.of(), List.of("health"), "publish", "the upload failed");

        assertThat(DogsBayAIEditor.summaryOf(check))
                .startsWith("Not ready: stopped at publish")
                .contains("the upload failed");
    }

    @Test
    void anOrphanIsNamedInTheVerdictEvenWhenTheProjectIsReady() {
        var health = new ProjectHealthReport(
                new HealthReport(List.of(), List.of(), List.of(), List.of("/p/forgotten.dita")),
                null, null, null, null, null);
        ProjectCheck check = new ProjectCheck(health, List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of("full"), List.of(), List.of());

        String summary = DogsBayAIEditor.summaryOf(check);

        assertThat(summary).startsWith("Ready").contains("1 orphan topic above: worth knowing");
    }

    @Test
    void anOrphanReachesThePaneAsAWarning() {
        var health = new ProjectHealthReport(
                new HealthReport(List.of(), List.of(), List.of(), List.of("/p/forgotten.dita")),
                null, null, null, null, null);
        ProjectCheck check = new ProjectCheck(health, List.of(), List.of(),
                List.of("health", "build", "output"), null, null, List.of("full"), List.of(), List.of());

        var errors = com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
                .fromProjectCheck(check, null);

        assertThat(errors).singleElement().satisfies(e -> {
            assertThat(e.getSystemId()).endsWith("/p/forgotten.dita");
            assertThat(e.getType()).isEqualTo(com.dogsbay.xml.XMLError.WARNING);
            assertThat(e.getMessage()).contains("Orphan topic");
        });
    }

}
