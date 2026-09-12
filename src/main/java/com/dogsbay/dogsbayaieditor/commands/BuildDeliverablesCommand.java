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

import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;

/**
 * Build a DITA project's deliverables with DITA-OT — each with its own transtype,
 * DITAVAL filter(s), publication params, and output directory. Builds one named
 * deliverable or all of them, reporting per-deliverable success + diagnostics
 * (the natural companion to {@link ValidateDeepCommand}).
 *
 * <p>Output goes to {@code <outputBaseDir>/<name>} when {@code outputBaseDir} is
 * given; else the deliverable's declared {@code <output>} (resolved against its
 * project file); else {@code <root>/out/<name>}.
 *
 * <p>Requires DITA-OT (the DITA Framework asset) — resolved from {@code ditaOtHome}
 * when given, else the project-configured engine path; absent → INVALID_ARGUMENT.
 *
 * @param root          the project root (workspace folder)
 * @param outputBaseDir    base output directory (each deliverable under a subdir), or null
 * @param deliverable      a single deliverable name, or null/blank for all deliverables
 * @param ditaOtHome       DITA-OT home override (the editor injects its path), or null
 * @param deliverableNames an explicit set of deliverable names to build — every project
 *                         deliverable whose name is in the set (so same-named ones all
 *                         build); takes precedence over {@code deliverable}. Null/empty ⇒
 *                         fall back to {@code deliverable} (single or all).
 */
public record BuildDeliverablesCommand(
    String root,
    String outputBaseDir,
    String deliverable,
    String ditaOtHome,
    List<String> deliverableNames
) implements Command<List<DeliverableBuild>> {

    /** One named deliverable (or all when {@code deliverable} is null). */
    public BuildDeliverablesCommand(String root, String outputBaseDir, String deliverable, String ditaOtHome) {
        this(root, outputBaseDir, deliverable, ditaOtHome, null);
    }
}
