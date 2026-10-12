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
package com.dogsbay.xml.review;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dogsbay.xml.review.Splices.Splice;
import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * Accept, reject and comment as textual splices into the document source,
 * so everything the author did not touch stays byte for byte as it was.
 * Every method returns the new document text; the caller writes it. Tags
 * that change are re-rendered from their parsed attributes, never regexed.
 */
public final class ProposalOps {

    private ProposalOps() {
    }

    public static String accept(String xml, Proposal p) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        return Splices.apply(xml, acceptSplices(xml, tokens, p));
    }

    public static String reject(String xml, Proposal p) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        return Splices.apply(xml, rejectSplices(xml, tokens, p));
    }

    /** Accept every change (not comments) by {@code author}, or by everyone when null. */
    public static String acceptAll(String xml, String author) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        List<Splice> all = new ArrayList<>();
        for (Proposal p : ProposalIndex.scan(xml, tokens)) {
            if (p.isChange() && (author == null || author.equals(p.author()))) {
                all.addAll(acceptSplices(xml, tokens, p));
            }
        }
        return Splices.apply(xml, all);
    }

    public static String rejectAll(String xml, String author) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        List<Splice> all = new ArrayList<>();
        for (Proposal p : ProposalIndex.scan(xml, tokens)) {
            if (p.isChange() && (author == null || author.equals(p.author()))) {
                all.addAll(rejectSplices(xml, tokens, p));
            }
        }
        return Splices.apply(xml, all);
    }

    /** The document as it would read with every change accepted and every comment removed. */
    public static String acceptedView(String xml) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        List<Splice> all = new ArrayList<>();
        for (Proposal p : ProposalIndex.scan(xml, tokens)) {
            all.addAll(acceptSplices(xml, tokens, p));
        }
        return Splices.apply(xml, all);
    }

    /** Insert a draft comment at {@code offset}, which must be inside mixed content. */
    public static String addComment(String xml, int offset, String author, String time, String text) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("author", author);
        attrs.put("time", time);
        attrs.put("disposition", "open");
        String tag = XmlTokens.renderTag("draft-comment", attrs, false) + XmlTokens.escapeText(text) + "</draft-comment>";
        return Splices.apply(xml, List.of(new Splice(offset, 0, tag)));
    }

    /** Set a comment's disposition (open, accepted, rejected, ...) without removing it. */
    public static String resolveComment(String xml, Proposal comment, String disposition) {
        List<Token> tokens = XmlTokens.tokenize(xml);
        Token t = tokens.get(tokenAt(tokens, comment.start()));
        Map<String, String> attrs = new LinkedHashMap<>(t.attrs());
        attrs.put("disposition", disposition);
        return Splices.apply(xml, List.of(new Splice(t.start(), t.end() - t.start(), Marks.retag(t, attrs))));
    }

    // ── splice planning ─────────────────────────────────────────────────

    private static List<Splice> acceptSplices(String xml, List<Token> tokens, Proposal p) {
        int idx = tokenAt(tokens, p.start());
        Token open = tokens.get(idx);
        int closeIdx = XmlTokens.closeOf(tokens, idx);
        return switch (p.kind()) {
            case INSERT -> p.synthetic() ? unwrap(tokens, idx, closeIdx) : List.of(strip(xml, open));
            case CHANGED -> {
                List<Splice> s = new ArrayList<>(List.of(strip(xml, open)));
                previousNote(xml, tokens, idx, closeIdx).ifPresent(c -> s.add(remove(xml, tokens, c)));
                yield s;
            }
            case DELETE -> p.wrapper() ? unwrap(tokens, idx, closeIdx) : List.of(remove(xml, tokens, idx));
            case COMMENT -> List.of(remove(xml, tokens, idx));
        };
    }

    private static List<Splice> rejectSplices(String xml, List<Token> tokens, Proposal p) {
        int idx = tokenAt(tokens, p.start());
        Token open = tokens.get(idx);
        int closeIdx = XmlTokens.closeOf(tokens, idx);
        return switch (p.kind()) {
            case INSERT -> p.wrapper() ? unwrap(tokens, idx, closeIdx) : List.of(remove(xml, tokens, idx));
            case DELETE -> p.synthetic() ? unwrap(tokens, idx, closeIdx) : List.of(strip(xml, open));
            case CHANGED -> {
                Optional<Integer> note = previousNote(xml, tokens, idx, closeIdx);
                if (note.isEmpty()) {
                    throw new IllegalStateException("this change kept no previous version to restore; "
                            + "accept it or edit the element by hand");
                }
                yield restore(xml, tokens, idx, closeIdx, note.get());
            }
            case COMMENT -> List.of(remove(xml, tokens, idx));
        };
    }

    /** Drop the start and end tags, keep the content. */
    private static List<Splice> unwrap(List<Token> tokens, int idx, int closeIdx) {
        Token open = tokens.get(idx);
        List<Splice> s = new ArrayList<>();
        s.add(new Splice(open.start(), open.end() - open.start(), ""));
        if (closeIdx > idx) {
            Token close = tokens.get(closeIdx);
            s.add(new Splice(close.start(), close.end() - close.start(), ""));
        }
        return s;
    }

    /**
     * Take an element out. One that sits alone on its lines takes its lines with it, so that accepting
     * a deleted paragraph or rejecting an inserted step leaves no blank indented line behind (the
     * structural edits put an element on its own line, and restore exactly this way).
     */
    /** Whether an element sits in text whose line breaks are content: a code block, or xml:space="preserve". */
    private static boolean preformatted(List<Token> tokens, int idx) {
        for (int p = tokens.get(idx).parent(); p >= 0; p = tokens.get(p).parent()) {
            Token t = tokens.get(p);
            String space = t.attrs().get("xml:space");
            if ("preserve".equals(space) || ChangeMarkup.PREFORMATTED.contains(t.name())) {
                return true;
            }
            if ("default".equals(space)) {
                return false;
            }
        }
        return false;
    }

    private static Splice remove(String xml, List<Token> tokens, int idx) {
        Token open = tokens.get(idx);
        int start = open.start();
        int end = XmlTokens.endOf(tokens, idx);
        int lineStart = xml.lastIndexOf('\n', start - 1) + 1;
        int newline = xml.indexOf('\n', end);
        int lineEnd = newline < 0 ? xml.length() : newline;
        if (lineStart > 0 && !preformatted(tokens, idx) && xml.substring(lineStart, start).isBlank()
                && xml.substring(end, lineEnd).isBlank()) {
            return new Splice(lineStart, (newline < 0 ? lineEnd : lineEnd + 1) - lineStart, "");
        }
        return new Splice(start, end - start, "");
    }

    /** Re-render the start tag without the proposal's marks. */
    /**
     * The tag without its review marks. When taking them off only removes attributes (nothing earlier to put
     * back), they are removed from the tag's text, so its quotes, line breaks and character references stay
     * as written; re-rendering the tag turned {@code '} into {@code "} and {@code &#169;} into
     * {@code &amp;#169;}. Otherwise the tag is rebuilt from its attributes, as before.
     */
    private static Splice strip(String xml, Token open) {
        Map<String, String> unmarked = Marks.unmarked(open);
        String tag = open.text(xml);
        boolean onlyRemoves = true;
        for (Map.Entry<String, String> a : open.attrs().entrySet()) {
            String kept = unmarked.get(a.getKey());
            if (kept == null ? !REVIEW_ATTRIBUTES.contains(a.getKey()) : !kept.equals(a.getValue())) {
                onlyRemoves = false;
            }
        }
        if (onlyRemoves && open.attrs().keySet().containsAll(unmarked.keySet())) {
            String text = tag;
            for (String name : REVIEW_ATTRIBUTES) {
                if (open.attrs().containsKey(name) && !unmarked.containsKey(name)) {
                    text = text.replaceFirst("\\s+" + name + "\\s*=\\s*(\"[^\"]*\"|'[^']*')", "");
                }
            }
            return new Splice(open.start(), open.end() - open.start(), text);
        }
        return new Splice(open.start(), open.end() - open.start(), Marks.retag(open, unmarked));
    }

    private static final List<String> REVIEW_ATTRIBUTES = List.of("status", "rev", "otherprops");

    /** The engine's previous-version note directly after the element, if any. */
    private static Optional<Integer> previousNote(String xml, List<Token> tokens, int idx, int closeIdx) {
        int from = closeIdx < 0 ? idx : closeIdx;
        for (int k = from + 1; k < tokens.size(); k++) {
            Token t = tokens.get(k);
            if (t.kind() == Kind.TEXT && t.text(xml).isBlank()) {
                continue;
            }
            if (t.kind() == Kind.START && "draft-comment".equals(t.name())
                    && ChangeMarkup.PREVIOUS_CLASS.equals(t.attrs().get("outputclass"))) {
                return Optional.of(k);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Rebuild the element exactly as the note recorded it, and drop the note. */
    private static List<Splice> restore(String xml, List<Token> tokens, int idx, int closeIdx, int noteIdx) {
        Token open = tokens.get(idx);
        int noteClose = XmlTokens.closeOf(tokens, noteIdx);
        Map<String, String> attrs = new LinkedHashMap<>();
        String inner = null;
        for (int k = noteIdx + 1; k < noteClose; k++) {
            Token t = tokens.get(k);
            if (t.kind() == Kind.EMPTY && "data".equals(t.name())) {
                String name = t.attrs().get("name");
                if (ChangeMarkup.PREVIOUS_ATTR.equals(name)) {
                    String v = t.attrs().get("value");
                    int eq = v == null ? -1 : v.indexOf('=');
                    if (eq > 0) {
                        attrs.put(v.substring(0, eq), v.substring(eq + 1));
                    }
                } else if (ChangeMarkup.PREVIOUS_XML.equals(name)) {
                    inner = t.attrs().get("value");
                }
            }
        }
        boolean empty = inner == null && open.kind() == Kind.EMPTY;
        String tag = XmlTokens.renderTag(open.name(), attrs, empty);
        List<Splice> out = new ArrayList<>();
        int end = XmlTokens.endOf(tokens, idx);
        if (inner != null) {
            out.add(new Splice(open.start(), end - open.start(), tag + inner + "</" + open.name() + ">"));
        } else {
            out.add(new Splice(open.start(), open.end() - open.start(), tag));
        }
        out.add(remove(xml, tokens, noteIdx));
        return out;
    }

    private static int tokenAt(List<Token> tokens, int start) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start() == start && (t.kind() == Kind.START || t.kind() == Kind.EMPTY)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no element starts at offset " + start + "; the document changed");
    }
}
