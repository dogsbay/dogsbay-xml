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

package com.dogsbay.dogsbayaieditor.links.scheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates profiling-attribute values against a {@link SubjectScheme}. Given an
 * attribute and its raw value (DITA conditional attributes are whitespace-separated
 * token lists), reports a {@link ConditionFinding} for each token that a
 * <em>governed</em> attribute doesn't allow, with a near-miss suggestion.
 *
 * <p>Ungoverned attributes produce no findings (the scheme is advisory where it
 * says nothing). {@code @otherprops} group syntax ({@code group(v1 v2)}) is left
 * unchecked in v1 rather than falsely flagged.
 */
public final class ConditionChecker {

    /** Max edit distance for a suggestion, relative to the token length. */
    private static final int MIN_SUGGEST_DISTANCE = 2;

    private final SubjectScheme scheme;

    public ConditionChecker(SubjectScheme scheme) {
        this.scheme = scheme == null ? SubjectScheme.empty() : scheme;
    }

    /**
     * Check one attribute occurrence. Returns one finding per disallowed token, or
     * empty if the attribute is ungoverned / every token is legal.
     */
    public List<ConditionFinding> check(String attribute, String rawValue) {
        List<ConditionFinding> out = new ArrayList<>();
        if (attribute == null || rawValue == null || rawValue.isBlank()
                || !scheme.governs(attribute)) {
            return out;
        }
        for (String token : rawValue.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            // Group syntax group(...) — tokenizing/validating inner values is v2.
            if (token.indexOf('(') >= 0 || token.indexOf(')') >= 0) {
                continue;
            }
            if (scheme.isAllowed(attribute, token)) {
                continue;
            }
            String suggestion = nearest(token, scheme.allowedValues(attribute));
            StringBuilder msg = new StringBuilder()
                    .append('“').append(token).append('”')
                    .append(" is not a controlled value for @").append(attribute);
            if (suggestion != null) {
                msg.append(" (did you mean “").append(suggestion).append("”?)");
            }
            out.add(new ConditionFinding(attribute, token, msg.toString(), suggestion));
        }
        return out;
    }

    /** The closest allowed value within a small edit distance, or null. */
    private static String nearest(String value, Set<String> allowed) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : allowed) {
            int d = levenshtein(value, candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        int threshold = Math.max(MIN_SUGGEST_DISTANCE, value.length() / 2);
        // Also require the edit distance to be strictly less than the token length,
        // so a short token whose "nearest" value shares nothing (e.g. "qt" → "os",
        // distance 2 == length 2) isn't offered as a bogus near-miss.
        boolean closeEnough = bestDistance <= threshold && bestDistance < value.length();
        return (best != null && closeEnough) ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
