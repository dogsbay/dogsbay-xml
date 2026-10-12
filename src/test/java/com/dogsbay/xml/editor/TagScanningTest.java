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

package com.dogsbay.xml.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tag parsing driven straight off the content store.
 *
 * <p>{@link Tag} reads the document through {@code BufferContent.getChar} — the
 * scanner's hot path, and the only method {@code BufferContent} adds to the
 * public {@code GapContent} it now extends. Nothing else covered that
 * integration, so rebuilding the store on public API (see
 * {@code plans/sun-code-investigation.md}) could have changed tag recognition
 * with the suite still green.
 *
 * <p>Each case is run twice: once on a freshly filled buffer, and once after an
 * edit has parked the gap <em>inside</em> the tag being parsed. The second is
 * what actually exercises the offset arithmetic — a wrong gap adjustment shows
 * up as a mangled name or type.
 */
class TagScanningTest {

    /** Content holding exactly {@code text}, gap at the end. */
    private static BufferContent flat(String text) throws Exception {
        BufferContent content = new BufferContent();
        content.insertString(0, text);
        return content;
    }

    /**
     * Content holding {@code text} with the gap parked mid-string: the text is
     * built by inserting the tail first and then splicing the head in, which
     * leaves the hole between them.
     */
    private static BufferContent gapped(String text, int splitAt) throws Exception {
        BufferContent content = new BufferContent();
        content.insertString(0, text.substring(splitAt));
        content.insertString(0, text.substring(0, splitAt));
        return content;
    }

    private static Tag tagOf(BufferContent content, String text) {
        return new Tag(content, 0, text.length() - 1);
    }

    // ── Element names ───────────────────────────────────────────────────

    @Test
    void readsAStartTagName() throws Exception {
        String xml = "<concept>";

        assertEquals("concept", tagOf(flat(xml), xml).getName());
        assertEquals("concept", tagOf(gapped(xml, 4), xml).getName(),
                "gap parked inside the element name");
    }

    @Test
    void readsAnEndTag() throws Exception {
        String xml = "</concept>";

        assertEquals(Tag.END_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.END_TAG, tagOf(gapped(xml, 5), xml).getType());
        assertEquals("concept", tagOf(gapped(xml, 5), xml).getName());
    }

    @Test
    void readsAnEmptyTag() throws Exception {
        String xml = "<image href=\"a.png\"/>";

        assertEquals(Tag.EMPTY_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.EMPTY_TAG, tagOf(gapped(xml, 8), xml).getType());
        assertEquals("image", tagOf(gapped(xml, 8), xml).getName());
    }

    @Test
    void readsAQualifiedName() throws Exception {
        String xml = "<dita:topic>";
        Tag tag = tagOf(gapped(xml, 7), xml);

        assertEquals("dita", tag.getPrefix());
        assertEquals("topic", tag.getName());
        assertEquals("dita:topic", tag.getQualifiedName());
    }

    // ── Non-element constructs ──────────────────────────────────────────

    @Test
    void recognisesAComment() throws Exception {
        String xml = "<!-- a note -->";

        assertEquals(Tag.COMMENT_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.COMMENT_TAG, tagOf(gapped(xml, 7), xml).getType());
    }

    @Test
    void recognisesCdata() throws Exception {
        String xml = "<![CDATA[ <not a tag> ]]>";

        assertEquals(Tag.CDATA_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.CDATA_TAG, tagOf(gapped(xml, 12), xml).getType());
    }

    @Test
    void recognisesAProcessingInstruction() throws Exception {
        String xml = "<?xml version=\"1.0\"?>";

        assertEquals(Tag.PI_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.PI_TAG, tagOf(gapped(xml, 9), xml).getType());
    }

    @Test
    void recognisesADoctypeDeclaration() throws Exception {
        String xml = "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">";

        assertEquals(Tag.DECLARATION_TAG, tagOf(flat(xml), xml).getType());
        assertEquals(Tag.DECLARATION_TAG, tagOf(gapped(xml, 30), xml).getType());
    }

    // ── Attributes ──────────────────────────────────────────────────────

    @Test
    void readsAttributeNamesAcrossTheGap() throws Exception {
        String xml = "<topic id=\"intro\" audience=\"admin\">";
        Tag tag = tagOf(gapped(xml, 20), xml);

        assertEquals(java.util.List.of("id", "audience"),
                java.util.List.copyOf(tag.getAttributeNames()));
    }

    @Test
    void readsAttributeValuesAcrossTheGap() throws Exception {
        String xml = "<topic id=\"intro\" audience=\"admin\">";
        Tag tag = tagOf(gapped(xml, 20), xml);

        assertEquals(java.util.List.of("intro", "admin"),
                java.util.List.copyOf(tag.getAttributeValues()));
    }

    @Test
    void locatesTheOffsetInsideAnAttributeValue() throws Exception {
        String xml = "<topic id=\"intro\">";
        Tag flatTag = tagOf(flat(xml), xml);
        Tag gappedTag = tagOf(gapped(xml, 12), xml);

        // Same answer whichever side of the gap the value sits on.
        assertEquals(flatTag.getAttributeValueOffset("id"),
                gappedTag.getAttributeValueOffset("id"));
        assertTrue(flatTag.inAttributeValue(flatTag.getAttributeValueOffset("id")));
        assertTrue(gappedTag.inAttributeValue(gappedTag.getAttributeValueOffset("id")));
    }

    // ── Incomplete input ────────────────────────────────────────────────

    /**
     * {@code isIncomplete()} does not mean "no closing angle bracket" — it scans
     * from just after the leading {@code <} and reports whether *another* {@code <}
     * appears inside the tag's span, i.e. the range has swallowed the start of the
     * next tag because this one was never closed.
     */
    @Test
    void flagsATagSpanThatSwallowedTheNextTag() throws Exception {
        String xml = "<topic id=\"intro\" <p>";

        assertTrue(tagOf(flat(xml), xml).isIncomplete());
        assertTrue(tagOf(gapped(xml, 10), xml).isIncomplete(),
                "the nested < sits on the far side of the gap");
    }

    @Test
    void aWellFormedTagIsNotFlagged() throws Exception {
        String xml = "<topic id=\"intro\">";

        assertFalse(tagOf(flat(xml), xml).isIncomplete());
        assertFalse(tagOf(gapped(xml, 10), xml).isIncomplete());
    }
}
