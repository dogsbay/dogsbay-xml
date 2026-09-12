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

import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;

/**
 * Audit a DITA project against its required-metadata policy: for each topic/map,
 * check the prolog/topicmeta against the policy rules for its topic type and report
 * every violation (missing required, forbidden present, value not allowed, pattern
 * mismatch).
 *
 * <p>The policy is resolved in precedence order: an explicit {@code policy} file →
 * the shared {@code .dogsbay/config.xml} {@code <metadata-policy>} → none (no policy
 * ⇒ no findings).
 *
 * @param root   the project root to scan
 * @param scope  {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 * @param policy an explicit policy file (overrides the project config), or null
 */
public record MetadataAuditCommand(
    String root,
    String scope,
    String policy
) implements Command<BatchResult<MetadataFinding>>, ReadOnlyCommand {}
