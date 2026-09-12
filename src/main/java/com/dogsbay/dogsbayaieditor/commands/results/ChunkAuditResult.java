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

package com.dogsbay.dogsbayaieditor.commands.results;

import java.util.List;

/**
 * The outcome of a {@code chunk_audit}: the {@code @chunk} inventory and any token
 * problems (unknown values or conflicting combinations).
 *
 * @param uses   every {@code @chunk} occurrence (file, line, element, value)
 * @param issues invalid tokens or conflicting combinations
 */
public record ChunkAuditResult(List<Use> uses, List<Issue> issues) {

    public ChunkAuditResult {
        uses = uses == null ? List.of() : List.copyOf(uses);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** One {@code @chunk} occurrence. */
    public record Use(String file, int line, String element, String value) {}

    /** A problem with a {@code @chunk} value. */
    public record Issue(String file, int line, String value, String problem) {}
}
