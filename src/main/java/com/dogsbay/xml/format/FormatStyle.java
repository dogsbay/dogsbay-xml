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

/**
 * The canonical XML "house style" — one description of formatting that every
 * write path (the F4 action, the CLI/MCP {@code format}, and the Author view)
 * obeys, so a project's files stay consistent and diffs stay small. See
 * {@code plans/format-house-style.md}.
 *
 * <p>Defaults are deliberately diff-friendly: <b>no hard-wrapping of prose</b>
 * ({@code maxLineWidth == 0} — long text stays one logical line and the editor
 * soft-wraps it visually), mixed content preserved, and a DITA space-preserve
 * list so verbatim blocks ({@code <codeblock>}, {@code <pre>}, …) are never
 * reflowed.
 *
 * @param unit                  spaces or tabs for one indent level
 * @param indentSize            spaces per level (display width when {@code unit==TABS})
 * @param maxLineWidth          hard-wrap column; {@code 0} = never hard-wrap (default)
 * @param preserveMixed         never reflow elements that mix text and child elements
 * @param preserveSpaceElements element names emitted verbatim (plus {@code xml:space="preserve"})
 * @param newline               line-ending style
 * @param finalNewline          ensure a trailing newline
 */
public record FormatStyle(
        IndentUnit unit,
        int indentSize,
        int maxLineWidth,
        boolean preserveMixed,
        Set<String> preserveSpaceElements,
        NewlineStyle newline,
        boolean finalNewline,
        boolean preserveTextLineBreaks,
        boolean preserveBlankLines,
        TextContinuation textContinuation,
        boolean trimWhitespace) {

    public enum IndentUnit { SPACES, TABS }

    /** Where the continuation lines of a reflowed paragraph sit. */
    public enum TextContinuation {
        /** Aligned with the block element's own indent (e.g. under {@code <p>}). */
        BLOCK,
        /** At column 0 — moving a block never re-indents its text (smallest diffs). */
        FLUSH
    }

    public enum NewlineStyle {
        LF("\n"), CRLF("\r\n"), PLATFORM(System.lineSeparator());

        private final String sequence;
        NewlineStyle(String sequence) { this.sequence = sequence; }
        public String sequence() { return sequence; }
    }

    /**
     * DITA elements whose whitespace is significant ({@code xml:space="preserve"} in the
     * DITA DTDs) — their content must be emitted verbatim, never indented or trimmed.
     */
    public static final Set<String> DITA_PRESERVE_SPACE =
            Set.of("codeblock", "pre", "lines", "screen");

    /** Canonical-form copy so the set can't be mutated by callers. */
    public FormatStyle {
        preserveSpaceElements = (preserveSpaceElements == null)
                ? Set.of() : Set.copyOf(preserveSpaceElements);
        if (indentSize < 0) {
            indentSize = 0;
        }
        if (maxLineWidth < 0) {
            maxLineWidth = 0;
        }
    }

    /**
     * The built-in default: 2-space structural indent, no hard-wrap, DITA verbatim
     * blocks preserved, LF, and <b>author prose line breaks preserved</b> (semantic
     * line breaks / one-sentence-per-line survive formatting).
     */
    public static FormatStyle defaults() {
        return new FormatStyle(IndentUnit.SPACES, 2, 0, true, DITA_PRESERVE_SPACE,
                NewlineStyle.LF, true, true, true, TextContinuation.BLOCK, true);
    }

    /** The continuation indent for a paragraph's later sentences at nesting {@code depth}. */
    public String continuationIndent(int depth) {
        return textContinuation == TextContinuation.FLUSH ? "" : indentString().repeat(depth);
    }

    /** The indent string for one level: N spaces, or a single tab. */
    public String indentString() {
        return unit == IndentUnit.TABS ? "\t" : " ".repeat(indentSize);
    }

    /** The hard-wrap line length for the serializer ({@code -1} = no wrap). */
    public int lineLengthForEngine() {
        return maxLineWidth <= 0 ? -1 : maxLineWidth;
    }

    /** True when {@code element} (by local name) must be emitted verbatim. */
    public boolean isPreserveSpace(String elementName) {
        return preserveSpaceElements.contains(elementName);
    }

    // ── builder-ish copies (keep records immutable but easy to derive) ──────────

    public FormatStyle withIndent(IndentUnit unit, int size) {
        return new FormatStyle(unit, size, maxLineWidth, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserveTextLineBreaks,
                preserveBlankLines, textContinuation, trimWhitespace);
    }

    public FormatStyle withMaxLineWidth(int width) {
        return new FormatStyle(unit, indentSize, width, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserveTextLineBreaks,
                preserveBlankLines, textContinuation, trimWhitespace);
    }

    public FormatStyle withNewline(NewlineStyle style) {
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                preserveSpaceElements, style, finalNewline, preserveTextLineBreaks,
                preserveBlankLines, textContinuation, trimWhitespace);
    }

    public FormatStyle withPreserveTextLineBreaks(boolean preserve) {
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserve, preserveBlankLines,
                textContinuation, trimWhitespace);
    }

    public FormatStyle withPreserveBlankLines(boolean preserve) {
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserveTextLineBreaks, preserve,
                textContinuation, trimWhitespace);
    }

    public FormatStyle withTextContinuation(TextContinuation mode) {
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserveTextLineBreaks,
                preserveBlankLines, mode, trimWhitespace);
    }

    public FormatStyle withTrimWhitespace(boolean trim) {
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                preserveSpaceElements, newline, finalNewline, preserveTextLineBreaks,
                preserveBlankLines, textContinuation, trim);
    }

    public FormatStyle withPreserveSpaceElements(Set<String> elements) {
        var merged = new LinkedHashSet<>(DITA_PRESERVE_SPACE);
        if (elements != null) {
            merged.addAll(elements);
        }
        return new FormatStyle(unit, indentSize, maxLineWidth, preserveMixed,
                merged, newline, finalNewline, preserveTextLineBreaks, preserveBlankLines,
                textContinuation, trimWhitespace);
    }
}
