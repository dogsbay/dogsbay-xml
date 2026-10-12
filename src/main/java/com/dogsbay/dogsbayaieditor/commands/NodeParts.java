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

package com.dogsbay.dogsbayaieditor.commands;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Which stretch of the source a part of a node occupies.
 *
 * <p>The editor has recorded where every element and attribute begins and ends since long before
 * anything asked: {@code XElement} carries its element and content positions and {@code XAttribute}
 * its own. This turns those four numbers into the stretch a caller asked for — the whole element,
 * one of its tags, its content, a name, an attribute or its value.
 *
 * <p>Pure, over the numbers and the text, because the arithmetic is where the mistakes are. Three
 * things about those numbers are not what anyone would guess, and each is a different off-by-one:
 *
 * <ul>
 * <li>An element's end is <strong>exclusive</strong> and an attribute's end is exclusive, but a
 *     <strong>content end is inclusive</strong> — it is the offset of the last character of the
 *     content, not one past it. {@code <y>z</y>} reports content 29 to 29.</li>
 * <li>A <strong>self-closing</strong> element reports its content as <em>the element itself</em>:
 *     {@code <e a="1"/>} reports both as 3 to 19. There is no content and no end tag to point at.</li>
 * <li>An <strong>empty pair</strong> reports content backwards: {@code <x></x>} reports 22 to 21.
 *     That is the only honest answer — there is no character between the tags — but it is a range
 *     nothing can draw.</li>
 * </ul>
 */
public final class NodeParts {

    private NodeParts() {
    }

    /** Which part of a node to measure. */
    public enum Part {

        /** An element, from the start of its start tag to the end of its end tag. */
        ELEMENT,

        /** An element's start tag, or the whole of a self-closing element. */
        START_TAG,

        /** An element's end tag. */
        END_TAG,

        /** What lies between an element's tags. */
        CONTENT,

        /** The name in an element's start tag, or an attribute's name. */
        NAME,

        /** An attribute, name and value and the quotes around it. */
        ATTRIBUTE,

        /** An attribute's value, inside its quotes. */
        VALUE,

        /** A text node. */
        TEXT;

        /** The part called {@code name}, or null when there is no such part. */
        public static Part of(String name) {
            if (name == null) {
                return null;
            }
            String wanted = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (Part part : values()) {
                if (part.wire().equals(wanted)) {
                    return part;
                }
            }
            return null;
        }

        /** The name a caller uses: lower case, with hyphens. */
        public String wire() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** Every name, for a refusal to list. */
        public static String names() {
            return String.join(", ", Stream.of(values()).map(Part::wire).toList());
        }
    }

    /**
     * The part a caller named, or null when there is no such part.
     *
     * <p>Naming nothing means the element, which is what a caller asking "where is this node"
     * means. Here rather than in the executor so that both the default and the refusal below can be
     * asserted without an editor.
     */
    public static Part chosen(String name) {
        return name == null || name.isBlank() ? Part.ELEMENT : Part.of(name);
    }

    /** What to tell a caller that named a part nothing has. */
    public static String unknownPart(String name) {
        return "There is no part called '" + name + "'. One of: " + Part.names() + ".";
    }

    /**
     * The stretch {@code part} occupies of an element.
     *
     * @param part          which part
     * @param elementStart  where the start tag begins
     * @param elementEnd    one past the end of the element
     * @param contentStart  where the content begins — equal to {@code elementStart} for a
     *                      self-closing element
     * @param contentEnd    the <em>last character</em> of the content, which is before
     *                      {@code contentStart} when the tags hold nothing
     * @param qualifiedName the name as written, prefix and all
     */
    public static TextRange.Outcome ofElement(Part part, int elementStart, int elementEnd,
            int contentStart, int contentEnd, String qualifiedName) {
        if (elementStart < 0 || elementEnd < elementStart) {
            return refuse(unplaced("<" + qualifiedName + ">"));
        }
        boolean selfClosing = contentStart == elementStart;
        boolean nothingBetween = !selfClosing && contentEnd < contentStart;
        return switch (part) {
            case ELEMENT -> found(elementStart, elementEnd);
            case START_TAG -> selfClosing
                    ? found(elementStart, elementEnd)
                    : found(elementStart, contentStart);
            case END_TAG -> selfClosing
                    ? refuse("<" + qualifiedName + "/> is written as one empty tag, so it has no end"
                            + " tag. Ask for 'element' or 'start-tag'.")
                    : found(contentEnd + 1, elementEnd);
            case CONTENT -> {
                if (selfClosing) {
                    yield refuse("<" + qualifiedName + "/> is written as one empty tag, so it has no"
                            + " content to point at. Ask for 'element'.");
                }
                yield nothingBetween
                        ? refuse("<" + qualifiedName + "> has nothing between its tags, so there is no"
                                + " content to point at. Ask for 'element' or one of its tags.")
                        : found(contentStart, contentEnd + 1);
            }
            case NAME -> found(elementStart + 1, elementStart + 1 + qualifiedName.length());
            case ATTRIBUTE, VALUE -> refuse("'" + part.wire() + "' is a part of an attribute, and this"
                    + " selected the element <" + qualifiedName + ">. Select the attribute itself —"
                    + " an XPath ending in @name — or ask for a part of an element.");
            case TEXT -> refuse("'text' is a part of a text node, and this selected the element <"
                    + qualifiedName + ">. Ask for 'content' to point at everything between its tags.");
        };
    }

