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

import com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult;

/**
 * Audit DITA 1.3 chunking ({@code @chunk}) across a project's maps: the inventory of
 * {@code @chunk} usages and validation of their token values against the DITA 1.3
 * vocabulary ({@code select-branch}/{@code select-document}/{@code select-topic}/
 * {@code by-topic}/{@code by-document}/{@code to-content}/{@code to-navigation}),
 * flagging unknown tokens and conflicting combinations.
 *
 * @param root  the project root to scan
 * @param scope {@code "map:<path>"}, {@code "glob:<pattern>"}, or {@code "root"}/null
 */
public record ChunkAuditCommand(String root, String scope)
        implements Command<ChunkAuditResult>, ReadOnlyCommand {}
