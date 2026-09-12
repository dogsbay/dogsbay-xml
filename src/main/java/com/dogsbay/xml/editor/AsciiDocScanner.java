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

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for AsciiDoc content using regex-based tokenization.
 * Patterns derived from the Asciidoctor VS Code extension's TextMate grammar.
 */
public class AsciiDocScanner {

    // ── Block delimiters (4+ chars on their own line) ──
    private static final Pattern BLOCK_LISTING    = Pattern.compile("^-{4,}\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_EXAMPLE    = Pattern.compile("^={4,}\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_SIDEBAR    = Pattern.compile("^\\*{4,}\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_LITERAL    = Pattern.compile("^\\.{4,}\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_PASSTHRU   = Pattern.compile("^\\+{4,}\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_COMMENT    = Pattern.compile("^/{4,}\\s*$", Pattern.MULTILINE);

    // ── Tables ──
    private static final Pattern TABLE_DELIM      = Pattern.compile("^\\|===\\s*$", Pattern.MULTILINE);

    // ── Line-level patterns ──
    private static final Pattern SECTION_TITLE    = Pattern.compile("^(?:={1,5}|#{1,6})[ \\t]+\\S.+$", Pattern.MULTILINE);
    private static final Pattern ATTRIBUTE_ENTRY  = Pattern.compile("^:[\\w_-][\\w.-]*:.*$", Pattern.MULTILINE);
    private static final Pattern LINE_COMMENT     = Pattern.compile("^//[^/].*$|^//$", Pattern.MULTILINE);
    private static final Pattern ADMONITION       = Pattern.compile("^(NOTE|TIP|WARNING|CAUTION|IMPORTANT):[ \\t]+", Pattern.MULTILINE);
    private static final Pattern BLOCK_ATTR_LIST  = Pattern.compile("^\\[[ \\t]*[\\w{,.#\"'%].*\\]\\s*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_TITLE      = Pattern.compile("^\\.([^ \\t.].*)$", Pattern.MULTILINE);

    // ── List markers (with optional leading whitespace) ──
    private static final Pattern LIST_MARKER_UL   = Pattern.compile("^[ \\t]*\\*{1,5}[ \\t]+", Pattern.MULTILINE);
    private static final Pattern LIST_MARKER_OL   = Pattern.compile("^[ \\t]*\\.{1,5}[ \\t]+", Pattern.MULTILINE);
    private static final Pattern LIST_MARKER_DASH = Pattern.compile("^[ \\t]*-[ \\t]+", Pattern.MULTILINE);
    private static final Pattern DESC_LIST        = Pattern.compile("^[ \\t]*\\S.*?:{2,4}(?:$|[ \\t]+)", Pattern.MULTILINE);

    // ── Inline formatting ──
    // Unconstrained (double markers, can appear anywhere)
    private static final Pattern BOLD_UC          = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_UC        = Pattern.compile("__(.+?)__");
    private static final Pattern MONO_UC          = Pattern.compile("``(.+?)``");
    private static final Pattern HIGHLIGHT_UC     = Pattern.compile("##(.+?)##");

    // Constrained (single markers, need word boundaries)
    private static final Pattern BOLD_C           = Pattern.compile("(?<=[\\s(\\[{,;:\"']|^)\\*(\\S|\\S.*?\\S)\\*(?=[\\s)\\]},;:.!?\"']|$)");
    private static final Pattern ITALIC_C         = Pattern.compile("(?<=[\\s(\\[{,;:\"']|^)_(\\S|\\S.*?\\S)_(?=[\\s)\\]},;:.!?\"']|$)");
    private static final Pattern MONO_C           = Pattern.compile("(?<!`)`([^`]+?)`(?!`)");
    private static final Pattern HIGHLIGHT_C      = Pattern.compile("(?<!#)#(\\S|\\S.*?\\S)#(?!#)");

    // ── Passthrough ──
    private static final Pattern PASSTHROUGH_INLINE = Pattern.compile("\\+{3}(.+?)\\+{3}|pass:\\[(.+?)\\]");

    // ── Attribute references {name} ──
    private static final Pattern ATTR_REF         = Pattern.compile("(?<!\\\\)\\{(\\w[\\w-]*)\\}");

    // ── Links, xrefs, images, macros ──
    private static final Pattern LINK_MACRO       = Pattern.compile("(?<!\\\\)link:([^\\s\\[]+)\\[([^\\]]*)\\]");
    private static final Pattern URL_BARE         = Pattern.compile("(?<!\")(?:https?|ftp)://[^\\s\\[\\]<>\"]+");
    private static final Pattern XREF_SHORT       = Pattern.compile("(?<!\\\\)<<([^,>]+)(?:,([^>]+))?>>");
    private static final Pattern XREF_MACRO       = Pattern.compile("(?<!\\\\)xref:([^\\[]+)\\[([^\\]]*)\\]");
    private static final Pattern IMAGE_MACRO      = Pattern.compile("(?<!\\\\)image::?([^\\[]+)\\[([^\\]]*)\\]");
    private static final Pattern INCLUDE_MACRO    = Pattern.compile("^include::([^\\[]+)\\[([^\\]]*)\\]", Pattern.MULTILINE);
    private static final Pattern GENERIC_MACRO    = Pattern.compile("(?:^|(?<=\\s))(ifdef|ifndef|endif|ifeval|toc|footnote|kbd|btn|menu|anchor)::?([^\\[]*)?\\[([^\\]]*)\\]", Pattern.MULTILINE);

    // ── Callouts ──
    private static final Pattern CALLOUT_LIST     = Pattern.compile("^<(\\d+)>[ \\t]+", Pattern.MULTILINE);
    private static final Pattern CALLOUT_CODE     = Pattern.compile("(?://|#|;;)[ \\t]*<(\\d+)>\\s*$", Pattern.MULTILINE);

    private final Document document;
    private List<Token> tokens = Collections.emptyList();
    private Token currentToken = null;
    private int nextIndex = 0;

    public int token = -1;
    public boolean error = false;

    public AsciiDocScanner(Document document) throws IOException {
        this.document = document;
        setRange(0, document.getLength());
    }

    public void setRange(int start, int end) throws IOException {
        buildTokens();
        positionTo(start);
    }

    public int getStartOffset() {
        return (currentToken != null) ? currentToken.start : 0;
    }

    public int getEndOffset() {
        return (currentToken != null) ? currentToken.end : Integer.MAX_VALUE;
    }

    public long scan() throws IOException {
        if (nextIndex >= tokens.size()) {
            currentToken = null;
            token = -1;
            return 0;
        }
        currentToken = tokens.get(nextIndex++);
        token = currentToken.type;
        return 0;
    }

    public void cleanup() {
        tokens = Collections.emptyList();
        currentToken = null;
    }

    private void positionTo(int start) {
        nextIndex = 0;
        currentToken = null;
        token = -1;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.end > start) {
                currentToken = t;
                token = t.type;
                nextIndex = i + 1;
                return;
            }
        }
    }

    private void buildTokens() throws IOException {
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            throw new IOException(e);
        }

        List<Span> spans = new ArrayList<>();

        // Block-level paired delimiters (highest priority — contents are styled)
        collectBlockPairs(text, BLOCK_COMMENT,  Constants.AD_COMMENT, 140, spans);
        collectBlockPairs(text, BLOCK_LISTING,  Constants.AD_BLOCK_DELIMITER, 130, spans);
        collectBlockPairs(text, BLOCK_LITERAL,  Constants.AD_LITERAL, 130, spans);
        collectBlockPairs(text, BLOCK_PASSTHRU, Constants.AD_PASSTHROUGH, 130, spans);
        collectBlockPairs(text, BLOCK_EXAMPLE,  Constants.AD_BLOCK_DELIMITER, 120, spans);
        collectBlockPairs(text, BLOCK_SIDEBAR,  Constants.AD_BLOCK_DELIMITER, 120, spans);

        // Tables (just the delimiter lines)
        collectMatchSpans(text, TABLE_DELIM, Constants.AD_TABLE, 125, spans);

        // Line-level patterns
        collectMatchSpans(text, SECTION_TITLE, Constants.AD_SECTION_TITLE, 110, spans);
        collectMatchSpans(text, LINE_COMMENT, Constants.AD_COMMENT, 135, spans);
        collectMatchSpans(text, ATTRIBUTE_ENTRY, Constants.AD_ATTRIBUTE, 105, spans);
        collectMatchSpans(text, BLOCK_ATTR_LIST, Constants.AD_BLOCK_ATTR, 105, spans);
        collectMatchSpans(text, BLOCK_TITLE, Constants.AD_BLOCK_TITLE, 100, spans);
        collectAdmonitions(text, spans);
        collectMatchSpans(text, DESC_LIST, Constants.AD_DESC_LIST, 65, spans);

        // List markers
        collectMatchSpans(text, LIST_MARKER_UL, Constants.AD_LIST_MARKER, 70, spans);
        collectMatchSpans(text, LIST_MARKER_OL, Constants.AD_LIST_MARKER, 70, spans);
        collectMatchSpans(text, LIST_MARKER_DASH, Constants.AD_LIST_MARKER, 70, spans);

        // Callouts
        collectMatchSpans(text, CALLOUT_LIST, Constants.AD_CALLOUT, 110, spans);
        collectMatchSpans(text, CALLOUT_CODE, Constants.AD_CALLOUT, 110, spans);

        // Macros (include, ifdef, toc, etc.)
        collectMatchSpans(text, INCLUDE_MACRO, Constants.AD_MACRO, 100, spans);
        collectMatchSpans(text, GENERIC_MACRO, Constants.AD_MACRO, 100, spans);

        // Links, xrefs, images
        collectMatchSpans(text, LINK_MACRO, Constants.AD_LINK, 95, spans);
        collectMatchSpans(text, XREF_SHORT, Constants.AD_XREF, 95, spans);
        collectMatchSpans(text, XREF_MACRO, Constants.AD_XREF, 95, spans);
        collectMatchSpans(text, IMAGE_MACRO, Constants.AD_IMAGE, 95, spans);
        collectMatchSpans(text, URL_BARE, Constants.AD_LINK, 90, spans);

        // Inline formatting (unconstrained first — higher priority)
        collectMatchSpans(text, BOLD_UC, Constants.AD_BOLD, 88, spans);
        collectMatchSpans(text, ITALIC_UC, Constants.AD_ITALIC, 88, spans);
        collectMatchSpans(text, MONO_UC, Constants.AD_MONOSPACE, 88, spans);
        collectMatchSpans(text, HIGHLIGHT_UC, Constants.AD_HIGHLIGHT, 88, spans);

        // Constrained
        collectMatchSpans(text, MONO_C, Constants.AD_MONOSPACE, 85, spans);
        collectMatchSpans(text, BOLD_C, Constants.AD_BOLD, 82, spans);
        collectMatchSpans(text, ITALIC_C, Constants.AD_ITALIC, 82, spans);
        collectMatchSpans(text, HIGHLIGHT_C, Constants.AD_HIGHLIGHT, 82, spans);

        // Passthrough
        collectMatchSpans(text, PASSTHROUGH_INLINE, Constants.AD_PASSTHROUGH, 85, spans);

        // Attribute references {name}
        collectMatchSpans(text, ATTR_REF, Constants.AD_ATTR_REF, 80, spans);

        tokens = buildTokenList(spans, text.length());
    }

    private void collectMatchSpans(String text, Pattern pattern, int type, int priority, List<Span> spans) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (end > start) {
                spans.add(new Span(start, end, type, priority));
            }
        }
    }

    private void collectAdmonitions(String text, List<Span> spans) {
        Matcher m = ADMONITION.matcher(text);
        while (m.find()) {
            // Highlight just the label (NOTE:, TIP:, etc.)
            spans.add(new Span(m.start(), m.start() + m.group(1).length() + 1, Constants.AD_ADMONITION, 110));
        }
    }

    private void collectBlockPairs(String text, Pattern delimPattern, int type, int priority, List<Span> spans) {
        Matcher m = delimPattern.matcher(text);
        List<int[]> delimiters = new ArrayList<>();
        while (m.find()) {
            delimiters.add(new int[] { m.start(), m.end() });
        }
        for (int i = 0; i + 1 < delimiters.size(); i += 2) {
            int blockStart = delimiters.get(i)[0];
            int blockEnd = delimiters.get(i + 1)[1];
            spans.add(new Span(blockStart, blockEnd, type, priority));
        }
    }

    // ── Token list building (priority-based span merging) ──

    private List<Token> buildTokenList(List<Span> spans, int length) {
        if (length <= 0) {
            return Collections.emptyList();
        }

        List<Event> events = new ArrayList<>();
        for (Span span : spans) {
            if (span.start < span.end) {
                events.add(new Event(span.start, true, span));
                events.add(new Event(span.end, false, span));
            }
        }

        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                if (a.pos != b.pos) return Integer.compare(a.pos, b.pos);
                if (a.isStart != b.isStart) return a.isStart ? 1 : -1;
                return 0;
            }
        });

        TreeSet<Span> active = new TreeSet<>(new Comparator<Span>() {
            @Override
            public int compare(Span a, Span b) {
                if (a == b) return 0;
                int cmp = Integer.compare(b.priority, a.priority);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.start, b.start);
                if (cmp != 0) return cmp;
                return Integer.compare(System.identityHashCode(a), System.identityHashCode(b));
            }
        });

        List<Token> result = new ArrayList<>();
        int pos = 0;

        for (Event event : events) {
            if (event.pos > pos) {
                int type = active.isEmpty() ? Constants.AD_TEXT : active.first().type;
                result.add(new Token(pos, event.pos, type));
            }
            if (event.isStart) {
                active.add(event.span);
            } else {
                active.remove(event.span);
            }
            pos = event.pos;
        }

        if (pos < length) {
            int type = active.isEmpty() ? Constants.AD_TEXT : active.first().type;
            result.add(new Token(pos, length, type));
        }

        return result;
    }

    static class Token {
        final int start, end, type;
        Token(int start, int end, int type) {
            this.start = start;
            this.end = end;
            this.type = type;
        }
    }

    static class Span {
        final int start, end, type, priority;
        Span(int start, int end, int type, int priority) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.priority = priority;
        }
    }

    static class Event {
        final int pos;
        final boolean isStart;
        final Span span;
        Event(int pos, boolean isStart, Span span) {
            this.pos = pos;
            this.isStart = isStart;
            this.span = span;
        }
    }
}
