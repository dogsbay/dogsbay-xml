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

package com.dogsbay.xml;

/** Helpers for writing text inside a CDATA section. */
final class CdataText {

    /** The only sequence that cannot appear inside a CDATA section: it ends one. */
    private static final String TERMINATOR = "]]>";

    /** `]]` ends the section, the next one opens with `>`, and a reader joins them back up. */
    private static final String SPLIT = "]]]]><![CDATA[>";

    private CdataText() {}

    /**
     * Make {@code text} safe to place inside a single CDATA section.
     *
     * <p>A text node can perfectly well contain {@code ]]>} — the source spells it by splitting the
     * run across two sections, which is the only legal way — but writing that node back as one
     * section ended it at the first terminator and produced a document no parser would accept.
     * Splitting it again on the way out says the same characters legally.
     *
     * <p>Idempotent in effect: the result contains no {@code ]]>} outside a section boundary, so a
     * document that has been through this does not split any further on the next pass.
     *
     * @param text the CDATA content (may be null).
     * @return the content, with any {@code ]]>} split across section boundaries.
     */
    static String splitTerminators( String text) {
        if ( text == null || text.indexOf( TERMINATOR) < 0) {
            return text;
        }
        return text.replace( TERMINATOR, SPLIT);
    }
}
