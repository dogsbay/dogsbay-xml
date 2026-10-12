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

import com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult;

/**
 * Validate the relationship tables in a DITA map and preview the related-links they
 * generate. Checks each cell topicref: it resolves ({@code @href} exists /
 * {@code @keyref} resolves), it targets a topic not a map, and (soft) its topic type
 * matches the column; flags degenerate rows that generate no links. Returns the
 * issues plus the projected per-topic links.
 *
 * @param map the {@code .ditamap} whose reltables to audit (keyrefs resolve against it)
 */
public record ReltableAuditCommand(String map) implements Command<ReltableAuditResult>, ReadOnlyCommand {}
