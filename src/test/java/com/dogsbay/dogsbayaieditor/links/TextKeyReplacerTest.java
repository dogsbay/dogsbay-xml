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

package com.dogsbay.dogsbayaieditor.links;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextKeyReplacerTest {

    private static final String STUB = "<ph keyref=\"product\"/>";

    @Test
    void replacesInRegularTextContent() {
        String content = "<concept id=\"c\"><title>About DogsBay XML</title><conbody>"
                + "<p>DogsBay XML is great. Use DogsBay XML daily.</p>"
                + "</conbody></concept>";
        String result = TextKeyReplacer.replace(content, "DogsBay XML", STUB);
        assertEquals("<concept id=\"c\"><title>About " + STUB + "</title><conbody>"
                + "<p>" + STUB + " is great. Use " + STUB + " daily.</p>"
                + "</conbody></concept>", result);
    }

    @Test
    void neverTouchesAttributes() {
        String content = "<concept id=\"c\"><conbody>"
                + "<p product=\"DogsBay XML\">DogsBay XML</p>"
                + "<xref href=\"DogsBay XML.dita\">see</xref>"
                + "</conbody></concept>";
        String result = TextKeyReplacer.replace(content, "DogsBay XML", STUB);
        assertTrue(result.contains("product=\"DogsBay XML\""), "attribute untouched");
        assertTrue(result.contains("href=\"DogsBay XML.dita\""), "href untouched");
        assertTrue(result.contains(">" + STUB + "</p>"), "text content replaced");
    }

    @Test
    void neverTouchesCodeContexts() {
        String content = "<concept id=\"c\"><conbody>"
                + "<p>DogsBay XML rocks.</p>"
                + "<codeblock>install DogsBay XML --now</codeblock>"
                + "<p>Run <codeph>DogsBay XML</codeph> or <filepath>DogsBay XML</filepath>.</p>"
                + "<pre>DogsBay XML</pre>"
                + "</conbody></concept>";
        String result = TextKeyReplacer.replace(content, "DogsBay XML", STUB);
        assertTrue(result.contains("<p>" + STUB + " rocks.</p>"));
        assertTrue(result.contains("<codeblock>install DogsBay XML --now</codeblock>"));
        assertTrue(result.contains("<codeph>DogsBay XML</codeph>"));
        assertTrue(result.contains("<filepath>DogsBay XML</filepath>"));
        assertTrue(result.contains("<pre>DogsBay XML</pre>"));
    }

    @Test
    void neverTouchesKeywordCommentsOrCdata() {
        String content = "<concept id=\"c\"><conbody>"
                + "<p><keyword>DogsBay XML</keyword></p>"
                + "<!-- DogsBay XML in a comment -->"
                + "<p><![CDATA[DogsBay XML literal]]></p>"
                + "</conbody></concept>";
        assertNull(TextKeyReplacer.replace(content, "DogsBay XML", STUB),
                "nothing replaceable → null");
    }

    @Test
    void wholeWordOnly() {
        String content = "<concept id=\"c\"><conbody>"
                + "<p>XML is fine but XMLplus and myXML are different words.</p>"
                + "</conbody></concept>";
        String result = TextKeyReplacer.replace(content, "XML", STUB);
        assertTrue(result.contains("<p>" + STUB + " is fine"), "standalone replaced");
        assertTrue(result.contains("XMLplus"), "prefix match skipped");
        assertTrue(result.contains("myXML"), "suffix match skipped");
    }

    @Test
    void nestedExclusion_subtreeFullySkipped() {
        String content = "<concept id=\"c\"><conbody>"
                + "<codeblock>before <b>DogsBay XML</b> after</codeblock>"
                + "</conbody></concept>";
        assertNull(TextKeyReplacer.replace(content, "DogsBay XML", STUB),
                "markup inside an excluded subtree stays excluded");
    }
}
