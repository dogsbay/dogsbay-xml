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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.dogsbay.dogsbayaieditor.commands.CheckProjectCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.CheckWording;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.xml.dita.DitaOtMessage;
import com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Is this project ready? The one command to run before publishing, and the one to run in CI.
 *
 * <p>It runs project health, then the build, then the check of the built output, and stops at the first stage that
 * fails. The report says which stages ran, so a stage that did not run says nothing rather than saying nothing is
 * wrong.
 */
@Command(name = "check",
        description = "Is this project ready? Project health, then the build, then the built output — "
                + "each stage only when the one before it passed. Exits 1 when it is not ready.")
class CheckCmd implements Callable<Integer> {

    /** How many errors to show for one invalid file before deferring to the full report. */
    private static final int ERRORS_SHOWN = 3;

    @Parameters(index = "0", description = "Project root to check")
    private String root;

    @Option(names = "--map", description = "Root map (default: the project's default root map; 'none' for no map)")
    private String map;

    @Option(names = "--schematron", description = "House rules .sch (default: the project's own; 'none' to skip)")
    private String schematron;

    @Option(names = "--deliverable", description = "One deliverable to build and check (default: all)")
    private String deliverable;

    @Option(names = "--out", description = "Base output directory (default: each deliverable's own)")
    private String out;

    @Option(names = {"-v", "--verbose"},
            description = "Also print DITA-OT's coded INFO messages (DOTJ047I), not only errors and warnings")
    private boolean verbose;

    @Option(names = "--no-build", description = "Stop after project health, without building")
    private boolean noBuild;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        ProjectCheck check = executor.execute(new CheckProjectCommand(
                root, map, schematron, deliverable, out, null, !noBuild));

        printHealth(check);
        printBuild(check);
        printOutput(check);

