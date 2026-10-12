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

package com.dogsbay.xml.format;

import java.util.Set;

/**
 * Reflows a run of prose to <b>one sentence per line</b> (semantic line breaks) —
 * a deliberate, writer-invoked operation, NOT part of always-on formatting
 * (sentence segmentation is heuristic and would not be idempotent enough to run on
 * every save; see plans/format-house-style.md).
 *
 * <p>Conservative by design: it only inserts a break after a sentence terminator
 * ({@code . ? !}) that is followed by whitespace and a capitalized word or an inline
 * tag, and it leaves the text alone when the preceding token is a known abbreviation.
 * Decimals like {@code 3.4} are safe because the dot is not followed by whitespace.
 * It never removes existing breaks or alters words.
 */
public final class SentenceReflow {

    /**
     * Lower-cased tokens (sans trailing dot) that are NOT sentence ends. Deliberately
     * excludes anything that is also a common English word ending a real sentence
     * (no, max, min, sec, co, st, al, ed, ca) — suppressing a true break is worse than
     * missing a rare abbreviation.
     */
    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g", "i.e", "etc", "vs", "mr", "mrs", "ms", "dr", "prof", "fig",
            "vol", "pp", "u.s", "u.k", "approx", "inc", "ltd", "cf", "eds", "esp");

    private SentenceReflow() {}

    /** As {@link #reflow(String, String)} with no continuation indent. */
    public static String reflow(String prose) {
        return reflow(prose, "");
    }

    /**
     * Put each sentence of {@code prose} on its own line. First collapses existing
     * whitespace (including any prior line wrapping) to single spaces, then breaks
     * after each sentence terminator, indenting continuation lines with
     * {@code continuationIndent}. Returns the input unchanged when null/blank.
     */
    public static String reflow(String prose, String continuationIndent) {
        if (prose == null || prose.isBlank()) {
            return prose;
        }
        // Collapse any existing wrapping/whitespace runs to single spaces first, so we
        // reflow from a clean single run rather than layering breaks on old ones.
        String s = prose.replaceAll("\\s+", " ");

        StringBuilder out = new StringBuilder(s.length() + 16);
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            out.append(c);
            if (c != '.' && c != '?' && c != '!') {
                continue;
            }
            // after collapsing there is exactly one space between sentences
            if (i + 2 >= n || s.charAt(i + 1) != ' ') {
                continue;   // no following space (e.g. "3.4") or end of run
            }
            char next = s.charAt(i + 2);
            boolean starts = Character.isUpperCase(next) || next == '<' || Character.isDigit(next);
            if (!starts || (c == '.' && isAbbreviation(s, i))) {
                continue;   // lower-case continuation, or an abbreviation like "e.g."
            }
            out.append('\n').append(continuationIndent);
            i++;   // skip the single space we replaced
        }
        return out.toString();
    }

    /** True when the word ending at the terminator at {@code dot} is a known abbreviation. */
    private static boolean isAbbreviation(String s, int dot) {
        int start = dot;
        while (start > 0) {
            char c = s.charAt(start - 1);
            if (Character.isLetter(c) || c == '.') {
                start--;
            } else {
                break;
            }
        }
        String word = s.substring(start, dot).toLowerCase();   // excludes the trailing dot
        // strip a leading dot left by patterns like ".g" from "e.g"
        return ABBREVIATIONS.contains(word);
    }
}
