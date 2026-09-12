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
 * The outcome of a {@code keyword_audit}: the project's keyword vocabulary with
 * frequencies, topics missing keywords, near-duplicate spellings (drift), and the
 * keyword co-occurrence signal (topic pairs sharing keywords) used as a relatedness
 * hint for relationship tables / related-links.
 *
 * @param vocabulary     distinct keywords with their usage counts (most-used first)
 * @param missing        topic files that carry no keywords
 * @param nearDuplicates surface-form variants of the same normalized keyword
 * @param relatedness    topic pairs that share keywords (the clustering signal), capped
 * @param truncated      relatedness pairs omitted by the cap (0 if none)
 */
public record KeywordAuditResult(List<Term> vocabulary, List<String> missing,
        List<Variant> nearDuplicates, List<Pair> relatedness, int truncated) {

    public KeywordAuditResult {
        vocabulary = vocabulary == null ? List.of() : List.copyOf(vocabulary);
        missing = missing == null ? List.of() : List.copyOf(missing);
        nearDuplicates = nearDuplicates == null ? List.of() : List.copyOf(nearDuplicates);
        relatedness = relatedness == null ? List.of() : List.copyOf(relatedness);
    }

    /** One distinct keyword and how many topics use it. */
    public record Term(String keyword, int count) {}

    /** A keyword that looks like a spelling variant of {@code canonical}. */
    public record Variant(String keyword, String canonical) {}

    /** Two topics that share keywords — a candidate relationship. */
    public record Pair(String source, String target, int shared, List<String> keywords) {
        public Pair {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }
}
