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

import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ProjectHealthCommand;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;

@Command(name = "project-health",
        description = "Comprehensive project health: reuse-health, DTD/grammar validation, element ids, "
                + "the metadata policy, the project's house rules, conref pushes and the index; plus "
                + "house-style formatting and authoring leftovers as warnings. The root map and house rules "
                + "default to the project's (.dogsbay/config.xml). Files with open review proposals are "
                + "judged by the text that would ship. A clean result is a publish-ready gate. Exits 1 when "
                + "anything is found.")
class ProjectHealthCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory to scan")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — enables key analysis and scopes validation to "
                    + "the publication set (default: the project's, for keys only; 'none' for no map)")
    private String rootMap;

    @Option(names = "--schematron",
            description = "Schematron schema (.sch) of house rules to apply across the "
                    + "same scope (default: the project's <default-schematron>; 'none' to skip). "
                    + "Rules like 'every topic needs a shortdesc' are "
                    + "Schematron, not grammar, so the gate cannot see them otherwise.")
    private String schematron;

    @Option(names = "--summary",
            description = "Print the counts only, without the per-finding detail. The "
                    + "detail is printed by default.")
    private boolean summaryOnly;

    @Option(names = "--include", split = ",",
            description = "Checks to run, comma-separated (default all): reuse, validation, "
                    + "elementIds, metadata, schematron, proposals, conrefPush, index, format, markers, "
                    + "conditions")
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
        return print(r, summaryOnly, include, severity);
    }

    /**
     * The report, as a reader sees it.
     *
     * <p>Separate from {@link #call()} so a test can drive it with a report of its own making. Every leg has to reach
     * both this and the summary, and finding out which ones did was a matter of running the command and reading — see
     * {@code ProjectHealthLegCoverageTest}.
     *
     * @return the exit code: 0 when clean, 1 otherwise.
     */
    static int print(ProjectHealthReport r, boolean summaryOnly, java.util.List<String> include,
            String severity) {
        var d = r.defaults();
        if (d != null && !summaryOnly) {
            System.out.println("Root map: " + (d.map() == null ? "none" : d.map()) + " (" + d.mapSource() + ");"
                    + " house rules: " + (d.schematron() == null ? "none" : d.schematron()) + " ("
                    + d.schematronSource() + ")");
        }
        if (!summaryOnly && !r.pendingFixes().isEmpty()) {
            System.out.println("Fixed once the open proposals are accepted (" + r.pendingFixes().size() + "):");
            for (var p : r.pendingFixes()) {
                System.out.printf("  %s — [%s] %s%n", p.file(), p.leg(), p.finding());
            }
        }
        if (!summaryOnly && !r.unformatted().isEmpty()) {
            System.out.println("Not in house style (" + r.unformatted().size() + "; warnings):");
            r.unformatted().forEach(f -> System.out.println("  " + f));
        }
        if (!summaryOnly && !r.markers().isEmpty()) {
            System.out.println("Authoring leftovers (" + r.markers().size() + "; warnings):");
            for (var m : r.markers()) {
                System.out.printf("  %s:%d  %s: %s%n", m.file(), m.line(), m.kind(), m.text());
            }
        }
        if (!summaryOnly && !r.openProposals().isEmpty()) {
            System.out.println("Open review proposals (" + r.openProposals().size() + " file(s)):");
            for (var op : r.openProposals()) {
                System.out.printf("  %s — %d change(s), %d comment(s) by %s%n", op.file(), op.changes(),
                        op.comments(), String.join(", ", op.authors()));
            }
        }
        if (r.isClean()) {
            // Orphans and unused keys do not block, but they are still findings, and the summary used to claim there
            // were none while returning before the lines that would have listed them. A topic left out of the map is
            // exactly what someone runs this to find.
            int orphans = r.reuse().orphanTopics().size();
            int unused = r.reuse().unusedKeys().size();
            if (!summaryOnly && orphans > 0) {
                System.out.println("Orphan topics (" + orphans + "; nothing refers to them):");
                for (String orphan : r.reuse().orphanTopics()) {
                    System.out.println("  " + orphan);
                }
            }
            if (!summaryOnly && unused > 0) {
                System.out.println("Unused keys (" + unused + "):");
                for (KeyInfo key : r.reuse().unusedKeys()) {
                    System.out.printf("  %s  [%s:%d]%n", key.name(), key.definedIn(), key.line());
                }
            }
            if (summaryOnly) {
                // Asked for a summary, get the numbers. This used to print the verdict alone on a clean project,
                // so the warning legs — house style, authoring leftovers, fixes waiting on a proposal — had no
                // summary at all: they are listed in the full report and counted nowhere.
                printSummary(r);
            }
            System.out.println(verdict(r, orphans, unused, include, severity)
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
        if (!r.conditions().findings().isEmpty()) {
            System.out.println("Controlled values (" + total(r.conditions()) + "):");
            for (var v : r.conditions().findings()) {
                System.out.printf("  %s:%d  @%s=\"%s\" — %s%n", v.file(),
                        v.line() > 0 ? v.line() : 1, v.attribute(), v.value(), v.message());
            }
            if (r.conditions().truncated() > 0) {
                System.out.println("  … and " + r.conditions().truncated() + " more.");
            }
        }
        if (!r.conrefPush().isEmpty()) {
            System.out.println("Conref pushes (" + r.conrefPush().size() + "):");
            for (var i : r.conrefPush()) {
                System.out.printf("  %s:%d  <%s> %s%n", i.file(), i.line(), i.element(), i.problem());
            }
        }
        if (!r.danglingIndex().isEmpty()) {
            System.out.println("Index redirects to nothing (" + r.danglingIndex().size() + "):");
            for (var i : r.danglingIndex()) {
                System.out.printf("  %s  \"%s\" %s \"%s\"%n", i.file(), i.from(), i.also() ? "see also" : "see", i.target());
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
    private static void printSummary(ProjectHealthReport r) {
        var reuse = r.reuse();
        System.out.println("Summary");
        count("Broken references", reuse.brokenReferences().size(), null);
        count("Undefined keys", reuse.undefinedKeys().size(), null);
        count("Broken element ids", r.brokenElementIds().size(), null);
        count("Controlled values", total(r.conditions()),
                r.conditions().total() > 0
                        ? "in " + r.conditions().failed() + " of " + r.conditions().total() + " files" : null);
        count("Conref pushes", r.conrefPush().size(), null);
        count("Index redirects to nothing", r.danglingIndex().size(), null);
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
        count("Fixed once accepted", r.pendingFixes().size(), null);
        count("Not in house style", r.unformatted().size(), "warnings");
        count("Authoring leftovers", r.markers().size(), "warnings");
    }

    /** One summary row, printed only when it has something to report. */
    /** "2 orphan topics and 1 unused key", for a summary that has to name what it found. */
    /**
     * What a clean run is entitled to say.
     *
     * <p>A run narrowed with {@code --include} checked some of the legs, and "Project is healthy: valid, …" is a claim
     * about the ones it did not. A gate is only worth having if it never overstates what it looked at, so a narrowed
     * run names its legs instead.
     */
    private static String verdict(ProjectHealthReport r, int orphans, int unused,
            java.util.List<String> include, String severity) {
        if (r.checked().isEmpty()) {
            // Reachable: --include format --severity error asks for one leg and then drops it, and exiting 0 with a
            // clean-sounding line would be the emptiest possible pass.
            return "Nothing was checked, so this says nothing about the project.";
        }
        String findings = orphans + unused == 0 ? ""
                : " " + describe(orphans, unused) + " above: worth knowing, and not treated as failures.";
        boolean full = include == null || include.isEmpty();
        if (full && !errorsOnly(severity)) {
            // A full run. A leg that found nothing to do — no house rules configured, say — still ran, which is not
            // the same as one that was excluded, so the wording is judged by what was asked for.
            return (orphans + unused == 0
                    ? "Project is healthy: valid, no broken references, keys, orphans, or broken element ids."
                    : "Project is valid, with no broken references, keys or element ids.") + findings;
        }
        if (full) {
            // --severity error drops orphans and unused keys before the report is built, so their counts here are
            // zero whether or not the project has any. Claiming "no orphans" would be reporting a list that was
            // thrown away.
            return "No errors. This run reported only what blocks a clean result, so it says nothing about orphan "
                    + "topics, unused keys, or anything else it filtered out.";
        }
        List<String> missed = ProjectHealthCommand.LEGS.stream()
                .filter(leg -> !r.checked().contains(leg)).toList();
        return "Clean, as far as this run looked: " + String.join(", ", r.checked())
                + (missed.isEmpty() ? "." : ". Nothing here speaks for " + String.join(", ", missed) + ".")
                + (errorsOnly(severity) ? " Only what blocks a clean result was reported." : findings);
    }

    /** Whether this run reported only what blocks a clean result, and so filtered findings out of the report. */
    private static boolean errorsOnly(String severity) {
        return severity != null && severity.equalsIgnoreCase("error");
    }

    private static String describe(int orphans, int unused) {
        StringBuilder out = new StringBuilder();
        if (orphans > 0) {
            out.append(orphans).append(orphans == 1 ? " orphan topic" : " orphan topics");
        }
        if (unused > 0) {
            out.append(out.length() > 0 ? " and " : "").append(unused).append(unused == 1 ? " unused key" : " unused keys");
        }
        return out.toString();
    }

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
