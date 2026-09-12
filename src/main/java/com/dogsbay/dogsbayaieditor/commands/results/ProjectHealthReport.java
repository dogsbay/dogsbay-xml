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

import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Comprehensive project health: the reuse-health checks (broken references,
 * undefined/unused keys, orphan topics), DTD/grammar validation of the scope,
 * <em>and</em> the conref element-id audit (reuse references whose target file
 * exists but whose {@code #fragment} id is missing).
 *
 * <p>This makes "clean" a trustworthy done-signal: it is true only when the
 * project validates, is reuse-healthy, and has no broken element ids — closing
 * the two capstone gaps (invalid topics, and typo'd conref ids) that let an agent
 * declare a guide "publish-ready" when it was not. Still <em>not</em> covered:
 * hardcoded product text and markup style.
 *
 * @param reuse            the reuse-health report (refs, keys, orphans)
 * @param validation       per-file DTD/grammar validation of the scope (failing files)
 * @param brokenElementIds reuse references pointing at a missing element id
 * @param metadata         required-metadata policy audit (empty when no policy);
 *                         only <em>error</em>-level findings block "clean"
 * @param schematron       house-rule findings when the command was given a
 *                         Schematron schema; empty and clean when it was not
 */
public record ProjectHealthReport(
    HealthReport reuse,
    BatchResult<FileValidation> validation,
    List<BrokenRef> brokenElementIds,
    BatchResult<MetadataFinding> metadata,
    List<OpenProposals> openProposals,
    BatchResult<SchematronFinding> schematron
) {
    public ProjectHealthReport {
        brokenElementIds = brokenElementIds == null ? List.of() : List.copyOf(brokenElementIds);
        metadata = metadata == null
                ? new BatchResult<>(0, 0, 0, List.of(), 0) : metadata;
        openProposals = openProposals == null ? List.of() : List.copyOf(openProposals);
        schematron = schematron == null
                ? new BatchResult<>(0, 0, 0, List.of(), 0) : schematron;
    }

    /** Without a Schematron leg — the shape before the {@code --schematron} option existed. */
    public ProjectHealthReport(HealthReport reuse, BatchResult<FileValidation> validation,
            List<BrokenRef> brokenElementIds, BatchResult<MetadataFinding> metadata,
            List<OpenProposals> openProposals) {
        this(reuse, validation, brokenElementIds, metadata, openProposals, null);
    }

    /** True when nothing is left to review. Not part of {@link #isClean()}: a proposal is not an error. */
    public boolean isReviewed() {
        return openProposals.isEmpty();
    }

    /**
     * Clean only when reuse-health, validation, element-id audit, the
     * required-metadata policy AND any Schematron schema all pass.
     * Recommended-metadata findings (warnings) don't block — only error-level
     * (missing required / forbidden / bad value). A run given no schema has an
     * empty Schematron leg, which is clean.
     */
    public boolean isClean() {
        return reuse.isClean() && validation.isClean() && brokenElementIds.isEmpty()
                && metadata.findings().stream().noneMatch(f -> "error".equals(f.severity()))
                && schematron.findings().stream().noneMatch(ProjectHealthReport::blocks);
    }

    /**
     * Whether a house-rule finding stops the project being publish-ready.
     *
     * <p>A schema author can mark a rule {@code role="warning"} (or info, or
     * hint), and the same courtesy the metadata policy extends to its
     * recommended fields applies here: advisory rules are reported but do not
     * fail the gate. A rule with no role is a rule the author did not soften,
     * so it blocks — including a fired {@code <report>}, which is how a
     * prohibition ("do not hardcode the product name") is written.
     */
    private static boolean blocks(SchematronFinding f) {
        String role = f.role() == null ? "" : f.role().toLowerCase(java.util.Locale.ROOT);
        return !(role.equals("warning") || role.equals("warn")
                || role.equals("info") || role.equals("information") || role.equals("hint"));
    }
}
