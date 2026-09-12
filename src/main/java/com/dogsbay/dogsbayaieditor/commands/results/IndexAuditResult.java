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
 * The outcome of an {@code index_audit}: the project's index-entry inventory with
 * frequencies, topics with no index terms (coverage gaps), and dangling
 * {@code <index-see>}/{@code <index-see-also>} redirects.
 *
 * @param entries  distinct index-entry paths ("primary &gt; secondary") with topic
 *                 counts, most-used first
 * @param missing  topic files that carry no index terms
 * @param dangling {@code index-see(-also)} redirects whose target is not itself an
 *                 index entry — a reader following the cross-reference finds nothing
 */
public record IndexAuditResult(List<Entry> entries, List<String> missing,
        List<DanglingSee> dangling) {

    public IndexAuditResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        missing = missing == null ? List.of() : List.copyOf(missing);
        dangling = dangling == null ? List.of() : List.copyOf(dangling);
    }

    /** One distinct index entry and how many topics carry it. */
    public record Entry(String path, int count) {}

    /** A see/see-also redirect that points at no real index entry. */
    public record DanglingSee(String file, String from, String target, boolean also) {}
}
