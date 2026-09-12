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

import com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation;

/**
 * Deep-validate a DITA project with DITA-OT (the T3/T4 engine tier): run the
 * {@code dita} preprocess over a deliverable's map, applying the deliverable's
 * DITAVAL so only the content that ships is checked. Catches DTD/grammar errors,
 * keyref/conref resolution failures, broken links, circular maprefs, and
 * filtered-content errors that the static {@code validate_project} cannot.
 *
 * <p>Scope, in precedence order: a specific {@code map} (validated with the
 * DITAVAL of the deliverable that ships it, if any; else unfiltered) → a single
 * named {@code deliverable} → all deliverables. The editor's "Current Map" action
 * uses {@code map}; "All Deliverables" leaves both null.
 *
 * <p>Requires DITA-OT to be installed (the DITA Framework asset) — resolved from
 * {@code ditaOtHome} when given, else from the project's configured engine path;
 * a missing engine is an {@code INVALID_ARGUMENT} error with guidance.
 *
 * @param root        the project root (workspace folder)
 * @param deliverable a single deliverable name, or null/blank for all deliverables
 * @param map         a specific map to validate (overrides {@code deliverable}),
 *                    or null; its DITAVAL is taken from a matching deliverable
 * @param ditaOtHome  DITA-OT home override (the editor injects its resolved path),
 *                    or null to use the project-configured engine path
 */
public record ValidateDeepCommand(
    String root,
    String deliverable,
    String map,
    String ditaOtHome
) implements Command<List<DitaOtValidation>>, ReadOnlyCommand {}
