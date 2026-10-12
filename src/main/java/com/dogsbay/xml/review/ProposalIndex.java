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
import java.util.Collections;
import java.util.List;

import com.dogsbay.xml.review.XmlTokens.Kind;
import com.dogsbay.xml.review.XmlTokens.Token;

/**
 * Finds the proposals in a document: elements whose {@code @status} is new,
 * deleted or changed <em>and</em> whose {@code @rev} carries an author id,
 * plus {@code <draft-comment>} elements other than the engine's own
 * previous-version notes. Human revision metadata ({@code rev="2.1"}) is not
 * a proposal. Tolerates hand-edited and unclosed markup.
 */
public final class ProposalIndex {

    private ProposalIndex() {
    }

    public static List<Proposal> scan(String xml) {
        return scan(xml, XmlTokens.tokenize(xml), false);
    }

    /**
     * What still needs a decision: every change, plus comments that carry a
     * review author id and no disposition yet. Hand-written draft comments and
     * resolved ones are left out.
     */
    public static List<Proposal> scanOpen(String xml) {
        return scan(xml, XmlTokens.tokenize(xml), true);
    }

    /**
     * Cheap text test before a scan: false means {@link #scan} would find
     * nothing. Looser than the tokenizer, never stricter.
     */
    public static boolean mayHaveProposals(String xml) {
        return xml != null && (xml.contains("status") || xml.contains("draft-comment"));
    }

    static List<Proposal> scan(String xml, List<Token> tokens) {
        return scan(xml, tokens, false);
    }

    private static List<Proposal> scan(String xml, List<Token> tokens, boolean openOnly) {
        List<Proposal> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Kind.START && t.kind() != Kind.EMPTY) {
                continue;
            }
            if ("draft-comment".equals(t.name())) {
                if (ChangeMarkup.PREVIOUS_CLASS.equals(t.attrs().get("outputclass"))) {
                    continue;   // belongs to the changed element before it
                }
                if (openOnly && (!Marks.isAuthorId(t.attrs().get("author"))
                        || (t.attrs().get("disposition") != null && !"open".equals(t.attrs().get("disposition"))))) {
                    continue;
                }
                int close = XmlTokens.closeOf(tokens, i);
                out.add(new Proposal(Proposal.Kind.COMMENT, "comment@" + t.start(), t.attrs().get("author"),
                        t.attrs().get("time"), innerText(xml, tokens, i, close), t.start(),
                        XmlTokens.endOf(tokens, i), t.name(), false, false));
                continue;
            }
            String status = t.attrs().get("status");
            String author = Marks.authorOf(t.attrs().get("rev"));
            if (status == null || author == null) {
                continue;
            }
            Proposal.Kind kind = switch (status) {
                case "new" -> Proposal.Kind.INSERT;
                case "deleted" -> Proposal.Kind.DELETE;
                case "changed" -> Proposal.Kind.CHANGED;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            int close = XmlTokens.closeOf(tokens, i);
            String rev = t.attrs().get("rev");
            out.add(new Proposal(kind, kind.name().toLowerCase() + "@" + t.start(), author, null,
                    innerText(xml, tokens, i, close), t.start(), XmlTokens.endOf(tokens, i), t.name(),
                    Marks.hasToken(rev, Marks.MARK), Marks.hasToken(rev, Marks.WRAPPER)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Proposals by one author, or all when {@code author} is null. */
    public static List<Proposal> scan(String xml, String author) {
        if (author == null) {
            return scan(xml);
        }
        return scan(xml).stream().filter(p -> author.equals(p.author())).toList();
    }

    /** The text content of the element (tags stripped, whitespace collapsed), for excerpts. */
    static String innerText(String xml, List<Token> tokens, int startIndex, int closeIndex) {
        if (closeIndex < 0 || closeIndex == startIndex) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int k = startIndex + 1; k < closeIndex; k++) {
            Token t = tokens.get(k);
            if (t.kind() == Kind.TEXT) {
                sb.append(XmlTokens.unescape(t.text(xml)));
            }
        }
        return sb.toString().replaceAll("\\s+", " ").strip();
    }
}
