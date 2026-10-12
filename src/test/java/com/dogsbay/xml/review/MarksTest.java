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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.review.XmlTokens.Token;

class MarksTest {

    @Test
    void authorIdsHaveASchemeAndPlainRevisionsDoNot() {
        assertThat(Marks.isAuthorId("ai:claude-acp")).isTrue();
        assertThat(Marks.isAuthorId("user:alice")).isTrue();
        assertThat(Marks.isAuthorId("2.1")).isFalse();
        assertThat(Marks.isAuthorId("draft")).isFalse();
        assertThat(Marks.authorOf("2.1 ai:claude-acp")).isEqualTo("ai:claude-acp");
        assertThat(Marks.authorOf("2.1")).isNull();
        assertThat(Marks.authorOf(null)).isNull();
    }

    @Test
    void markingAndUnmarkingRoundTripAnElementsOwnAttributes() {
        Token t = XmlTokens.tokenize("<p id=\"a\" status=\"changed\" rev=\"2.1\" otherprops=\"x\">").get(0);
        Map<String, String> marked = Marks.marked(t, "new", "ai:x", List.of(Marks.DELETED));
        assertThat(marked).containsEntry("status", "new")
                .containsEntry("rev", "2.1 ai:x review-prev-status-changed review-prev-props-x")
                .containsEntry("otherprops", "review-deleted").containsEntry("id", "a");
        Token mt = XmlTokens.tokenize(XmlTokens.renderTag("p", marked, false)).get(0);
        assertThat(Marks.unmarked(mt)).containsExactly(Map.entry("id", "a"), Map.entry("status", "changed"),
                Map.entry("rev", "2.1"), Map.entry("otherprops", "x"));
    }

    @Test
    void unmarkingAnElementWithNothingElseLeavesItBare() {
        Token t = XmlTokens.tokenize("<ph status=\"new\" rev=\"ai:x review-mark\">").get(0);
        assertThat(Marks.unmarked(t)).isEmpty();
        assertThat(Marks.retag(t, Marks.unmarked(t))).isEqualTo("<ph>");
    }
}
