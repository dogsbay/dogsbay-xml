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

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

class XmlTokensTest {

    @Test
    void tokenizesTagsTextAndAuxiliariesWithExactOffsets() {
        String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE topic SYSTEM \"topic.dtd\">\n<topic id=\"t\">"
                + "<!-- c --><title>Hi &amp; bye</title><p a='1' b=\"x > y\">text<br/></p><![CDATA[<raw>]]></topic>";
        List<Token> t = XmlTokens.tokenize(xml);
        assertThat(t).extracting(Token::kind).containsExactly(Kind.PI, Kind.TEXT, Kind.DOCTYPE, Kind.TEXT,
                Kind.START, Kind.COMMENT, Kind.START, Kind.TEXT, Kind.END, Kind.START, Kind.TEXT, Kind.EMPTY,
                Kind.END, Kind.CDATA, Kind.END);
        for (Token tok : t) {
            assertThat(xml.substring(tok.start(), tok.end())).isEqualTo(tok.text(xml));
        }
        Token topic = t.get(4);
        assertThat(topic.name()).isEqualTo("topic");
        assertThat(topic.attrs()).containsEntry("id", "t");
        Token p = t.get(9);
        assertThat(p.attrs()).containsEntry("a", "1").containsEntry("b", "x > y");
        assertThat(t.get(10).parent()).isEqualTo(9);
        assertThat(t.get(10).depth()).isEqualTo(2);
        assertThat(XmlTokens.closeOf(t, 9)).isEqualTo(12);
        assertThat(XmlTokens.closeOf(t, 11)).isEqualTo(11);
        assertThat(XmlTokens.closeOf(t, 4)).isEqualTo(14);
        assertThat(XmlTokens.ancestor(t, 10, java.util.Set.of("topic"))).isEqualTo(4);
    }

    @Test
    void attributeParsingHandlesQuotesAndEntities() {
        assertThat(XmlTokens.parseAttrs(" a=\"1\"  b = '2'  c=\"&lt;&amp;&quot;\" d"))
                .containsEntry("a", "1").containsEntry("b", "2").containsEntry("c", "<&\"").containsEntry("d", "");
    }

    @Test
    void malformedInputDegradesToTextNotAnException() {
        List<Token> t = XmlTokens.tokenize("<a><b>unclosed <c attr=\"oops");
        assertThat(t).isNotEmpty();
        assertThat(t.get(t.size() - 1).end()).isEqualTo("<a><b>unclosed <c attr=\"oops".length());
    }
}
