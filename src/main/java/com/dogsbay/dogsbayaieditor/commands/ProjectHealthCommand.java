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
 * @param root       the project root to scan
 * @param rootMap    optional root map — enables key analysis and scopes validation
 *                   to the publication set
 * @param schematron optional Schematron schema (.sch) to apply across the same
 *                   scope; null or blank to skip that leg
 */
public record ProjectHealthCommand(
    String root,
    String rootMap,
    String schematron
) implements Command<ProjectHealthReport>, ReadOnlyCommand {

    /** Without a Schematron schema — the shape every caller used before the option existed. */
    public ProjectHealthCommand(String root, String rootMap) {
        this(root, rootMap, null);
    }
}
