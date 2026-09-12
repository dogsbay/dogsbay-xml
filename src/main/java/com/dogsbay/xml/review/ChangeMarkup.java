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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.dogsbay.xml.review.Myers.Op;
import com.dogsbay.xml.review.Myers.Step;
import com.dogsbay.xml.review.Splices.Splice;
import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * The diff-to-markup engine: given a document's baseline and a modified
 * version, rewrite the modified version so the delta appears as proposals
 * for one author, in DITA's own markup, by textual splices.
 *
 * <p>Both versions are tokenized and aligned with a Myers diff in which tags
 * match by name and text by content. The non-matching runs are then read as
 * a small vocabulary of edits, each with an exact undo:
 *
 * <ul>
 * <li><b>text</b>: a word-level diff inside the text node, each hunk a
 *     synthetic {@code <ph>} pair carrying {@code review-mark};</li>
 * <li><b>whole element added or removed</b>: the element marked new, or the
 *     baseline element put back marked deleted;</li>
 * <li><b>wrapper added or removed</b>: content that was already there got an
 *     element around it, or lost one; the wrapper is marked with
 *     {@code review-wrapper} so undoing it unwraps rather than deletes;</li>
 * <li><b>element changed</b> (attributes, a rename, or text where
 *     {@code <ph>} is not allowed): the old element kept as deleted next to
 *     the new one marked new. When a validator says that pair is invalid
 *     because the element may not repeat, the new element is marked changed
 *     and the previous version is kept, lossless, in a note beside it.</li>
 * </ul>
 *
 * <p>Two invariants hold: accepting every mark gives the modified text byte
 * for byte, and rejecting every mark gives the baseline, modulo whitespace
 * between elements. A delta too large to review as marks (thousands of
 * differences) raises {@link Myers.TooDifferent} instead of producing
 * something worse than an overwrite. Whitespace-only differences are not
 * marked. Existing proposals in either version are ordinary markup.
 */
public final class ChangeMarkup {

    /** The delta is too large to review as marks; the caller decides what to do instead. */
    public static final class TooDifferent extends RuntimeException {
        TooDifferent(String message) {
            super(message);
        }
    }

    public static final String PREVIOUS_CLASS = "dogsbay-previous";
    public static final String PREVIOUS_ATTR = "dogsbay:previous-attr";
    public static final String PREVIOUS_XML = "dogsbay:previous-xml";

    /** Elements whose content model has no {@code <ph>}, plus the prolog. */
    /** Elements whose line breaks are content: a change on each line stays its own proposal. */
    static final Set<String> PREFORMATTED = Set.of("pre", "codeblock", "screen", "msgblock", "lines");

    static final Set<String> NO_PH = Set.of("keyword", "tm", "indexterm", "index-see", "index-see-also",
            "apiname", "cmdname", "kwd", "msgnum", "option", "parmname", "shortcut", "text", "varname", "wintitle",
            "shape", "uicontrol", "filepath", "userinput", "systemoutput", "msgph", "var", "oper", "delim", "sep",
            "repsep", "synph", "author", "publisher", "copyrholder", "brand", "category", "component", "featnum",
            "platform", "prodname", "prognum", "series", "source", "resourceid", "navtitle", "searchtitle",
            "linktext", "prolog");

    private final String author;
    private final String time;
    private final Predicate<String> isValid;

    public ChangeMarkup(String author, String time) {
        this(author, time, null);
    }

    /**
     * @param isValid decides whether a candidate marked document is valid. With
     *                it, element changes are first tried as restorable pairs and
     *                fall back to a changed mark with a previous-version note
     *                when the pair is invalid. Without it, pairs are always used.
     */
    public ChangeMarkup(String author, String time, Predicate<String> isValid) {
        if (!Marks.isAuthorId(author)) {
            throw new IllegalArgumentException("author must be an id like ai:claude-acp or user:alice, not '"
                    + author + "'");
        }
        this.author = author;
        this.time = time;
        this.isValid = isValid;
    }

