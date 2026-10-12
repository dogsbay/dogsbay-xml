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

package com.dogsbay.xml.author.adapter;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;

/**
 * Pretty-prints an exported block tree in place by inserting indentation
 * whitespace between element-only children. Elements containing any text node
 * (paragraphs, codeblocks, mixed content) are left untouched, and so are inline
 * elements and elements whose children are inline: whitespace there is text,
 * so the formatting pass can never alter content.
 *
 * <p>Before the inline rule, a link holding only an element
 * ({@code <xref keyref="k"><codeph>x</codeph></xref>} inside a paragraph) was
 * indented like structure; the next import read the indentation as a space
 * inside the link, and every save changed the file again. 39 of the 303 topics
 * of the DITA-OT documentation never settled because of it (2026-09-18).
 */
public final class BlockXmlFormatter {

    private static final String INDENT = "  ";

    private BlockXmlFormatter() {
    }

    /** Indents {@code root}'s structural content recursively, using two spaces. */
    public static void indent(Element root) {
        indent(root, INDENT);
    }

    /** Indents {@code root}'s structural content using {@code indentUnit} per level
     *  (the project house style's indent), so Author-view saves match the document. */
    public static void indent(Element root, String indentUnit) {
        indent(root, 0, (indentUnit == null || indentUnit.isEmpty()) ? INDENT : indentUnit);
    }

    /** Elements that sit in running text: whitespace inside or between them is content. */
    private static final java.util.Set<String> INLINE = inlineNames();

    private static java.util.Set<String> inlineNames() {
        java.util.Set<String> names = new java.util.HashSet<>(com.dogsbay.xml.author.model.InlineStyle.DITA_INLINE_NAMES);
        names.addAll(java.util.List.of("b", "i", "u", "xref", "ph", "image", "tm", "sup", "sub", "keyword", "term"));
        return java.util.Set.copyOf(names);
    }

    /**
     * Blocks whose whitespace is content (the DITA profile's preserve-space blocks), whether or not
     * the instance says xml:space="preserve": the DTD supplies it, and it is not in the document. A
     * code block holding only a coderef was indented, and the indentation became code.
     */
    private static final java.util.Set<String> PREFORMATTED = java.util.Set.of("codeblock", "pre", "lines", "screen", "msgblock");

    static boolean isInline(Element element) {
        return INLINE.contains(element.getName());
    }

    private static void indent(Element element, int depth, String indentUnit) {
        if ("preserve".equals(element.attributeValue(org.dom4j.QName.get("space",
                org.dom4j.Namespace.XML_NAMESPACE)))) {
            return;   // whitespace here is the author's, all the way down
        }
        if (isInline(element) || PREFORMATTED.contains(element.getName())) {
            return;   // running or preformatted text: its content, all the way down, is kept as it is
        }
        List<Node> content = new ArrayList<>();
        List<Node> namespaces = new ArrayList<>();   // declarations are not content: no line of their own
        boolean hasText = false;
        for (Object node : element.content()) {
            if (node instanceof org.dom4j.Namespace) {
                namespaces.add((Node) node);
                continue;
            }
            content.add((Node) node);
            if (node instanceof Text) {
                hasText = true;
            }
        }
        // An element holding only inline elements is a line of text too (<p><b>x</b><i>y</i></p>):
        // whitespace between its children would read back as a space between words
        boolean onlyInline = !content.isEmpty() && content.stream().allMatch(n -> n instanceof Element e && isInline(e));
        if (!hasText && !onlyInline && !content.isEmpty()) {
            DocumentFactory factory = DocumentFactory.getInstance();
            element.content().clear();
            for (Node ns : namespaces) {
                element.add(ns);
            }
            for (Node node : content) {
                element.add(factory.createText("\n" + indentUnit.repeat(depth + 1)));
                element.add(node);
            }
            element.add(factory.createText("\n" + indentUnit.repeat(depth)));
        }
        for (Node node : content) {
            if (node instanceof Element child) {
                indent(child, depth + 1, indentUnit);
            }
        }
    }
}