    /**
     * The stretch {@code part} occupies of an attribute.
     *
     * @param part            which part
     * @param attributeStart  where the name begins
     * @param attributeEnd    one past the closing quote
     * @param qualifiedName   the name as written, prefix and all
     * @param text            the document's text, for finding the quotes
     */
    public static TextRange.Outcome ofAttribute(Part part, int attributeStart, int attributeEnd,
            String qualifiedName, String text) {
        // Before any part is measured. An attribute a DTD supplies is in the tree and not in the
        // text, so it keeps the -1 its positions start at, and only 'value' used to notice.
        if (attributeStart < 0 || attributeEnd <= attributeStart || attributeEnd > text.length()) {
            return refuse(unplaced(qualifiedName));
        }
        return switch (part) {
            case ATTRIBUTE -> found(attributeStart, attributeEnd);
            case NAME -> found(attributeStart, attributeStart + qualifiedName.length());
            case VALUE -> valueOf(attributeStart, attributeEnd, qualifiedName, text);
            case ELEMENT, START_TAG, END_TAG, CONTENT -> refuse("'" + part.wire() + "' is a part of an"
                    + " element, and this selected the attribute " + qualifiedName + ". Ask for"
                    + " 'attribute', 'name' or 'value'.");
            case TEXT -> refuse("'text' is a part of a text node, and this selected the attribute "
                    + qualifiedName + ". Ask for 'value' to point at what it holds.");
        };
    }

    /**
     * What is inside an attribute's quotes.
     *
     * <p>Found in the text rather than computed, because either quote may be used and the name may
     * have space around the {@code =}. The closing quote is the attribute's last character; the
     * opening one is the first quote after that same character's kind appears.
     */
    private static TextRange.Outcome valueOf(int attributeStart, int attributeEnd,
            String qualifiedName, String text) {
        char closing = text.charAt(attributeEnd - 1);
        if (closing != '"' && closing != '\'') {
            return refuse("The attribute " + qualifiedName + " does not end in a quote, so its value"
                    + " cannot be told from its name. Ask for 'attribute'.");
        }
        int opening = text.indexOf(closing, attributeStart);
        // opening + 1 == attributeEnd - 1 is the two quotes side by side: a="" has a value, and it
        // is nothing, so there is no rectangle to draw over it.
        if (opening < 0 || opening + 1 >= attributeEnd - 1) {
            return refuse(qualifiedName + " has no value between quotes to point at. Ask for"
                    + " 'attribute' or 'name'.");
        }
        return found(opening + 1, attributeEnd - 1);
    }

    /**
     * What to say about a node the text does not contain.
     *
     * <p>Positions start at -1 and are stamped while the text is parsed, so anything the parser did
     * not see in the text keeps that -1. The ordinary way to meet this is a DTD default, and in a
     * DITA document that is not exotic at all: {@code @class} and {@code @domains} are fixed by the
     * DTD on essentially every element, so asking for {@code @class} is an ordinary request with no
     * ordinary answer. Said plainly, because the caller has done nothing wrong.
     */
    private static String unplaced(String name) {
        return name + " is not written in this document — a DTD or the editor supplies it — so there"
                + " is nothing on screen to point at. Ask for a part that is written, or select a"
                + " node that is.";
    }

    private static TextRange.Outcome found(int start, int end) {
        return new TextRange.Outcome.Found(new TextRange.Span(start, end));
    }

    private static TextRange.Outcome refuse(String message) {
        return new TextRange.Outcome.Refused(message);
    }
}
