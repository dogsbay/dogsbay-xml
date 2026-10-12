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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.List;

/**
 * Whether a project is ready: its health, the deliverables it built, and whether the built output holds together.
 *
 * <p>Each stage runs only when the one before it passed, so a stage that is not in {@code stages} was not run and says
 * nothing — in particular it does not say "clean". A project with a broken reference produces output with a broken link
 * in the same place, and a report that pressed on would name one fault twice under two headings.
 *
 * <p>That discipline is why {@code outputsChecked} exists. An empty list of broken links is only good news when
 * something was read: a project with no deliverables, a build that wrote nothing, and a PDF deliverable with no pages
 * in it all produce no broken links, and none of them is a project whose output holds together. The {@code output}
 * stage counts as run only when at least one deliverable's output had pages in it.
 *
 * @param health             the project-health report, which always runs
 * @param builds             what each deliverable built, or empty when the build did not run
 * @param brokenOutputLinks  links in the built output that lead nowhere, across every deliverable that was read
 * @param stages             the stages that ran, in order, from {@link #STAGES}
 * @param stoppedAt          the stage that failed, or null when every stage that ran passed
 * @param reason             a sentence saying what stopped it, or what a stage could not look at; null when there is
 *                           nothing to say
 * @param outputsChecked     the deliverables whose output was read
 * @param outputsSkipped     the deliverables whose output had no pages to read, so nothing here speaks for them
 * @param unresolvedInBuild  key references a deliverable's own build could not resolve, and that carry no
 *                           {@code @href} to fall back on — warnings, and per deliverable: the key can be
 *                           defined in the project and missing from the scope this deliverable builds
 */
public record ProjectCheck(
    ProjectHealthReport health,
    List<DeliverableBuild> builds,
    List<BrokenRef> brokenOutputLinks,
    List<String> stages,
    String stoppedAt,
    String reason,
    List<String> outputsChecked,
    List<String> outputsSkipped,
    List<UnresolvedKeyRef> unresolvedInBuild
) {

    /** The stages, in the order they run. */
    public static final List<String> STAGES = List.of("health", "build", "output");

    public ProjectCheck {
        builds = builds == null ? List.of() : List.copyOf(builds);
        brokenOutputLinks = brokenOutputLinks == null ? List.of() : List.copyOf(brokenOutputLinks);
        unresolvedInBuild = unresolvedInBuild == null ? List.of() : List.copyOf(unresolvedInBuild);
        stages = stages == null ? List.of() : List.copyOf(stages);
        outputsChecked = outputsChecked == null ? List.of() : List.copyOf(outputsChecked);
        outputsSkipped = outputsSkipped == null ? List.of() : List.copyOf(outputsSkipped);
    }

    /** A check that stopped, or finished, with nothing to say about the output. */
    public ProjectCheck(ProjectHealthReport health, List<DeliverableBuild> builds, List<String> stages,
            String stoppedAt, String reason) {
        this(health, builds, List.of(), stages, stoppedAt, reason, List.of(), List.of(), List.of());
    }

    /** True when every stage that ran passed and none was cut short. */
    public boolean isClean() {
        return stoppedAt == null;
    }

    /**
     * True when the stage ran, and so its part of this report means something.
     *
     * @param stage one of {@link #STAGES}.
     */
    public boolean ran(String stage) {
        if (!STAGES.contains(stage)) {
            throw new IllegalArgumentException("Not a stage: " + stage + ". The stages are " + STAGES + ".");
        }
        return stages.contains(stage);
    }
}
