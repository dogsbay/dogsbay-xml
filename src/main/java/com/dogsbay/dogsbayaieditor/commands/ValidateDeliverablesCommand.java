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

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableValidation;

/**
 * Validate every deliverable defined for a project — each deliverable's root-map
 * publication set, validated on disk. Resolves the DITA project from a
 * {@code project.&#123;xml,json,yaml&#125;} file (else a synthesized single
 * deliverable from the default root map); returns one result per deliverable so
 * you can see which audiences/outputs are affected.
 *
 * <p>Validates the raw publication set per deliverable. DITAVAL-<em>filtered</em>
 * validation (what actually ships after conditional processing) and building the
 * deliverables both need the preprocessing engine — tracked separately.
 *
 * @param root the project root (workspace folder)
 */
public record ValidateDeliverablesCommand(
    String root
) implements Command<List<DeliverableValidation>>, ReadOnlyCommand {}
