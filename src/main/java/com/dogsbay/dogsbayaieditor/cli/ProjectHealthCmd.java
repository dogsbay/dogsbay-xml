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

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ProjectHealthCommand;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;

@Command(name = "project-health",
        description = "Comprehensive project health: reuse-health PLUS DTD/grammar "
                + "validation, the conref element-id audit and, with --schematron, "
                + "the project's house rules. A clean result is a publish-ready "
                + "gate. Exits 1 when anything is found.")
class ProjectHealthCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory to scan")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — enables key analysis and scopes validation to "
                    + "the publication set")
    private String rootMap;

    @Option(names = "--schematron",
            description = "Schematron schema (.sch) of house rules to apply across the "
                    + "same scope. Rules like 'every topic needs a shortdesc' are "
                    + "Schematron, not grammar, so the gate cannot see them otherwise.")
    private String schematron;

    @Option(names = "--summary",
            description = "Print the counts only, without the per-finding detail. The "
                    + "detail is printed by default.")
    private boolean summaryOnly;

    @Option(names = "--include", split = ",",
            description = "Checks to run, comma-separated (default all): reuse, validation, "
                    + "elementIds, metadata, schematron, proposals")
    private java.util.List<String> include;

    @Option(names = "--severity",
            description = "'error' reports only what blocks a clean result (no unused keys, "
                    + "orphans, warnings or recommended metadata)")
    private String severity;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        ProjectHealthReport r = executor.execute(
                new ProjectHealthCommand(root, rootMap, schematron, include, severity, false));

        if (!summaryOnly && !r.openProposals().isEmpty()) {
            System.out.println("Open review proposals (" + r.openProposals().size() + " file(s)):");
            for (var op : r.openProposals()) {
                System.out.printf("  %s — %d change(s), %d comment(s) by %s%n", op.file(), op.changes(),
                        op.comments(), String.join(", ", op.authors()));
            }
        }
        if (r.isClean()) {
            System.out.println("Project is healthy: valid, no broken references, keys, "
                    + "orphans, or broken element ids."
                    + (r.isReviewed() ? "" : " Review the proposals above before publishing."));
            return 0;
        }

        var reuse = r.reuse();
        if (summaryOnly) {
            printSummary(r);
            return 1;
        }
        if (!reuse.brokenReferences().isEmpty()) {
            System.out.println("Broken references (" + reuse.brokenReferences().size() + "):");
            for (BrokenRef ref : reuse.brokenReferences()) {
                System.out.printf("  %s:%d  @%s=\"%s\"%n",
                        ref.source(), ref.line(), ref.attribute(), ref.value());
            }
        }
        if (!reuse.undefinedKeys().isEmpty()) {
            System.out.println("Undefined keys (" + reuse.undefinedKeys().size() + "):");
            for (BrokenRef ref : reuse.undefinedKeys()) {
                System.out.printf("  %s:%d  %s%n", ref.source(), ref.line(), ref.reason());
            }
        }
        if (!r.brokenElementIds().isEmpty()) {
            System.out.println("Broken element ids (" + r.brokenElementIds().size() + "):");
            for (BrokenRef ref : r.brokenElementIds()) {
                System.out.printf("  %s:%d  @%s=\"%s\" — %s%n",
                        ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason());
            }
        }
        if (!r.schematron().findings().isEmpty()) {
            System.out.println("House rules (" + r.schematron().failed() + " of "
                    + r.schematron().total() + " files):");
            for (var f : r.schematron().findings()) {
                System.out.printf("  %s:%d — %s%n", f.file(), f.line(), f.message());
            }
            if (r.schematron().truncated() > 0) {
                System.out.println("  … and " + r.schematron().truncated() + " more finding(s).");
            }
        }
        if (!r.metadata().findings().isEmpty()) {
            System.out.println("Metadata policy (" + r.metadata().failed() + " of "
                    + r.metadata().total() + " files):");
            for (var f : r.metadata().findings()) {
                System.out.printf("  %s — [%s] %s%n", f.file(), f.severity(), f.message());
            }
        }
        var validation = r.validation();
        if (!validation.isClean()) {
            System.out.println("Invalid files (" + validation.failed() + " of "
                    + validation.total() + "):");
            for (FileValidation fv : validation.findings()) {
                System.out.println("  " + fv.file() + ":");
                for (ValidationError e : fv.errors()) {
                    System.out.printf("    %d:%d  %s: %s%n",
                            e.line(), e.column(), e.severity(), e.message());
                }
            }
            if (validation.truncated() > 0) {
                System.out.println("  … and " + validation.truncated() + " more failing file(s).");
            }
        }
        if (!reuse.unusedKeys().isEmpty()) {
            System.out.println("Unused keys (" + reuse.unusedKeys().size() + "):");
            for (KeyInfo key : reuse.unusedKeys()) {
                System.out.printf("  %s  [%s:%d]%n", key.name(), key.definedIn(), key.line());
            }
        }
        if (!reuse.orphanTopics().isEmpty()) {
            System.out.println("Orphan topics (" + reuse.orphanTopics().size() + "):");
            for (String orphan : reuse.orphanTopics()) {
                System.out.println("  " + orphan);
            }
        }
        System.out.println();
        printSummary(r);
        return 1;
    }


    /**
     * The counts, after the detail. A broken project produces hundreds of lines
     * — 15 files failing one house rule is 15 lines that all say the same thing
     * — and the question is how much of what, not which line of which file. Last
     * rather than first because in a terminal the end is what you are looking
     * at; {@code --summary} prints this block alone.
     */
    private void printSummary(ProjectHealthReport r) {
        var reuse = r.reuse();
        System.out.println("Summary");
        count("Broken references", reuse.brokenReferences().size(), null);
        count("Undefined keys", reuse.undefinedKeys().size(), null);
        count("Broken element ids", r.brokenElementIds().size(), null);
        // findings() is capped, with the remainder in truncated(): counting the
        // list would report 200 findings across 250 failing files.
        count("House rules", total(r.schematron()),
                "in " + r.schematron().failed() + " of " + r.schematron().total() + " files");
        breakdown(r.schematron().findings().stream().map(SchematronFinding::message).toList(),
                r.schematron().truncated());
        long metaErrors = r.metadata().findings().stream()
                .filter(f -> "error".equals(f.severity())).count();
        count("Metadata policy", total(r.metadata()),
                "in " + r.metadata().failed() + " of " + r.metadata().total() + " files ("
                        + metaErrors + " error(s), "
                        + (r.metadata().findings().size() - metaErrors) + " warning(s))");
        breakdown(r.metadata().findings().stream().map(f -> f.message()).toList(),
                r.metadata().truncated());
        count("Invalid files", r.validation().failed(), "of " + r.validation().total());
        count("Unused keys", reuse.unusedKeys().size(), null);
        count("Orphan topics", reuse.orphanTopics().size(), null);
        count("Files with open proposals", r.openProposals().size(), null);
    }

    /** One summary row, printed only when it has something to report. */
    private static void count(String label, int n, String detail) {
        if (n > 0) {
            System.out.printf("  %-26s %4d%s%n", label, n, detail == null ? "" : "  " + detail);
        }
    }

    /**
     * Which rules account for the findings, most frequent first. Six kinds is
     * enough to see the shape of the work; the rest are in the detail below.
     */
    private static void breakdown(java.util.List<String> messages, int notListed) {
        var byMessage = byMessage(messages);
        int shown = Math.min(6, byMessage.size());
        for (var e : byMessage.subList(0, shown)) {
            System.out.printf("    %-62s %4d%n", abbreviate(e.getKey(), 62), e.getValue());
        }
        // The tail row holds findings, like every row above it — the number of
        // remaining rule kinds in that column would read as a finding count.
        int rest = notListed + byMessage.subList(shown, byMessage.size()).stream()
                .mapToInt(java.util.Map.Entry::getValue).sum();
        if (rest > 0) {
            int kinds = byMessage.size() - shown;
            System.out.printf("    %-62s %4d%n",
                    kinds > 0 ? "… across " + kinds + " other rule(s)" : "… not listed above", rest);
        }
    }

    /** Every finding, including those the report capped away. */
    private static int total(com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<?> batch) {
        return batch.findings().size() + batch.truncated();
    }

    /** Findings per distinct message, most frequent first. */
    private static java.util.List<java.util.Map.Entry<String, Integer>> byMessage(
            java.util.List<String> messages) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String m : messages) {
            counts.merge(m == null ? "(no message)" : m, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
    }

    private static String abbreviate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        // offsetByCodePoints, not substring: cutting mid-surrogate leaves half a
        // character, which renders as a replacement glyph.
        int end = text.offsetByCodePoints(0, text.codePointCount(0, max - 1));
        return text.substring(0, end) + "…";
    }
}
