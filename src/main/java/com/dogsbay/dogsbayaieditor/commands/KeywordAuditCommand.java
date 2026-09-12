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

import com.dogsbay.dogsbayaieditor.commands.results.KeywordAuditResult;

/**
 * Survey the {@code <keyword>} vocabulary across a DITA project: distinct keywords +
 * frequencies, topics with none, near-duplicate spellings (drift), and the keyword
 * co-occurrence signal (topic pairs sharing keywords) — the deterministic relatedness
 * hint behind the {@code dita-keywords}/{@code dita-reltables} skill workflows.
 *
 * @param root  the project root to scan
 * @param scope {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 */
public record KeywordAuditCommand(String root, String scope)
        implements Command<KeywordAuditResult>, ReadOnlyCommand {}