        if (!check.isClean()) {
            System.out.println("Not ready: " + whatFailed(check));
            return 1;
        }
        // Every warning printed above the verdict is counted in it, not only health's. Thirteen key references
        // that resolve to nothing, under a line reading "Ready: the project is healthy", is how a reader who
        // reads only the last line misses them.
        String said = describeWarnings(check);
        String warnings = said.isEmpty() ? ""
                : " " + said + " above: worth knowing, and not treated as failures.";
        if (check.ran("output")) {
            System.out.println("Ready: the project is healthy, every deliverable built, and "
                    + CheckWording.linksVerdict(check.outputsChecked()) + "." + warnings);
        } else if (!check.builds().isEmpty()) {
            System.out.println("The project is healthy and every deliverable built, but nothing was read"
                    + (check.reason() == null ? "" : " — " + check.reason())
                    + ", so nothing here speaks for the output." + warnings);
        } else {
            System.out.println("Project health is clean — "
                    + (check.reason() == null ? "the build was not run" : check.reason())
                    + ", so nothing here speaks for the output." + warnings);
        }
        return 0;
    }

    /**
     * What actually failed, in the words of the stage that failed.
     *
     * <p>This used to print the report's note, which is about what could not be read rather than what went wrong. A
     * run that stopped on seven broken links said "stopped at output — no pages to check links in book-pdf", and the
     * person reading it believed the PDF was the problem. The note still has its place; it is not the verdict.
     */
    static String whatFailed(ProjectCheck check) {
        return switch (check.stoppedAt()) {
            case "health" -> "the project itself has faults. The build and the built output were not checked.";
            case "build" -> check.builds().isEmpty()
                    ? "nothing was built" + parenthetical(check.reason()) + "."
                    : describeFailedBuilds(check) + " failed to build. The built output was not read.";
            case "output" -> {
                int broken = check.brokenOutputLinks().size();
                if (broken > 0) {
                    // No note here. It is about what could not be read, which is a different deliverable and a
                    // different subject; on its own output line it informs, in the verdict it misdirects.
                    yield broken + " link" + (broken == 1 ? "" : "s") + " in the built output lead"
                            + (broken == 1 ? "s" : "") + " nowhere.";
                }
                yield check.outputsSkipped().isEmpty()
                        ? "a deliverable wrote nothing."
                        : String.join(", ", check.outputsSkipped()) + " wrote nothing.";
            }
            default -> "stopped at " + check.stoppedAt() + parenthetical(check.reason()) + ".";
        };
    }

    private static String parenthetical(String reason) {
        return reason == null ? "" : " (" + reason + ")";
    }

    /** The deliverables that failed, named. */
    private static String describeFailedBuilds(ProjectCheck check) {
        List<String> failed = check.builds().stream()
                .filter(built -> !built.success())
                .map(DeliverableBuild::name)
                .toList();
        return failed.isEmpty() ? "a deliverable" : String.join(", ", failed);
    }

    private void printHealth(ProjectCheck check) {
        var health = check.health();
        if (health.isClean()) {
            System.out.println("health   clean" + (worthKnowing(health).isEmpty() ? "" : ", with warnings"));
            printWorthKnowing(health);
            return;
        }
        System.out.println("health   NOT CLEAN");
        for (BrokenRef ref : health.reuse().brokenReferences()) {
            System.out.printf("  %s:%d  @%s=\"%s\" — %s%n",
                    ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason());
        }
        for (BrokenRef key : health.reuse().undefinedKeys()) {
            System.out.printf("  %s:%d  @%s=\"%s\" — %s%n",
                    key.source(), key.line(), key.attribute(), key.value(), key.reason());
        }
        health.validation().findings().stream().filter(v -> !v.valid()).forEach(this::printInvalidFile);
        health.brokenElementIds().forEach(ref ->
                System.out.printf("  %s:%d  @%s=\"%s\" — %s%n",
                        ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason()));
        for (MetadataFinding finding : health.metadata().findings()) {
            if ("error".equals(finding.severity())) {
                System.out.printf("  %s:%d  %s — %s%n", finding.file(),
                        finding.line() > 0 ? finding.line() : 1, finding.field(), finding.message());
            }
        }
        for (SchematronFinding finding : health.schematron().findings()) {
            if (ProjectHealthReport.blocks(finding)) {
                // The message, as project-health prints it. The test expression was printed in front of it,
                // which for a <report test="true()"> rule put the word "true()" where the reader looks for the
                // rule — and the same finding then read differently depending on which command found it.
                System.out.printf("  %s:%d — %s%n", finding.file(),
                        finding.line() > 0 ? finding.line() : 1, finding.message());
            }
        }
        for (var violation : health.conditions().findings()) {
            System.out.printf("  %s:%d  @%s=\"%s\" — %s%n", violation.file(),
                    violation.line() > 0 ? violation.line() : 1, violation.attribute(), violation.value(),
                    violation.message());
        }
        health.conrefPush().forEach(issue ->
                System.out.printf("  %s:%d  <%s> — %s%n", issue.file(), issue.line(), issue.element(),
                        issue.problem()));
        health.danglingIndex().forEach(see ->
                System.out.printf("  %s  index %s \"%s\" — no entry \"%s\"%n", see.file(),
                        see.also() ? "see-also" : "see", see.from(), see.target()));
        System.out.println("  (run project-health for the full report)");
        printWorthKnowing(health);
    }

    /**
     * An invalid file, and why.
     *
     * <p>Naming the file alone made every reader open the Project Validation panel or run project-health to learn
     * anything — which is the whole job of this line. The first few errors are what tells you whether it is one typo
     * or a topic in the wrong shape; the rest belong in the full report.
     */
    private void printInvalidFile(FileValidation file) {
        System.out.println("  invalid: " + file.file());
        file.errors().stream().limit(ERRORS_SHOWN).forEach(e ->
                System.out.printf("    %d:%d  %s%n", e.line(), e.column(), e.message()));
        int more = file.errors().size() - ERRORS_SHOWN;
        if (more > 0) {
            System.out.println("    … and " + more + " more error(s) in this file.");
        }
    }

    /**
     * Orphan topics and unused keys: worth knowing, not failures.
     *
     * <p>Forgetting to add a topic to the map is the commonest mistake there is, and nothing else catches it — the
     * topic is valid, nothing refers to it, and it quietly does not appear in the output. A check that said "Ready"
     * over one would have told the truth and been no use.
     */
    private void printWorthKnowing(ProjectHealthReport health) {
        for (String orphan : health.reuse().orphanTopics()) {
            System.out.println("  orphan topic: " + orphan + " — nothing refers to it, so it will not appear in the "
                    + "output");
        }
        for (KeyInfo key : health.reuse().unusedKeys()) {
            System.out.printf("  unused key: %s  [%s:%d] — nothing references it%n",
                    key.name(), key.definedIn(), key.line());
        }
    }

    /** What the run found that is worth knowing without being a failure. */
    private static List<String> worthKnowing(ProjectHealthReport health) {
        List<String> out = new ArrayList<>(health.reuse().orphanTopics());
        health.reuse().unusedKeys().forEach(key -> out.add(key.name()));
        return out;
    }

    /** "2 orphan topics", "13 key references that resolve to nothing in a deliverable", or all of them. */
    static String describeWarnings(ProjectCheck check) {
        ProjectHealthReport health = check.health();
        List<String> parts = new ArrayList<>();
        // A check that stopped before health ran carries none, and the verdict still has to be printable.
        int orphans = health == null ? 0 : health.reuse().orphanTopics().size();
        int unused = health == null ? 0 : health.reuse().unusedKeys().size();
        if (orphans > 0) {
            parts.add(orphans + (orphans == 1 ? " orphan topic" : " orphan topics"));
        }
        if (unused > 0) {
            parts.add(unused + (unused == 1 ? " unused key" : " unused keys"));
        }
        int unresolved = check.unresolvedInBuild().size();
        if (unresolved > 0) {
            parts.add(unresolved + (unresolved == 1
                    ? " key reference that resolves to nothing in a deliverable"
                    : " key references that resolve to nothing in a deliverable"));
        }
        return String.join(", ", parts);
    }

    private void printBuild(ProjectCheck check) {
        if (!check.ran("build")) {
            return;
        }

        for (DeliverableBuild built : check.builds()) {
            System.out.printf("build    %-20s %s  %s%n", built.name(),
                    built.success() ? "ok" : "FAILED", built.outputDir());
            // Without --verbose, because this is the one note worth interrupting for: a key the project
            // defines and this deliverable does not, so the link is missing from this output alone. Matched on
            // the deliverable the finding names, not on its own prose — two deliverables can share a name, and
            // one name can sit inside another's.
            check.unresolvedInBuild().stream()
                    .filter(ref -> ref.deliverable().equals(built.name()))
                    .forEach(ref -> System.out.printf(
                            "  WARN  %s:%d  '%s' resolves to nothing here, and has no @href to fall back on%n",
                            ref.file(), ref.line(), ref.key()));
            // Every warning, coded or not. The coded ones were filtered out, which is precisely backwards:
            // DOTJ049W is the one a reader can look up, and it is what the documentation is indexed by.
            // Coded INFO messages (DOTJ047I, "not included in any map") come with --verbose.
            built.messages().stream()
                    .filter(m -> !m.isError())
                    .filter(m -> verbose || !"INFO".equals(m.severity()))
                    .forEach(m -> System.out.printf("  %s  %s%s%s%n", m.severity(),
                            m.code() == null || m.code().isBlank() ? "" : "[" + m.code() + "] ",
                            m.file() == null || m.file().isBlank() ? ""
                                    : m.file() + (m.line() > 0 ? ":" + m.line() : "") + "  ",
                            m.message()));
            if (!verbose) {
                long notes = built.messages().stream()
                        .filter(m -> "INFO".equals(m.severity())).count();
                if (notes > 0) {
                    System.out.printf("  %d note(s) — run with --verbose to see them%n", notes);
                }
            }
            if (!built.success()) {
                // With the code: DOTJ046E is searchable and the prose is not, and it is what DITA-OT's own
                // documentation is indexed by.
                built.messages().stream().filter(DitaOtMessage::isError).forEach(m ->
                        System.out.printf("  %s%s%s%n",
                                m.code() == null || m.code().isBlank() ? "" : "[" + m.code() + "] ",
                                m.file() == null || m.file().isBlank() ? ""
                                        : m.file() + (m.line() > 0 ? ":" + m.line() : "") + "  ",
                                m.message()));
            }
        }
    }

    private void printOutput(ProjectCheck check) {
        if (!check.ran("output")) {
            return;
        }
        if (!check.outputsSkipped().isEmpty()) {
            // Named, not silently dropped. A deliverable with no pages was still checked for having produced
            // something — it is the links, not the deliverable, that went unchecked.
            boolean wroteNothing = "output".equals(check.stoppedAt()) && check.brokenOutputLinks().isEmpty();
            System.out.println("output   " + (wroteNothing ? "wrote nothing: "
                    : "wrote a file, no pages to check links in ") + String.join(", ", check.outputsSkipped()));
        }
        if (check.outputsChecked().isEmpty()) {
            return;
        }
        if (check.brokenOutputLinks().isEmpty()) {
            System.out.println("output   clean (" + String.join(", ", check.outputsChecked()) + ")");
            return;
        }
        System.out.println("output   " + check.brokenOutputLinks().size() + " broken link(s)");
        for (BrokenRef ref : check.brokenOutputLinks()) {
            System.out.printf("  %s:%d  @%s=\"%s\" — %s%n",
                    ref.source(), ref.line(), ref.attribute(), ref.value(), ref.reason());
        }
    }
}
