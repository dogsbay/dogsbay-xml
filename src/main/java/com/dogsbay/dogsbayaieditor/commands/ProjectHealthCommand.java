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

import java.util.ArrayList;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;

/**
 * Comprehensive project health: reuse-health (broken refs, undefined/unused
 * keys, orphans) plus DTD/grammar validation of the scope. The aggregate
 * {@code clean} is a trustworthy done-signal — see {@link ProjectHealthReport}.
 *
 * <p>When a root map is given, validation covers its publication set (the
 * {@code FileSet} crawl); otherwise it covers every XML-ish file under the root.
 *
 * <p>A project's house rules — "every topic needs a shortdesc", "no hardcoded
 * product name" — are Schematron, not grammar, so a gate that skipped them
 * called a project publish-ready while its own {@code .sch} was failing. Give
 * {@code schematron} a schema and those findings join the report and the
 * {@code clean} verdict.
 *
 * <p>An agent rarely needs every leg: metadata findings were most of a report
 * that was asked about broken references. {@code include} picks legs,
 * {@code severity} drops what is not an error, and {@code groupRules} reports a
 * rule that fired in many files once, with a count.
 *
 * @param root       the project root to scan
 * @param rootMap    optional root map — enables key analysis and scopes validation
 *                   to the publication set
 * @param schematron optional Schematron schema (.sch) to apply across the same
 *                   scope; null or blank to skip that leg
 * @param include    legs to run, from {@link #LEGS}; null or empty for all
 * @param severity   {@code error} to keep only findings that block a clean report; null for all
 * @param groupRules report metadata and Schematron findings grouped by rule instead of one per file
 */
public record ProjectHealthCommand(
    String root,
    String rootMap,
    String schematron,
    List<String> include,
    String severity,
    boolean groupRules
) implements Command<ProjectHealthReport>, ReadOnlyCommand {

    /** The legs {@code include} can name. */
    public static final List<String> LEGS =
            List.of("reuse", "validation", "elementIds", "metadata", "schematron", "proposals");

    public ProjectHealthCommand {
        if (include != null) {
            List<String> canonical = new ArrayList<>();
            for (String leg : include) {
                if (leg == null || leg.isBlank()) {
                    continue;
                }
                String name = leg.trim();
                canonical.add(LEGS.stream().filter(l -> l.equalsIgnoreCase(name)).findFirst().orElse(name));
            }
            include = List.copyOf(canonical);
        }
    }

    /** Without a Schematron schema — the shape every caller used before the option existed. */
    public ProjectHealthCommand(String root, String rootMap) {
        this(root, rootMap, null);
    }

    /** Every leg, every severity, one finding per file: the full report the CLI prints. */
    public ProjectHealthCommand(String root, String rootMap, String schematron) {
        this(root, rootMap, schematron, null, null, false);
    }

    /** Whether the named leg runs. */
    public boolean includes(String leg) {
        return include == null || include.isEmpty() || include.contains(leg);
    }

    /** Whether only blocking findings are wanted. */
    public boolean errorsOnly() {
        return severity != null && severity.trim().equalsIgnoreCase("error");
    }

    /** Names in {@code include} that are not legs, for a clear refusal. */
    public List<String> unknownLegs() {
        return include == null ? List.of() : include.stream().filter(l -> !LEGS.contains(l)).toList();
    }
}
