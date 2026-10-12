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

import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Scan a DITA project (or a deliverable's publication set) for profiling-attribute
 * values that violate a subjectScheme's controlled values — e.g. {@code
 * platform="macos"} when the scheme only allows {@code mac}. Checks both element
 * attributes in topics/maps and {@code <prop val>} entries in DITAVAL files,
 * reporting each offending token with its file, line, and a near-miss suggestion.
 *
 * <p>The governing scheme is resolved in precedence order: an explicit {@code
 * subjectScheme} file → the subjectScheme map(s) referenced by a {@code map:}
 * scope's root map → none (no scheme ⇒ no findings, an advisory no-op).
 *
 * @param root          the project root (workspace folder) to scan within
 * @param scope         {@code "map:<path>"} (the map's publication set),
 *                      {@code "glob:<pattern>"}, or {@code "root"}/null (all files)
 * @param subjectScheme an explicit subjectScheme {@code .ditamap}, or null to
 *                      discover the scheme from the {@code map:} scope's closure
 */
public record ValidateConditionsCommand(
    String root,
    String scope,
    String subjectScheme
) implements Command<BatchResult<ConditionViolation>>, ReadOnlyCommand {}
