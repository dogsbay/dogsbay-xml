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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.dogsbay.xml.review.XmlTokens.Token;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.dita.dost.util.FilterUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

/**
 * The bundled DITA-OT applying the review filter to the engine's own marks.
 * DITA-OT excludes an element only when every token of the attribute is
 * excluded, so struck elements must carry {@code review-deleted} alone.
 */
class ReviewDitavalFilterTest {

    private static final FilterUtils FILTER = new FilterUtils(false,
            Map.of(new FilterUtils.FilterKey(new QName("otherprops"), Marks.DELETED), new FilterUtils.Exclude()),
            null, null);
    private final ChangeMarkup engine = new ChangeMarkup("ai:claude-acp", "2026-09-05T10:00:00Z");

    private static boolean excluded(Token t) {
        AttributesImpl atts = new AttributesImpl();
        t.attrs().forEach((k, v) -> atts.addAttribute("", k, k, "CDATA", v));
        return FILTER.needExclude(atts, new QName[0][]);
    }

    /** What DITA-OT keeps: each element's text when it survives the filter, concatenated. */
    private static String published(String xml) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        StringBuilder out = new StringBuilder();
        int skipUntil = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (i <= skipUntil) {
                continue;
            }
            if (t.kind() == XmlTokens.Kind.START && excluded(t)) {
                skipUntil = XmlTokens.closeOf(tokens, i);
            } else if (t.kind() == XmlTokens.Kind.TEXT) {
                out.append(t.text(xml));
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    @Test
    void wordChangeShipsOnlyTheNewWord() {
        String marked = engine.mark("<p>Press the green button.</p>", "<p>Press the red button.</p>");
        assertThat(published(marked)).isEqualTo("Press the red button.");
    }

    @Test
    void wholeElementDeletionWithExistingPropsIsExcluded() {
        String base = "<body><p otherprops=\"beta\" status=\"changed\" rev=\"2\">Old.</p><p>Kept.</p></body>";
        String marked = engine.mark(base, "<body><p>Kept.</p></body>");
        assertThat(marked).contains("otherprops=\"" + Marks.DELETED + "\"");
        assertThat(published(marked)).isEqualTo("Kept.");
        assertThat(ProposalOps.rejectAll(marked, null)).isEqualTo(base);
    }

    @Test
    void unwrappedContentSurvivesTheFilter() {
        String marked = engine.mark("<p><b>Save</b> the file</p>", "<p>Save the file</p>");
        assertThat(published(marked)).isEqualTo("Save the file");
        assertThat(ProposalOps.acceptedView(marked)).isEqualTo("<p>Save the file</p>");
    }

    @Test
    void insertionsAndChangedElementsAreKept() {
        String marked = engine.mark("<body><p>One.</p></body>", "<body><p>One.</p><p>Two.</p></body>");
        assertThat(published(marked)).isEqualTo("One.Two.");
    }
}
