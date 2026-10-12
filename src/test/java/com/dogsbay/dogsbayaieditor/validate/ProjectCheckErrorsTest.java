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

package com.dogsbay.dogsbayaieditor.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Every fault that stops a project being ready has to reach the pane.
 *
 * <p>The failure this guards against is specific and quiet: a summary saying "the project itself has faults" over an
 * empty list, with nothing to click and no hint of where to look. It happens the moment a leg is added to
 * {@link ProjectHealthReport#isClean()} without being added to the flattening — which is exactly how conref-push and
 * index findings came to be missing.
 */
class ProjectCheckErrorsTest {

    private static final BrokenRef REF = new BrokenRef("/p/a.dita", 3, "href", "gone.dita", "target not found");

    private ProjectHealthReport healthWith(Function<ProjectHealthReport, ProjectHealthReport> leg) {
        return leg.apply(empty());
    }

    private static ProjectHealthReport empty() {
        return new ProjectHealthReport(null, null, null, null, null, null, null, null,
                (List<String>) null);
    }

    private static List<com.dogsbay.xml.XMLError> paneErrors(ProjectHealthReport health) {
        return ProjectValidationErrors.fromProjectCheck(
                new ProjectCheck(health, null, List.of("health"), "health", "faults"), null);
    }

    /** Each leg that blocks a clean verdict, on its own, with nothing else wrong. */
    private static List<ProjectHealthReport> everyBlockingLeg() {
        return List.of(
            // broken references
            new ProjectHealthReport(new HealthReport(List.of(REF), List.of(), List.of(), List.of()),
                null, null, null, null, null),
            // undefined keys
            new ProjectHealthReport(new HealthReport(List.of(), List.of(REF), List.of(), List.of()),
                null, null, null, null, null),
            // a file that does not validate
            new ProjectHealthReport(null,
                new BatchResult<>(1, 0, 1, List.of(new FileValidation("/p/a.dita", false,
                    List.of(new ValidationError(2, 1, "error", "element not allowed", "concept.dtd")))), 0),
                null, null, null, null),
            // a conref pointing at an element id that is not there
            new ProjectHealthReport(null, null, List.of(REF), null, null, null),
            // required metadata missing
            new ProjectHealthReport(null, null, null,
                new BatchResult<>(1, 0, 1, List.of(new MetadataFinding("/p/a.dita", 1, "audience",
                    "required field missing", "error")), 0),
                null, null),
            // a house rule that fired
            new ProjectHealthReport(null, null, null, null, null,
                new BatchResult<>(1, 0, 1, List.of(new SchematronFinding("/p/a.dita", "/topic[1]",
                    "Every topic needs a shortdesc.", null, "shortdesc", 1)), 0)),
            // a conref push that goes nowhere
            new ProjectHealthReport(null, null, null, null, null, null, null, null, null,
                List.of(new ConrefPushAuditResult.Issue("/p/a.dita", 7, "p", "pushes to a target that is not there")),
                null, null, null, null, null, null),
            // an index see redirecting to an entry that does not exist
            new ProjectHealthReport(null, null, null, null, null, null, null, null, null, null,
                List.of(new IndexAuditResult.DanglingSee("/p/a.dita", "Widgets", "Gadgets", false)),
                null, null, null, null, null),
            // a profiling value the scheme does not allow
            new ProjectHealthReport(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, new BatchResult<>(1, 0, 1, List.of(new ConditionViolation("/p/a.dita", "platform", "macos",
                    "\u201Cmacos\u201D is not a controlled value for @platform", "mac", 3)), 0), null));
    }

    @Test
    void everyFaultThatBlocksACleanVerdictReachesThePane() {
        for (ProjectHealthReport health : everyBlockingLeg()) {
            assertThat(health.isClean())
                    .as("this fixture should describe a project that is not clean")
                    .isFalse();
            assertThat(paneErrors(health))
                    .as("a fault that stops the project being ready must be visible in the pane")
                    .isNotEmpty();
        }
    }

    @Test
    void aConrefPushThatGoesNowhereOpensTheFileThatPushes() {
        var errors = ProjectValidationErrors.fromConrefPush(
                List.of(new ConrefPushAuditResult.Issue("/p/a.dita", 7, "p", "pushes nowhere")));

        assertThat(errors).singleElement().satisfies(e -> {
            assertThat(e.getSystemId()).endsWith("/p/a.dita");
            assertThat(e.getLineNumber()).isEqualTo(7);
            assertThat(e.getMessage()).contains("pushes nowhere");
        });
    }

    @Test
    void aDanglingIndexEntrySaysWhatItRedirectsTo() {
        var errors = ProjectValidationErrors.fromDanglingIndex(
                List.of(new IndexAuditResult.DanglingSee("/p/a.dita", "Widgets", "Gadgets", true)));

        assertThat(errors).singleElement().satisfies(e ->
                assertThat(e.getMessage()).contains("see-also").contains("Widgets").contains("Gadgets"));
    }

    @Test
    void aKeyThatResolvesToNothingInADeliverableReachesThePane() {
        // The reader of the editor was the one person these never reached: the command line listed them under
        // each deliverable while Check Project said "Ready" over an empty pane (reported 2026-09-29).
        var unresolved = new com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef(
                "install-variants", "topics/exporting-audio.dita", 47, "formats/choosing");
        var check = new ProjectCheck(empty(), List.of(), List.of(), List.of("health", "build", "output"),
                null, null, List.of("install-variants"), List.of(), List.of(unresolved));

        var errors = ProjectValidationErrors.fromProjectCheck(check, new java.io.File("/p"));

        assertThat(errors).singleElement().satisfies(e -> {
            // Opens the topic that holds the reference, at the line, which needs the project-relative path
            // the finding carries to be resolved against the root.
            assertThat(e.getSystemId()).endsWith("/p/topics/exporting-audio.dita");
            assertThat(e.getLineNumber()).isEqualTo(47);
            assertThat(e.getType()).isEqualTo(com.dogsbay.xml.XMLError.WARNING);
            assertThat(e.getMessage()).contains("install-variants").contains("formats/choosing");
        });
    }

    @Test
    void aCleanCheckWithNoSuchWarningsAddsNothingToThePane() {
        var check = new ProjectCheck(empty(), List.of(), List.of(), List.of("health", "build", "output"),
                null, null, List.of("full"), List.of(), List.of());

        assertThat(ProjectValidationErrors.fromProjectCheck(check, new java.io.File("/p"))).isEmpty();
    }

    @Test
    void brokenLinksInTheBuiltOutputOpenTheOutputPageNotTheSource() {
        BrokenRef inOutput = new BrokenRef("/p/out/index.html", 4, "href", "gone.html", "there is no gone.html");
        var check = new ProjectCheck(empty(), List.of(), List.of(inOutput), List.of("health", "build", "output"),
                "output", null, List.of("full"), List.of(), List.of());

        var errors = ProjectValidationErrors.fromProjectCheck(check, null);

        assertThat(errors).singleElement().satisfies(e -> {
            assertThat(e.getSystemId()).endsWith("/p/out/index.html");
            assertThat(e.getLineNumber()).isEqualTo(4);
        });
    }
}
