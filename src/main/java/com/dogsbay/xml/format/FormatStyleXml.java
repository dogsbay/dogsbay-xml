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

package com.dogsbay.xml.format;

import java.util.LinkedHashSet;
import java.util.Set;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * Reads/writes a {@link FormatStyle} as a {@code <format-style>} element for
 * {@code .dogsbay/config.xml} (the project-shared house style). Mirrors the
 * {@code MetadataPolicy} config pattern; keeps {@link FormatStyle} a pure record.
 *
 * <pre>{@code
 * <format-style indent="spaces" size="2" max-line-width="0"
 *               preserve-mixed="true" newline="lf" final-newline="true">
 *   <preserve-space>codeblock</preserve-space>
 *   <preserve-space>pre</preserve-space>
 * </format-style>
 * }</pre>
 */
public final class FormatStyleXml {

    public static final String ELEMENT = "format-style";

    private FormatStyleXml() {}

    /** Parse a {@code <format-style>} element, or null if {@code el} is null. */
    public static FormatStyle parse(Element el) {
        if (el == null) {
            return null;
        }
        FormatStyle d = FormatStyle.defaults();

        FormatStyle.IndentUnit unit = "tabs".equalsIgnoreCase(el.attributeValue("indent"))
                ? FormatStyle.IndentUnit.TABS : FormatStyle.IndentUnit.SPACES;
        int size = intAttr(el, "size", d.indentSize());
        int maxWidth = intAttr(el, "max-line-width", d.maxLineWidth());
        boolean preserveMixed = boolAttr(el, "preserve-mixed", d.preserveMixed());
        boolean finalNewline = boolAttr(el, "final-newline", d.finalNewline());
        boolean preserveTextBreaks = boolAttr(el, "preserve-text-breaks", d.preserveTextLineBreaks());
        boolean preserveBlankLines = boolAttr(el, "preserve-blank-lines", d.preserveBlankLines());
        FormatStyle.TextContinuation continuation = "flush".equalsIgnoreCase(el.attributeValue("text-continuation"))
                ? FormatStyle.TextContinuation.FLUSH : FormatStyle.TextContinuation.BLOCK;
        boolean trimWhitespace = boolAttr(el, "trim-whitespace", d.trimWhitespace());
        FormatStyle.NewlineStyle newline = switch (str(el.attributeValue("newline"))) {
            case "crlf" -> FormatStyle.NewlineStyle.CRLF;
            case "platform" -> FormatStyle.NewlineStyle.PLATFORM;
            default -> FormatStyle.NewlineStyle.LF;
        };

        Set<String> preserve = new LinkedHashSet<>(FormatStyle.DITA_PRESERVE_SPACE);
        for (Object o : el.elements("preserve-space")) {
            String name = ((Element) o).getTextTrim();
            if (name != null && !name.isBlank()) {
                preserve.add(name);
            }
        }

        return new FormatStyle(unit, size, maxWidth, preserveMixed, preserve, newline,
                finalNewline, preserveTextBreaks, preserveBlankLines, continuation, trimWhitespace);
    }

    /** Serialize a style to a {@code <format-style>} element. */
    public static Element toElement(FormatStyle style) {
        Element el = DocumentHelper.createElement(ELEMENT);
        el.addAttribute("indent", style.unit() == FormatStyle.IndentUnit.TABS ? "tabs" : "spaces");
        el.addAttribute("size", Integer.toString(style.indentSize()));
        el.addAttribute("max-line-width", Integer.toString(style.maxLineWidth()));
        el.addAttribute("preserve-mixed", Boolean.toString(style.preserveMixed()));
        el.addAttribute("newline", style.newline().name().toLowerCase());
        el.addAttribute("final-newline", Boolean.toString(style.finalNewline()));
        el.addAttribute("preserve-text-breaks", Boolean.toString(style.preserveTextLineBreaks()));
        el.addAttribute("preserve-blank-lines", Boolean.toString(style.preserveBlankLines()));
        el.addAttribute("text-continuation", style.textContinuation().name().toLowerCase());
        el.addAttribute("trim-whitespace", Boolean.toString(style.trimWhitespace()));
        // Persist only the extras beyond the built-in DITA defaults, to keep config terse.
        for (String name : style.preserveSpaceElements()) {
            if (!FormatStyle.DITA_PRESERVE_SPACE.contains(name)) {
                el.addElement("preserve-space").setText(name);
            }
        }
        return el;
    }

    private static int intAttr(Element el, String name, int fallback) {
        try {
            String v = el.attributeValue(name);
            return (v == null || v.isBlank()) ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolAttr(Element el, String name, boolean fallback) {
        String v = el.attributeValue(name);
        return (v == null || v.isBlank()) ? fallback : Boolean.parseBoolean(v.trim());
    }

    private static String str(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
