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

package com.dogsbay.dogsbayaieditor.links.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Validates that {@link SourcePositions} offsets line up with the original source text. */
class SourcePositionsTest {

    /** Substring at each element's open/close spans must be the actual tags. */
    private static void assertSpansMatch(String src, String... preorderNames) throws Exception {
        SourcePositions pos = SourcePositions.of(src);
        assertEquals(preorderNames.length, pos.size(), "element count");
        for (int i = 0; i < preorderNames.length; i++) {
            String name = preorderNames[i];
            SourcePositions.Span s = pos.get(i);
            String open = src.substring(s.openTagStart(), s.openTagEnd());
            assertTrue(open.startsWith("<" + name), "open tag #" + i + " = <" + name + ">, got: " + open);
            assertTrue(open.endsWith(">"), "open tag ends with > : " + open);
            if (s.selfClosing()) {
                assertTrue(open.endsWith("/>"), "self-closing: " + open);
            } else {
                assertEquals("</" + name + ">", src.substring(s.closeTagStart(), s.closeTagEnd()),
                        "close tag #" + i + " for " + name);
            }
        }
    }

    @Test
    void simpleNestedElements() throws Exception {
        String src = "<?xml version=\"1.0\"?>\n"
                + "<concept id=\"x\">\n"
                + "  <title>Hi</title>\n"
                + "  <conbody>\n"
                + "    <p>text</p>\n"
                + "  </conbody>\n"
                + "</concept>\n";
        assertSpansMatch(src, "concept", "title", "conbody", "p");
    }

    @Test
    void selfClosingAndAttributes() throws Exception {
        String src = "<concept id=\"x\">\n"
                + "  <prolog>\n"
                + "    <critdates>\n"
                + "      <created date=\"2024-06-01\"/>\n"
                + "    </critdates>\n"
                + "  </prolog>\n"
                + "</concept>\n";
        assertSpansMatch(src, "concept", "prolog", "critdates", "created");
        SourcePositions pos = SourcePositions.of(src);
        assertTrue(pos.get(3).selfClosing(), "created is self-closing");
        assertEquals("      ", pos.get(3).indent(), "created indented 6 spaces");
        assertEquals("  ", pos.get(1).indent(), "prolog indented 2 spaces");
    }

    @Test
    void crlfAndDoctype() throws Exception {
        String src = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\r\n"
                + "\r\n"
                + "<concept id=\"x\">\r\n"
                + "  <title>T</title>\r\n"
                + "</concept>\r\n";
        assertSpansMatch(src, "concept", "title");
    }

    @Test
    void attributesSpanningLines() throws Exception {
        String src = "<concept\n    id=\"x\"\n    audience=\"admin\">\n"
                + "  <title>T</title>\n"
                + "</concept>\n";
        assertSpansMatch(src, "concept", "title");
    }
}
