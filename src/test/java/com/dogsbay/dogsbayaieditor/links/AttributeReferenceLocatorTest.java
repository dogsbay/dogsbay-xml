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

import com.dogsbay.dogsbayaieditor.links.AttributeReferenceLocator.Located;

/** Tests for the attribute-under-offset locator. Pure text, headless. */
class AttributeReferenceLocatorTest {

    /** Offset of the marker '|' in a probe string (marker removed). */
    private static int offsetOf(String probe) {
        return probe.indexOf('|');
    }

    private static Located locate(String probe) {
        int offset = offsetOf(probe);
        String text = probe.replace("|", "");
        return AttributeReferenceLocator.locate(text, offset);
    }

    @Test
    void keyref_offsetInsideValue() {
        Located l = locate("<p>Get <ph keyref=\"prod|uct-name\"/> now.</p>");
        assertNotNull(l);
        assertEquals("ph", l.element());
        assertEquals("keyref", l.attribute());
        assertEquals("product-name", l.value());
        assertTrue(l.isKeyReference());
        assertEquals("product-name", l.keyName());
    }

    @Test
    void keyref_atValueEdges() {
        assertNotNull(locate("<ph keyref=\"|product\"/>"), "at first char");
        assertNotNull(locate("<ph keyref=\"product|\"/>"), "just past last char");
    }

    @Test
    void conkeyref_keyNameStripsElementId() {
        Located l = locate("<p conkeyref=\"ware|house/legal\"/>");
        assertNotNull(l);
        assertEquals("conkeyref", l.attribute());
        assertEquals("warehouse", l.keyName());
    }

    @Test
    void hrefWithFragment() {
        Located l = locate("<xref href=\"topics/intro.di|ta#intro/sect\">x</xref>");
        assertNotNull(l);
        assertEquals("href", l.attribute());
        assertEquals("topics/intro.dita#intro/sect", l.value());
        assertFalse(l.isKeyReference());
        assertNull(l.keyName());
    }

    @Test
    void singleQuotedValue() {
        Located l = locate("<image src='img/pi|c.png'/>");
        assertNotNull(l);
        assertEquals("src", l.attribute());
        assertEquals("img/pic.png", l.value());
    }

    @Test
    void gtInsideAttributeValue() {
        Located l = locate("<xref href=\"weird>na|me.dita\">x</xref>");
        assertNotNull(l, "'>' is legal inside attribute values");
        assertEquals("weird>name.dita", l.value());
    }

    @Test
    void offsetInAttributeName_null() {
        assertNull(locate("<ph key|ref=\"product\"/>"));
    }

    @Test
    void offsetInTextContent_null() {
        assertNull(locate("<p>some te|xt <ph keyref=\"k\"/></p>"));
    }

    @Test
    void offsetInNonNavAttribute_null() {
        assertNull(locate("<ph id=\"my|id\" keyref=\"k\"/>"));
    }

    @Test
    void offsetInOtherAttributeOfSameTag_findsTheRightOne() {
        Located l = locate("<topicref id=\"x\" href=\"a.di|ta\" navtitle=\"T\"/>");
        assertNotNull(l);
        assertEquals("href", l.attribute());
        assertEquals("a.dita", l.value());
    }

    @Test
    void closingTagCommentPiDoctype_null() {
        assertNull(locate("</con|cept>"));
        assertNull(locate("<!-- a com|ment -->"));
        assertNull(locate("<?xml vers|ion=\"1.0\"?>"));
        assertNull(locate("<!DOCTYPE conc|ept SYSTEM \"c.dtd\">"));
    }

    @Test
    void unterminatedValue_null() {
        assertNull(locate("<ph keyref=\"unclo|sed"));
    }

    @Test
    void namespacedElement_localName() {
        Located l = locate("<xi:include href=\"part.x|ml\"/>");
        assertNotNull(l);
        assertEquals("include", l.element());
    }

    @Test
    void valueSpanOffsets() {
        String probe = "<ph keyref=\"k|ey\"/>";
        String text = probe.replace("|", "");
        Located l = AttributeReferenceLocator.locate(text, offsetOf(probe));
        assertNotNull(l);
        assertEquals("key", text.substring(l.valueStart(), l.valueEnd()));
    }

    @Test
    void nullAndOutOfRange_null() {
        assertNull(AttributeReferenceLocator.locate(null, 0));
        assertNull(AttributeReferenceLocator.locate("<a href=\"x\"/>", -1));
        assertNull(AttributeReferenceLocator.locate("<a href=\"x\"/>", 999));
    }
}
