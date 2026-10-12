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
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.HealthMarker;
import com.dogsbay.dogsbayaieditor.commands.results.HealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.commands.results.OpenProposals;
import com.dogsbay.dogsbayaieditor.commands.results.PendingFix;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.RuleGroup;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * A leg nobody can see is a leg nobody fixed.
 *
 * <p>The controlled-values leg shipped three times before it was usable: not run at all, then only when a map was
 * named on the command line, then exiting 1 over a value the full report never mentioned — under an empty "Summary",
 * from the command {@code check} tells people to run for the full report. Each surface a leg has to reach was found
 * by hand, and nothing made the list of them obvious.
 *
 * <p>This is that list. Every finding-bearing component of {@link ProjectHealthReport} must be described here with a
 * populated sample and the words its report is expected to contain, or named in {@link #NOT_FINDINGS} with a reason.
 * Adding a component to the record fails {@link #everyPartOfTheReportIsAccountedFor} until one or the other is done —
 * and describing it is what makes the rest of the checks run against it.
 */
class ProjectHealthLegCoverageTest {

    private static final String FILE = "/p/a.dita";

    /** What a leg is: a report carrying only that leg's finding, and what the printed report must say about it. */
    private record Leg(ProjectHealthReport report, String inReport, String inSummary) {
    }

    /**
     * Components that carry no findings, with the reason each is exempt.
     *
     * <p>A component belongs here only when there is nothing a reader could act on in it.
     */
    private static final Map<String, String> NOT_FINDINGS = Map.of(
            "checked", "the legs that ran, which is metadata about the report rather than a finding",
            "defaults", "the root map and house rules used, printed as the report's first line",
            "metadataRules", "the grouped form of metadata, reported through the same counts",
            "schematronRules", "the grouped form of schematron, reported through the same counts");

    /** One sample per finding-bearing component. The order follows the record's. */
    private static Map<String, Leg> legs() {
        Map<String, Leg> legs = new LinkedHashMap<>();
        legs.put("reuse", new Leg(report(r -> r.reuse(new HealthReport(
                List.of(new BrokenRef(FILE, 3, "href", "gone.dita", "target not found")),
                List.of(), List.of(), List.of()))), "gone.dita", "Broken references"));
        legs.put("validation", new Leg(report(r -> r.validation(new BatchResult<>(1, 0, 1,
                List.of(new FileValidation(FILE, false,
                        List.of(new ValidationError(2, 1, "error", "element not allowed", "concept.dtd")))), 0))),
                "element not allowed", "Invalid files"));
        legs.put("brokenElementIds", new Leg(report(r -> r.brokenElementIds(
                List.of(new BrokenRef(FILE, 4, "conref", "b.dita#b/missing", "no such element id")))),
                "b.dita#b/missing", "Broken element ids"));
        legs.put("metadata", new Leg(report(r -> r.metadata(new BatchResult<>(1, 0, 1,
                List.of(new MetadataFinding(FILE, 1, "audience", "required field missing", "error")), 0))),
                "required field missing", "Metadata policy"));
        legs.put("openProposals", new Leg(report(r -> r.openProposals(
                List.of(new OpenProposals(FILE, 2, 1, List.of("an agent"))))),
                "Open review proposals", "proposal"));
        legs.put("schematron", new Leg(report(r -> r.schematron(new BatchResult<>(1, 0, 1,
                List.of(new SchematronFinding(FILE, "/topic[1]", "Every topic needs a shortdesc.",
                        null, "shortdesc", 1)), 0))),
                "Every topic needs a shortdesc", "House rules"));
        legs.put("conrefPush", new Leg(report(r -> r.conrefPush(
                List.of(new ConrefPushAuditResult.Issue(FILE, 7, "p", "pushes nowhere")))),
                "pushes nowhere", "Conref pushes"));
        legs.put("danglingIndex", new Leg(report(r -> r.danglingIndex(
                List.of(new IndexAuditResult.DanglingSee(FILE, "Widgets", "Gadgets", false)))),
                "Gadgets", "Index redirects to nothing"));
        legs.put("unformatted", new Leg(report(r -> r.unformatted(List.of(FILE))),
                "Not in house style", "Not in house style"));
        legs.put("markers", new Leg(report(r -> r.markers(
                List.of(new HealthMarker(FILE, 9, "TODO", "finish this")))),
                "finish this", "Authoring leftovers"));
        legs.put("pendingFixes", new Leg(report(r -> r.pendingFixes(
                List.of(new PendingFix(FILE, "reuse", "a broken reference a proposal already fixes")))),
                "a broken reference a proposal already fixes", "Fixed once accepted"));
        legs.put("conditions", new Leg(report(r -> r.conditions(new BatchResult<>(1, 0, 1,
                List.of(new ConditionViolation(FILE, "platform", "macos",
                        "“macos” is not a controlled value for @platform", "mac", 3)), 0))),
                "macos", "Controlled values"));
        return legs;
    }

    @Test
    void everyPartOfTheReportIsAccountedFor() {
        Set<String> described = legs().keySet();
        List<String> unaccounted = new ArrayList<>();
        for (RecordComponent component : ProjectHealthReport.class.getRecordComponents()) {
            if (!described.contains(component.getName()) && !NOT_FINDINGS.containsKey(component.getName())) {
                unaccounted.add(component.getName());
            }
        }

        assertThat(unaccounted)
                .as("a new part of the health report must be given a sample here, so the checks below run against "
                        + "it — or named in NOT_FINDINGS with the reason it carries nothing to act on")
                .isEmpty();
    }

    @TestFactory
    List<DynamicTest> everyLegAppearsInTheFullReport() {
        List<DynamicTest> tests = new ArrayList<>();
        legs().forEach((name, leg) -> tests.add(DynamicTest.dynamicTest(name, () -> {
            String out = report(leg.report(), false);

            // check tells people to run project-health "for the full report"; a leg missing from it exits 1 over
            // something the reader is never shown
            assertThat(out).as("%s in the full report", name).contains(leg.inReport());
        })));
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyLegAppearsInTheSummary() {
        List<DynamicTest> tests = new ArrayList<>();
        legs().forEach((name, leg) -> tests.add(DynamicTest.dynamicTest(name, () -> {
            String out = report(leg.report(), true);

            assertThat(out.toLowerCase()).as("%s in the summary", name)
                    .contains(leg.inSummary().toLowerCase());
        })));
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyLegThatBlocksACleanVerdictReachesThePane() {
        List<DynamicTest> tests = new ArrayList<>();
        legs().forEach((name, leg) -> {
            if (leg.report().isClean()) {
                return; // a warning-only leg is not required to reach the pane as an error
            }
            tests.add(DynamicTest.dynamicTest(name, () -> {
                var errors = com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors.fromProjectCheck(
                        new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                                leg.report(), List.of(), List.of("health"), "health", null), null);

                assertThat(errors).as("%s must be visible in the Project Validation panel, since it is what stops "
                        + "the project being ready", name).isNotEmpty();
            }));
        });
        return tests;
    }

    /** Run the CLI over a report, with the executor stubbed out. */
    private static String report(ProjectHealthReport report, boolean summaryOnly) throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            ProjectHealthCmd.print(report, summaryOnly, java.util.List.of(), null);
        } finally {
            System.setOut(previous);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    // ── building a report with one leg filled in ─────────────────────────

    private interface With {
        Builder apply(Builder builder);
    }

    private static ProjectHealthReport report(With with) {
        return with.apply(new Builder()).build();
    }

    /** A report with nothing in it but the one leg under test. */
    private static final class Builder {
        private HealthReport reuse;
        private BatchResult<FileValidation> validation;
        private List<BrokenRef> brokenElementIds;
        private BatchResult<MetadataFinding> metadata;
        private List<OpenProposals> openProposals;
        private BatchResult<SchematronFinding> schematron;
        private List<ConrefPushAuditResult.Issue> conrefPush;
        private List<IndexAuditResult.DanglingSee> danglingIndex;
        private List<String> unformatted;
        private List<HealthMarker> markers;
        private List<PendingFix> pendingFixes;
        private BatchResult<ConditionViolation> conditions;

        Builder reuse(HealthReport v) {
            reuse = v;
            return this;
        }

        Builder validation(BatchResult<FileValidation> v) {
            validation = v;
            return this;
        }

        Builder brokenElementIds(List<BrokenRef> v) {
            brokenElementIds = v;
            return this;
        }

        Builder metadata(BatchResult<MetadataFinding> v) {
            metadata = v;
            return this;
        }

        Builder openProposals(List<OpenProposals> v) {
            openProposals = v;
            return this;
        }

        Builder schematron(BatchResult<SchematronFinding> v) {
            schematron = v;
            return this;
        }

        Builder conrefPush(List<ConrefPushAuditResult.Issue> v) {
            conrefPush = v;
            return this;
        }

        Builder danglingIndex(List<IndexAuditResult.DanglingSee> v) {
            danglingIndex = v;
            return this;
        }

        Builder unformatted(List<String> v) {
            unformatted = v;
            return this;
        }

        Builder markers(List<HealthMarker> v) {
            markers = v;
            return this;
        }

        Builder pendingFixes(List<PendingFix> v) {
            pendingFixes = v;
            return this;
        }

        Builder conditions(BatchResult<ConditionViolation> v) {
            conditions = v;
            return this;
        }

        ProjectHealthReport build() {
            return new ProjectHealthReport(reuse, validation, brokenElementIds, metadata, openProposals,
                    schematron, List.<RuleGroup>of(), List.<RuleGroup>of(), List.<String>of(), conrefPush,
                    danglingIndex, unformatted, markers, pendingFixes, conditions, null);
        }
    }
}