    /** {@code modified} with its delta from {@code baseline} marked as proposals. */
    public String mark(String baseline, String modified) {
        if (baseline.equals(modified)) {
            return modified;
        }
        List<Token> b = XmlTokens.tokenize(baseline);
        List<Token> m = XmlTokens.tokenize(modified);
        if (!XmlTokens.isBalanced(m)) {
            throw new IllegalArgumentException("the modified document is not well-formed; nothing to mark");
        }
        if (!XmlTokens.isBalanced(b)) {
            throw new IllegalArgumentException("the baseline document is not well-formed; nothing to compare with");
        }
        Plan plan = new Plan(baseline, modified, b, m);
        try {
            plan.analyse(Myers.diff(b, m, (x, y) -> same(x, baseline, y, modified)));
        } catch (Myers.TooDifferent e) {
            throw new TooDifferent(e.getMessage());
        }

        String candidate = Splices.apply(modified, plan.render(false));
        if (isValid == null || plan.elementChanges.isEmpty() || isValid.test(candidate)) {
            return candidate;
        }
        return Splices.apply(modified, plan.render(true));
    }

    // ── alignment ───────────────────────────────────────────────────────

    /**
     * Tags match by kind and name; text by content with whitespace collapsed;
     * the rest exactly. Equal text that moved into a new element reads as a
     * wrapper change, which undoes correctly either way.
     */
    private static boolean same(Token x, String xs, Token y, String ys) {
        if (x.kind() != y.kind()) {
            return false;
        }
        if (x.isTag()) {
            return x.name().equals(y.name());
        }
        if (x.kind() == Kind.TEXT) {
            return collapse(x.text(xs)).equals(collapse(y.text(ys)));
        }
        return x.text(xs).equals(y.text(ys));
    }

