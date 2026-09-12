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

import java.io.IOException;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.DogsBayOutputFormat;
import com.dogsbay.xml.XMLUtilities;

/**
 * The single canonical formatter: pretty-prints XML according to a
 * {@link FormatStyle}, so every write path (F4 action, CLI/MCP {@code format},
 * Author view) produces identical output. Wraps the existing
 * {@link XMLUtilities#format} two-pass serializer — it doesn't reimplement it.
 *
 * <p>Idempotent by contract: {@code format(format(x)) == format(x)}.
 */
public final class FormatEngine {

    private FormatEngine() {}

    /**
     * Format {@code text} to the house style.
     *
     * @param text     the XML source
     * @param systemId base URI for entity/DTD resolution (may be null)
     * @param encoding the document encoding (defaults to UTF-8 when blank)
     * @param style    the house style to apply
     * @return the formatted XML, with the style's newline + final-newline applied
     */
    public static String format(String text, String systemId, String encoding, FormatStyle style)
            throws IOException, SAXParseException {
        String enc = canonicalXmlEncoding(encoding);
        // Optionally collapse insignificant whitespace (runs of spaces/tabs, trailing and
        // block-edge whitespace) in prose/mixed content first, preserving verbatim blocks
        // and semantic line breaks. The serializer then lays the cleaned tree out.
        String source = style.trimWhitespace()
                ? XMLUtilities.normalizeWhitespace(text, systemId, enc, style)
                : text;
        String formatted = XMLUtilities.format(
                source,
                systemId,
                enc,
                style.indentString(),
                true,                          // newLines between elements
                false,                         // padText
                style.lineLengthForEngine(),   // <= 0 → no hard wrap
                true,                          // trim insignificant whitespace
                style.preserveMixed(),
                new DogsBayOutputFormat(),
                style.preserveSpaceElements(),
                style.preserveTextLineBreaks(),
                style.preserveBlankLines());
        return applyNewlines(formatted, style);
    }

    /**
     * The canonical IANA encoding name for the XML declaration (e.g. Java's "UTF8" →
     * "UTF-8"). Falls back to UTF-8 for blank/unknown encodings. Keeps the declaration
     * standards-correct regardless of whether callers pass the XML or the Java name.
     */
    static String canonicalXmlEncoding(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return "UTF-8";
        }
        try {
            return java.nio.charset.Charset.forName(encoding.trim()).name();
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    /** Normalize line endings to the style and (optionally) ensure a trailing newline. */
    static String applyNewlines(String s, FormatStyle style) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String nl = style.newline().sequence();
        String normalized = s.replace("\r\n", "\n").replace("\r", "\n");
        if (!"\n".equals(nl)) {
            normalized = normalized.replace("\n", nl);
        }
        if (style.finalNewline() && !normalized.endsWith(nl)) {
            normalized = normalized + nl;
        }
        return normalized;
    }
}
