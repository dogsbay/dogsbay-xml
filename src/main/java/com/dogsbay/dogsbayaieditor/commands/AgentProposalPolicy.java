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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.dogsbayaieditor.validate.DocumentValidator;
import com.dogsbay.xml.review.ChangeMarkup;
import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ProposalIndex;

/**
 * Decides how an agent's write lands in a DITA document: as marked
 * proposals for the writer to review, or, when that is impossible, as the
 * plain text it asked for. The user's own writes are never touched.
 */
public final class AgentProposalPolicy {

    /**
     * The text to put in the document, and what happened. A refused outcome
     * has no text: the write must not happen, and {@code note} says why.
     */
    public record Outcome(String text, boolean asProposals, int proposals, String note, boolean refused) {
        static Outcome plain(String text, String note) {
            return new Outcome(text, false, 0, note, false);
        }

        static Outcome refuse(String why) {
            return new Outcome(null, false, 0, why, true);
        }
    }

    private final Clock clock;

    public AgentProposalPolicy() {
        this(Clock.systemUTC());
    }

    public AgentProposalPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param session   who is writing
     * @param file      the document's path (null for an untitled document)
     * @param baseline  the document's current text
     * @param modified  the text the agent wants
     * @param enabled   the preference
     * @param validator whether a candidate is valid against its grammar
     */
    public Outcome decide(AgentSession session, Path file, String baseline, String modified, boolean enabled,
            Predicate<String> validator) {
        if (!enabled || session == null || !session.isAgent()) {
            return Outcome.plain(modified, null);
        }
        if (file == null || !DocumentValidator.isDitaFile(file)) {
            return Outcome.plain(modified, null);
        }
        if (baseline == null || baseline.equals(modified)) {
            return Outcome.plain(modified, null);
        }
        // Other authors' open proposals in the baseline must survive this write,
        // marked or not: an agent may neither accept nor erase someone else's.
        List<Proposal> othersBefore = ProposalIndex.scan(baseline).stream()
                .filter(x -> !session.identity().equals(x.author())).toList();
        int mine = ProposalIndex.scan(baseline, session.identity()).size();
        try {
            String marked = new ChangeMarkup(session.identity(),
                    DateTimeFormatter.ISO_INSTANT.format(clock.instant()), validator).mark(baseline, modified);
            String missing = missingProposals(othersBefore, marked);
            if (missing != null) {
                return Outcome.refuse(missing);
            }
            int count = Math.max(0, ProposalIndex.scan(marked, session.identity()).size() - mine);
            return new Outcome(marked, true, count, count + " proposal(s) marked for review with rev=\""
                    + session.identity() + "\"; the writer accepts or rejects them. Do not edit the marks.", false);
        } catch (ChangeMarkup.TooDifferent | IllegalArgumentException e) {
            if (!othersBefore.isEmpty()) {
                return Outcome.refuse("the document holds review proposals by other authors, and this write "
                        + "cannot be marked (" + e.getMessage() + "); re-read the document and make a smaller edit");
            }
            return Outcome.plain(modified, "applied as a whole: " + e.getMessage());
        }
    }

    /**
     * A message when any of {@code before} is gone from {@code after}, else null.
     *
     * <p>
     * Every field is compared null-safely. A hand-written {@code <draft-comment>}
     * carries no {@code author}, and a full scan reports it — comparing on it
     * directly turned an ordinary edit near an unattributed comment into a
     * NullPointerException instead of an edit.
     */
    static String missingProposals(List<Proposal> before, String after) {
        List<Proposal> now = ProposalIndex.scan(after);
        for (Proposal p : before) {
            boolean kept = now.stream().anyMatch(q -> q.kind() == p.kind()
                    && Objects.equals(q.author(), p.author())
                    && Objects.equals(q.element(), p.element())
                    && Objects.equals(q.text(), p.text()));
            if (!kept) {
                return "this write would remove a review proposal by " + describe(p.author()) + " ("
                        + p.kind().name().toLowerCase()
                        + " of \"" + abbreviate(p.text()) + "\"); only the writer may accept or reject it. "
                        + "Re-read the document and keep the existing marks.";
            }
        }
        return null;
    }

    /** Who a proposal belongs to; a hand-written draft comment names nobody. */
    private static String describe(String author) {
        return author == null ? "another author" : author;
    }

    private static String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    /**
     * A validator for {@code file}'s grammar. The candidate is written to a
     * temp file in the system temp directory with the document's extension,
     * so DITA picks up the bundled catalog, and validated as the editor
     * would. If validation cannot run at all the candidate is treated as
     * valid and the failure logged: the restorable pair is the better default.
     */
    public static Predicate<String> validatorFor(Path file) {
        return candidate -> {
            Path tmp = null;
            try {
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String ext = dot >= 0 ? name.substring(dot) : ".xml";
                tmp = Files.createTempFile("dogsbay-review-", ext);
                Files.writeString(tmp, candidate, StandardCharsets.UTF_8);
                return DocumentValidator.validate(tmp, null, List.of()).errors().isEmpty();
            } catch (IOException | RuntimeException e) {
                System.err.println("[review] could not validate a candidate for " + file + ": " + e.getMessage());
                return true;
            } finally {
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignore) {
                        // temp file
                    }
                }
            }
        };
    }

    /**
     * Replace {@code doc}'s text with {@code text} as one edit covering only
     * the span that differs, so undo is one step and the caret stays put.
     */
    public static void replaceMinimal(javax.swing.text.Document doc, String text) throws javax.swing.text.BadLocationException {
        String current = doc.getText(0, doc.getLength());
        int prefix = 0;
        int max = Math.min(current.length(), text.length());
        while (prefix < max && current.charAt(prefix) == text.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix && current.charAt(current.length() - 1 - suffix) == text.charAt(text.length() - 1 - suffix)) {
            suffix++;
        }
        int removeLen = current.length() - prefix - suffix;
        String insert = text.substring(prefix, text.length() - suffix);
        if (removeLen > 0) {
            doc.remove(prefix, removeLen);
        }
        if (!insert.isEmpty()) {
            doc.insertString(prefix, insert, null);
        }
    }
}