    private static String collapse(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    static boolean phAllowed(List<Token> tokens, int index) {
        Token t = tokens.get(index);
        int parent = t.kind() == Kind.TEXT ? t.parent() : t.parent();
        if (parent >= 0 && NO_PH.contains(tokens.get(parent).name())) {
            return false;
        }
        return XmlTokens.ancestor(tokens, index, Set.of("prolog")) < 0;
    }

    /** Words and the whitespace between them, as separate tokens, so the join is exact. */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int j = i;
            boolean ws = Character.isWhitespace(text.charAt(i));
            while (j < n && Character.isWhitespace(text.charAt(j)) == ws) {
                j++;
            }
            out.add(text.substring(i, j));
            i = j;
        }
        return out;
    }

    // ── the plan: edits read from the diff, then rendered ───────────────

    final class Plan {
        final String baseline;
        final String modified;
        final List<Token> b;
        final List<Token> m;
        final Map<Integer, Integer> bToM = new HashMap<>();
        final Map<Integer, Integer> mToB = new HashMap<>();
        /** m START index -> b START index, for pairs (attributes, rename, text-only text). */
        final Map<Integer, Integer> elementChanges = new LinkedHashMap<>();
        final List<int[]> textPairs = new ArrayList<>();         // {bIdx, mIdx}
        final List<Integer> wholeInserts = new ArrayList<>();    // m START idx
        final List<Integer> wrapperInserts = new ArrayList<>();  // m START idx
        final List<Integer> wholeDeletes = new ArrayList<>();    // b START idx
        final List<int[]> wrapperDeletes = new ArrayList<>();    // {bIdx, mFirstInside, mLastInside}
        final List<Integer> loneInserts = new ArrayList<>();     // m TEXT idx
        final List<Integer> loneDeletes = new ArrayList<>();     // b TEXT idx
        final Set<Integer> insertedM = new HashSet<>();
        final Set<Integer> deletedB = new HashSet<>();

        Plan(String baseline, String modified, List<Token> b, List<Token> m) {
            this.baseline = baseline;
            this.modified = modified;
            this.b = b;
            this.m = m;
        }

        void analyse(List<Step> raw) {
            for (Step s : raw) {
                if (s.op() == Op.EQUAL) {
                    bToM.put(s.a(), s.b());
                    mToB.put(s.b(), s.a());
                }
            }
            // A tag-level diff can pair a START with one element and its END with
            // another. Break such pairs so every aligned element is aligned whole.
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<Integer, Integer> e : new ArrayList<>(bToM.entrySet())) {
                    Token bt = b.get(e.getKey());
                    if (bt.kind() == Kind.START) {
                        int bc = XmlTokens.closeOf(b, e.getKey());
                        int mc = XmlTokens.closeOf(m, e.getValue());
                        if (bc >= 0 && mc >= 0 && !Integer.valueOf(mc).equals(bToM.get(bc))) {
                            unalign(e.getKey(), e.getValue());
                            if (bToM.containsKey(bc)) {
                                unalign(bc, bToM.get(bc));
                            }
                            if (mToB.containsKey(mc)) {
                                unalign(mToB.get(mc), mc);
                            }
                            changed = true;
                        }
                    } else if (bt.kind() == Kind.END) {
                        int bo = openOf(b, e.getKey());
                        int mo = openOf(m, e.getValue());
                        if (bo >= 0 && mo >= 0 && !Integer.valueOf(mo).equals(bToM.get(bo))) {
                            unalign(e.getKey(), e.getValue());
                            changed = true;
                        }
                    }
                }
            }
            List<Step> steps = rebuild();
            for (Step s : steps) {
                if (s.op() == Op.INSERT) {
                    insertedM.add(s.b());
                } else if (s.op() == Op.DELETE) {
                    deletedB.add(s.a());
                }
            }
            // Aligned tokens that still differ: attributes, or text that collapsed equal but is not.
            for (Step s : steps) {
                if (s.op() != Op.EQUAL) {
                    continue;
                }
                Token bt = b.get(s.a());
                Token mt = m.get(s.b());
                if (bt.isTag() && bt.kind() != Kind.END && !bt.attrs().equals(mt.attrs())) {
                    elementChanges.put(s.b(), s.a());
                }
            }
            Set<Integer> coveredB = new HashSet<>();
            Set<Integer> coveredM = new HashSet<>();
            int k = 0;
            while (k < steps.size()) {
                if (steps.get(k).op() == Op.EQUAL) {
                    k++;
                    continue;
                }
                int end = k;
                while (end < steps.size() && steps.get(end).op() != Op.EQUAL) {
                    end++;
                }
                analyseRun(steps.subList(k, end), coveredB, coveredM);
                k = end;
            }
        }

        private void unalign(int bi, int mi) {
            bToM.remove(bi);
            mToB.remove(mi);
        }

        /** The steps implied by the (monotone) alignment maps. */
        private List<Step> rebuild() {
            List<Step> out = new ArrayList<>();
            int i = 0;
            int j = 0;
            while (i < b.size() || j < m.size()) {
                if (i < b.size() && bToM.containsKey(i)) {
                    int target = bToM.get(i);
                    while (j < target) {
                        out.add(new Step(Op.INSERT, -1, j++));
                    }
                    out.add(new Step(Op.EQUAL, i++, j++));
                } else if (i < b.size()) {
                    out.add(new Step(Op.DELETE, i++, -1));
                } else {
                    out.add(new Step(Op.INSERT, -1, j++));
                }
            }
            return out;
        }

        private static int openOf(List<Token> tokens, int endIndex) {
            int depth = 0;
            for (int k = endIndex; k >= 0; k--) {
                Token t = tokens.get(k);
                if (t.kind() == Kind.END) {
                    depth++;
                } else if (t.kind() == Kind.START) {
                    depth--;
                    if (depth == 0) {
                        return k;
                    }
                }
            }
            return -1;
        }

        private void analyseRun(List<Step> run, Set<Integer> coveredB, Set<Integer> coveredM) {
            List<Integer> ins = run.stream().filter(s -> s.op() == Op.INSERT).map(Step::b).toList();
            List<Integer> del = run.stream().filter(s -> s.op() == Op.DELETE).map(Step::a).toList();
            List<Integer> insText = new ArrayList<>();
            List<Integer> delText = new ArrayList<>();

            for (int idx : ins) {
                if (coveredM.contains(idx)) {
                    continue;
                }
                Token t = m.get(idx);
                if (t.kind() == Kind.START || t.kind() == Kind.EMPTY) {
                    int close = XmlTokens.closeOf(m, idx);
                    boolean endInserted = close == idx || insertedM.contains(close);
                    if (!endInserted) {
                        // renamed element: its END aligned with some other element's END
                        int bStart = renamedPartner(idx, del);
                        if (bStart >= 0) {
                            elementChanges.put(idx, bStart);
                            cover(coveredM, idx, close, m);
                            cover(coveredB, bStart, XmlTokens.closeOf(b, bStart), b);
                            continue;
                        }
                    }
                    boolean allInsideInserted = true;
                    for (int j = idx + 1; j < close; j++) {
                        if (!insertedM.contains(j)) {
                            allInsideInserted = false;
                            break;
                        }
                    }
                    if (allInsideInserted) {
                        wholeInserts.add(idx);
                        cover(coveredM, idx, close, m);
                    } else {
                        wrapperInserts.add(idx);
                        coveredM.add(idx);
                        coveredM.add(close);
                    }
                } else if (t.kind() == Kind.TEXT && !t.text(modified).isBlank()) {
                    insText.add(idx);
                }
                // comments, PIs and CDATA cannot carry marks and are left as they are
            }
            for (int idx : del) {
                if (coveredB.contains(idx)) {
                    continue;
                }
                Token t = b.get(idx);
                if (t.kind() == Kind.START || t.kind() == Kind.EMPTY) {
                    int close = XmlTokens.closeOf(b, idx);
                    boolean endDeleted = close == idx || deletedB.contains(close);
                    if (!endDeleted) {
                        continue;   // a rename handled from the inserted side, or unclosed
                    }
                    boolean allInsideDeleted = true;
                    int firstEqual = -1;
                    int lastEqual = -1;
                    for (int j = idx + 1; j < close; j++) {
                        if (bToM.containsKey(j)) {
                            allInsideDeleted = false;
                            int mj = bToM.get(j);
                            if (firstEqual < 0) {
                                firstEqual = mj;
                            }
                            lastEqual = mj;
                        }
                    }
                    if (allInsideDeleted) {
                        wholeDeletes.add(idx);
                        cover(coveredB, idx, close, b);
                    } else {
                        wrapperDeletes.add(new int[] {idx, firstEqual, lastEqual});
                        coveredB.add(idx);
                        coveredB.add(close);
                    }
                } else if (t.kind() == Kind.TEXT && !t.text(baseline).isBlank()) {
                    delText.add(idx);
                }
            }
            // Text that moved out and in within one run under the same parent is one change.
            int i = 0;
            while (i < delText.size() && i < insText.size()) {
                int bi = delText.get(i);
                int mi = insText.get(i);
                if (sameParent(bi, mi)) {
                    if (phAllowed(m, mi)) {
                        textPairs.add(new int[] {bi, mi});
                    } else if (m.get(mi).parent() >= 0 && b.get(bi).parent() >= 0) {
                        elementChanges.put(m.get(mi).parent(), b.get(bi).parent());
                    }
                    i++;
                } else {
                    break;
                }
            }
            for (int j = i; j < insText.size(); j++) {
                int mi = insText.get(j);
                if (phAllowed(m, mi)) {
                    loneInserts.add(mi);
                } else if (m.get(mi).parent() >= 0 && mToB.containsKey(m.get(mi).parent())) {
                    elementChanges.put(m.get(mi).parent(), mToB.get(m.get(mi).parent()));
                }
            }
            for (int j = i; j < delText.size(); j++) {
                int bi = delText.get(j);
                int bParent = b.get(bi).parent();
                if (bParent >= 0 && bToM.containsKey(bParent) && !phAllowed(m, bToM.get(bParent))) {
                    elementChanges.put(bToM.get(bParent), bParent);
                } else {
                    loneDeletes.add(bi);
                }
            }
        }

        /** The deleted START whose END aligned with the inserted element's END: a rename. */
        private int renamedPartner(int mStart, List<Integer> del) {
            int mClose = XmlTokens.closeOf(m, mStart);
            if (mClose < 0 || !mToB.containsKey(mClose)) {
                return -1;
            }
            int bClose = mToB.get(mClose);
            for (int idx : del) {
                Token t = b.get(idx);
                if (t.kind() == Kind.START && XmlTokens.closeOf(b, idx) == bClose) {
                    return idx;
                }
            }
            return -1;
        }

        private boolean sameParent(int bIdx, int mIdx) {
            int bp = b.get(bIdx).parent();
            int mp = m.get(mIdx).parent();
            if (bp < 0 || mp < 0) {
                return bp == mp;
            }
            return mToB.containsKey(mp) && mToB.get(mp) == bp;
        }

        private static void cover(Set<Integer> covered, int from, int to, List<Token> tokens) {
            for (int j = from; j <= Math.max(from, to); j++) {
                covered.add(j);
            }
        }

        // ── rendering ───────────────────────────────────────────────────

        /** A wrapper taken off and the same-named wrapper put back around the same content cancels out. */
        void cancelIdentityWrappers() {
            for (int[] w : new ArrayList<>(wrapperDeletes)) {
                Token bt = b.get(w[0]);
                for (int mStart : new ArrayList<>(wrapperInserts)) {
                    Token mt = m.get(mStart);
                    int mClose = XmlTokens.closeOf(m, mStart);
                    if (mt.name().equals(bt.name()) && w[1] > mStart && w[2] < mClose
                            && w[1] == mStart + 1 && w[2] == mClose - 1) {
                        wrapperDeletes.remove(w);
                        wrapperInserts.remove(Integer.valueOf(mStart));
                        if (!bt.attrs().equals(mt.attrs())) {
                            elementChanges.put(mStart, w[0]);
                        }
                        break;
                    }
                }
            }
        }

        List<Splice> render(boolean changedFallback) {
            cancelIdentityWrappers();
            // Outer element changes and whole inserts cover everything inside them.
            List<int[]> spans = new ArrayList<>();
            for (int mStart : elementChanges.keySet()) {
                spans.add(new int[] {m.get(mStart).start(), XmlTokens.endOf(m, mStart)});
            }
            for (int mStart : wholeInserts) {
                spans.add(new int[] {m.get(mStart).start(), XmlTokens.endOf(m, mStart)});
            }
            List<Splice> out = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : elementChanges.entrySet()) {
                if (strictlyInside(m.get(e.getKey()).start(), spans)) {
                    continue;
                }
                out.addAll(changedFallback ? changedNote(e.getKey(), e.getValue()) : pair(e.getKey(), e.getValue()));
            }
            for (int mStart : wholeInserts) {
                if (!strictlyInside(m.get(mStart).start(), spans)) {
                    out.add(markTag(m.get(mStart), "new", List.of()));
                }
            }
            for (int mStart : wrapperInserts) {
                if (!inside(m.get(mStart).start(), spans)) {
                    out.add(markTag(m.get(mStart), "new", List.of(Marks.WRAPPER)));
                }
            }
            // Deleted things go back where they were: just after the modified position
            // of the nearest earlier baseline token that still has one, in baseline order.
            Map<Integer, StringBuilder> restoredAt = new java.util.TreeMap<>();
            List<int[]> deletions = new ArrayList<>();   // {bIdx, isElement}
            wholeDeletes.forEach(bi -> deletions.add(new int[] {bi, 1}));
            loneDeletes.forEach(bi -> deletions.add(new int[] {bi, 0}));
            deletions.sort(java.util.Comparator.comparingInt(d -> d[0]));
            for (int[] d : deletions) {
                int anchor = anchorFor(d[0]);
                // An insertion at the very start of a covered span is beside it, not in it.
                if (strictlyInside(anchor, spans)) {
                    continue;
                }
                String text = d[1] == 1 ? restored(d[0], List.of(Marks.DELETED))
                        : ph("deleted", b.get(d[0]).text(baseline));
                restoredAt.computeIfAbsent(anchor, a -> new StringBuilder()).append(text);
            }
            restoredAt.forEach((at, text) -> out.add(new Splice(at, 0, text.toString())));
            for (int[] w : wrapperDeletes) {
                if (w[1] < 0 || strictlyInside(m.get(w[1]).start(), spans)) {
                    continue;
                }
                Token bt = b.get(w[0]);
                out.add(new Splice(m.get(w[1]).start(), 0,
                        Marks.retag(bt, Marks.marked(bt, "deleted", author, List.of(Marks.WRAPPER)))));
                Token last = m.get(w[2]);
                int endAt = last.kind() == Kind.START ? XmlTokens.endOf(m, w[2]) : last.end();
                out.add(new Splice(endAt, 0, "</" + bt.name() + ">"));
            }
            for (int[] p : textPairs) {
                if (!inside(m.get(p[1]).start(), spans)) {
                    out.add(textChange(b.get(p[0]), m.get(p[1]),
                            XmlTokens.ancestor(m, p[1], PREFORMATTED) >= 0));
                }
            }
            for (int mi : loneInserts) {
                if (!inside(m.get(mi).start(), spans)) {
                    out.add(wrapInserted(m.get(mi)));
                }
            }
            return out;
        }

        /**
         * Where in the modified text a deleted baseline token belongs: right after
         * the modified position of the closest preceding baseline token that is
         * still there (aligned, or the deleted half of a text pair), else before the
         * first aligned token that follows, else the start.
         */
        private int anchorFor(int bIdx) {
            for (int j = bIdx - 1; j >= 0; j--) {
                if (bToM.containsKey(j)) {
                    int mj = bToM.get(j);
                    Token mt = m.get(mj);
                    return mt.kind() == Kind.START ? mt.end() : XmlTokens.endOf(m, mj) == mt.end() ? mt.end() : mt.end();
                }
                for (int[] p : textPairs) {
                    if (p[0] == j) {
                        return m.get(p[1]).end();
                    }
                }
            }
            for (int j = bIdx + 1; j < b.size(); j++) {
                if (bToM.containsKey(j)) {
                    return m.get(bToM.get(j)).start();
                }
            }
            return 0;
        }

        private boolean inside(int offset, List<int[]> spans) {
            for (int[] s : spans) {
                if (s[0] <= offset && offset < s[1]) {
                    return true;
                }
            }
            return false;
        }

        private boolean strictlyInside(int offset, List<int[]> spans) {
            for (int[] s : spans) {
                if (s[0] < offset && offset < s[1]) {
                    return true;
                }
            }
            return false;
        }

        private Splice markTag(Token t, String status, List<String> props) {
            return new Splice(t.start(), t.end() - t.start(), Marks.retag(t, Marks.marked(t, status, author, props)));
        }

        /** The baseline element's full text with its start tag marked. */
        private String restored(int bStart, List<String> props) {
            Token t = b.get(bStart);
            return Marks.retag(t, Marks.marked(t, "deleted", author, props))
                    + baseline.substring(t.end(), XmlTokens.endOf(b, bStart));
        }

        /** Old element kept as deleted, new element marked new: restorable by construction. */
        private List<Splice> pair(int mStart, int bStart) {
            Token mt = m.get(mStart);
            List<Splice> out = new ArrayList<>();
            out.add(new Splice(mt.start(), 0, restored(bStart, List.of(Marks.DELETED))));
            out.add(markTag(mt, "new", List.of()));
            return out;
        }

        /** A changed mark with the previous version, lossless, in a note beside the element. */
        private List<Splice> changedNote(int mStart, int bStart) {
            Token mt = m.get(mStart);
            Token bt = b.get(bStart);
            List<Splice> out = new ArrayList<>();
            out.add(markTag(mt, "changed", List.of()));
            if (!noteAllowed(mStart)) {
                return out;
            }
            StringBuilder data = new StringBuilder();
            for (Map.Entry<String, String> a : bt.attrs().entrySet()) {
                data.append("<data name=\"").append(PREVIOUS_ATTR).append("\" value=\"")
                    .append(XmlTokens.escapeAttr(a.getKey() + "=" + a.getValue())).append("\"/>");
            }
            if (bt.kind() == Kind.START) {
                int bEnd = XmlTokens.endOf(b, bStart);
                int close = XmlTokens.closeOf(b, bStart);
                String inner = baseline.substring(bt.end(), close < 0 ? bEnd : b.get(close).start());
                data.append("<data name=\"").append(PREVIOUS_XML).append("\" value=\"")
                    .append(XmlTokens.escapeAttr(inner)).append("\"/>");
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("author", author);
            attrs.put("time", time);
            attrs.put("disposition", "open");
            attrs.put("outputclass", PREVIOUS_CLASS);
            out.add(new Splice(XmlTokens.endOf(m, mStart), 0, XmlTokens.renderTag("draft-comment", attrs, false)
                    + "Previous version kept for review." + data + "</draft-comment>"));
            return out;
        }

        private boolean noteAllowed(int mStart) {
            int parent = m.get(mStart).parent();
            return parent >= 0 && !NO_PH.contains(m.get(parent).name())
                    && XmlTokens.ancestor(m, mStart, Set.of("prolog")) < 0;
        }

        private Splice textChange(Token bt, Token mt, boolean preformatted) {
            List<String> oldWords = words(bt.text(baseline));
            List<String> newWords = words(mt.text(modified));
            List<Step> steps;
            try {
                steps = Myers.diff(oldWords, newWords, String::equals);
            } catch (Myers.TooDifferent e) {
                // a text node rewritten wholesale: one delete plus one insert is the honest mark
                steps = new ArrayList<>();
                for (int i = 0; i < oldWords.size(); i++) {
                    steps.add(new Step(Op.DELETE, i, -1));
                }
                for (int j = 0; j < newWords.size(); j++) {
                    steps.add(new Step(Op.INSERT, -1, j));
                }
            }
            StringBuilder rebuilt = new StringBuilder();
            StringBuilder del = new StringBuilder();
            StringBuilder ins = new StringBuilder();
            for (int k = 0; k < steps.size(); k++) {
                Step s = steps.get(k);
                switch (s.op()) {
                    case EQUAL -> {
                        String word = newWords.get(s.b());
                        boolean nextIsChange = k + 1 < steps.size() && steps.get(k + 1).op() != Op.EQUAL;
                        if (joinsChanges(word, !del.isEmpty() && !ins.isEmpty(), nextIsChange, preformatted)) {
                            // "6-12 inches" -> "15-30 cm" is one decision, not two:
                            // the unchanged space between them rides inside both marks.
                            del.append(word);
                            ins.append(word);
                        } else {
                            flush(rebuilt, del, ins);
                            rebuilt.append(word);
                        }
                    }
                    case DELETE -> del.append(oldWords.get(s.a()));
                    case INSERT -> ins.append(newWords.get(s.b()));
                }
            }
            flush(rebuilt, del, ins);
            return new Splice(mt.start(), mt.end() - mt.start(), rebuilt.toString());
        }

        /**
         * Whether an unchanged whitespace token between two changed runs joins
         * them into one pair. Contiguous changed words form one proposal; an
         * unchanged word ends it. Both sides must already be pending (a delete
         * and an insert), otherwise the shared space would become the whole
         * content of one mark. Inside preformatted text a line break ends the
         * run, since lines there are separate decisions; in prose a break is a
         * soft wrap the formatter put there and means nothing.
         *
         * <p>{@code words()} never emits an empty token: a token is either all
         * whitespace or all non-whitespace.
         */
        static boolean joinsChanges(String word, boolean pendingBothSides, boolean nextIsChange,
                boolean preformatted) {
            if (!pendingBothSides || !nextIsChange || !word.isBlank()) {
                return false;
            }
            return !(preformatted && hasLineBreak(word));
        }

        private static boolean hasLineBreak(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\n' || c == '\r' || c == '\u0085') {
                    return true;
                }
                int t = Character.getType(c);
                if (t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR) {
                    return true;
                }
            }
            return false;
        }

        /**
         * One coalesced hunk. Only whitespace both sides share in front stays
         * outside the marks; everything else, trailing space included, goes
         * inside, so accepting every mark reproduces the modified text exactly
         * and rejecting every mark reproduces the baseline exactly.
         */
        private void flush(StringBuilder out, StringBuilder del, StringBuilder ins) {
            if (del.isEmpty() && ins.isEmpty()) {
                return;
            }
            String d = del.toString();
            String i = ins.toString();
            if (d.isBlank() && i.isBlank()) {
                out.append(i);
            } else {
                int lead = 0;
                while (lead < d.length() && lead < i.length() && d.charAt(lead) == i.charAt(lead)
                        && Character.isWhitespace(d.charAt(lead))) {
                    lead++;
                }
                out.append(d, 0, lead);
                if (d.length() > lead) {
                    out.append(ph("deleted", d.substring(lead)));
                }
                if (i.length() > lead) {
                    out.append(ph("new", i.substring(lead)));
                }
            }
            del.setLength(0);
            ins.setLength(0);
        }

        private Splice wrapInserted(Token t) {
            String text = t.text(modified);
            int lead = 0;
            while (lead < text.length() && Character.isWhitespace(text.charAt(lead))) {
                lead++;
            }
            return new Splice(t.start(), t.end() - t.start(), text.substring(0, lead) + ph("new", text.substring(lead)));
        }

        /** A synthetic wrapper: {@code review-mark} says the engine wrote it and it unwraps on undo. */
        private String ph(String status, String content) {
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("status", status);
            attrs.put("rev", author + " " + Marks.MARK);
            if ("deleted".equals(status)) {
                attrs.put("otherprops", Marks.DELETED);
            }
            return XmlTokens.renderTag("ph", attrs, false) + content + "</ph>";
        }
    }
}
