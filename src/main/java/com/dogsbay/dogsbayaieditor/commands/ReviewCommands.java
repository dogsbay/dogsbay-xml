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
import java.util.ArrayList;
import java.util.List;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.dogsbayaieditor.commands.results.ProposalInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ReviewResult;
import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ProposalIndex;
import com.dogsbay.xml.review.ProposalOps;
import com.dogsbay.xml.review.ReviewAudit;
import com.dogsbay.xml.review.XmlTokens;

/**
 * The review commands over a document's text. Both executors use these:
 * the headless one on the file, the editor on the open buffer. Decisions
 * are the writer's alone: an agent session asking to accept or reject is
 * refused. Comments are for everyone; a comment's author is the session.
 */
public final class ReviewCommands {

    private ReviewCommands() {
    }

    public static List<ProposalInfo> list(String xml, String author) {
        List<ProposalInfo> out = new ArrayList<>();
        for (Proposal p : ProposalIndex.scan(xml, author)) {
            out.add(new ProposalInfo(p.id(), p.kind().name().toLowerCase(), p.author(), p.element(), p.text(),
                    p.start(), p.end(), lineOf(xml, p.start()), p.synthetic(), p.wrapper()));
        }
        return out;
    }

    /** The new text after a decision, or a refusal. */
    public static Decision decide(String xml, boolean accept, String id, String author, boolean all)
            throws CommandException {
        AgentSession session = SessionContext.current();
        if (session.isAgent()) {
            throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                    "Only the writer may accept or reject proposals; " + session.displayName()
                    + " may leave a comment with review-comment instead");
        }
        boolean hasId = id != null && !id.isBlank();
        if (hasId && all) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Give either a proposal id or --all, not both");
        }
        if (!hasId && !all) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Give a proposal id (from review-list) or --all");
        }
        String decision = accept ? "accept" : "reject";
        List<Proposal> affected;
        String result;
        try {
            if (all) {
                affected = ProposalIndex.scan(xml, author).stream().filter(Proposal::isChange).toList();
                result = accept ? ProposalOps.acceptAll(xml, author) : ProposalOps.rejectAll(xml, author);
            } else {
                Proposal p = ProposalIndex.scan(xml).stream().filter(x -> x.id().equals(id)).findFirst()
                        .orElseThrow(() -> new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                                "No proposal " + id + " in the document; run review-list for current ids"));
                if (author != null && !author.isBlank() && !author.equals(p.author())) {
                    throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                            "Proposal " + id + " is by " + p.author() + ", not " + author);
                }
                affected = List.of(p);
                result = accept ? ProposalOps.accept(xml, p) : ProposalOps.reject(xml, p);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT, e.getMessage());
        }
        return new Decision(result, decision, affected, session.identity());
    }

    /** A decided document and what to record about it. */
    public record Decision(String text, String decision, List<Proposal> affected, String decidedBy) {
        public ReviewResult result(Path file) {
            int remaining = (int) ProposalIndex.scan(text).stream().filter(Proposal::isChange).count();
            return new ReviewResult(file == null ? null : file.toString(), decision, affected.size(), remaining,
                    affected.size() + " proposal(s) " + decision + "ed, " + remaining + " remaining");
        }

        public void audit(ReviewAudit audit, Path file) {
            for (Proposal p : affected) {
                audit.record(decidedBy, decision, p, file);
            }
        }
    }

    /**
     * The document with a comment added after {@code afterText} or inside the
     * element {@code elementId}. The candidate is validated when a validator
     * is given: for an element anchor the end of the element is tried first,
     * then just inside its start tag, and a placement the grammar forbids (a
     * comment directly under {@code <topic>}, say) is refused with advice.
     */
    public static String comment(String xml, String afterText, String elementId, String text, Clock clock,
            java.util.function.Predicate<String> isValid) throws CommandException {
        if (text == null || text.isBlank()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "The comment text is empty");
        }
        AgentSession session = SessionContext.current();
        String time = DateTimeFormatter.ISO_INSTANT.format(clock.instant());
        for (int at : anchors(xml, afterText, elementId)) {
            String candidate = ProposalOps.addComment(xml, at, session.identity(), time, text);
            if (isValid == null || isValid.test(candidate)) {
                return candidate;
            }
        }
        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "A comment is not allowed there by the document's grammar; anchor it inside a paragraph, "
                + "step or other text-bearing element instead");
    }

    /** Backwards-compatible form without validation. */
    public static String comment(String xml, String afterText, String elementId, String text, Clock clock)
            throws CommandException {
        return comment(xml, afterText, elementId, text, clock, null);
    }

    /**
     * Candidate offsets for a comment, best first. Text is matched on its
     * unescaped content and may span inline tags; text inside an existing
     * comment or a deleted span never matches.
     */
    static List<Integer> anchors(String xml, String afterText, String elementId) throws CommandException {
        List<XmlTokens.Token> tokens = XmlTokens.tokenize(xml);
        if (elementId != null && !elementId.isBlank()) {
            for (int i = 0; i < tokens.size(); i++) {
                XmlTokens.Token t = tokens.get(i);
                if ((t.kind() == XmlTokens.Kind.START || t.kind() == XmlTokens.Kind.EMPTY)
                        && elementId.equals(t.attrs().get("id"))) {
                    if (t.kind() == XmlTokens.Kind.EMPTY) {
                        return List.of(t.end());
                    }
                    int close = XmlTokens.closeOf(tokens, i);
                    int end = close < 0 ? t.end() : tokens.get(close).start();
                    return end == t.end() ? List.of(t.end()) : List.of(end, t.end());
                }
            }
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "No element with id \"" + elementId + "\"");
        }
        if (afterText != null && !afterText.isBlank()) {
            // Project the eligible text into one string, remembering where each character came from.
            StringBuilder projected = new StringBuilder();
            List<int[]> spans = new ArrayList<>();   // {projectedStart, tokenIndex}
            for (int i = 0; i < tokens.size(); i++) {
                XmlTokens.Token t = tokens.get(i);
                if (t.kind() != XmlTokens.Kind.TEXT || excluded(tokens, i)) {
                    continue;
                }
                spans.add(new int[] {projected.length(), i});
                projected.append(XmlTokens.unescape(t.text(xml)));
            }
            int hit = projected.indexOf(afterText);
            if (hit < 0) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "The text \"" + afterText + "\" does not occur in the document");
            }
            int endInProjection = hit + afterText.length();
            // The token where the match ends, and the offset within it.
            for (int k = spans.size() - 1; k >= 0; k--) {
                if (spans.get(k)[0] < endInProjection) {
                    XmlTokens.Token t = tokens.get(spans.get(k)[1]);
                    int within = endInProjection - spans.get(k)[0];
                    return List.of(t.start() + Math.min(t.end() - t.start(), rawOffset(t.text(xml), within)));
                }
            }
            throw new IllegalStateException("unreachable");
        }
        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Say where the comment goes: afterText or elementId");
    }

    /** Text inside a draft-comment or a deleted proposal is not a place to anchor on. */
    private static boolean excluded(List<XmlTokens.Token> tokens, int index) {
        int p = tokens.get(index).parent();
        while (p >= 0) {
            XmlTokens.Token e = tokens.get(p);
            if ("draft-comment".equals(e.name()) || "deleted".equals(e.attrs().get("status"))) {
                return true;
            }
            p = e.parent();
        }
        return false;
    }

    /** The raw-source offset that corresponds to {@code unescapedChars} characters of unescaped text. */
    static int rawOffset(String raw, int unescapedChars) {
        int i = 0;
        int seen = 0;
        while (i < raw.length() && seen < unescapedChars) {
            if (raw.charAt(i) == '&') {
                int semi = raw.indexOf(';', i);
                i = semi < 0 ? i + 1 : semi + 1;
            } else {
                i++;
            }
            seen++;
        }
        return i;
    }

    static int lineOf(String xml, int offset) {
        int line = 1;
        int from = 0;
        int nl;
        while ((nl = xml.indexOf('\n', from)) >= 0 && nl < offset) {
            line++;
            from = nl + 1;
        }
        return line;
    }

    public static String read(String file) throws CommandException {
        Path p = Path.of(file);
        if (!Files.isRegularFile(p)) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND, "No such file: " + file);
        }
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
        }
    }

    /** Save through the editor's atomic writer, which handles symlinks, permissions and cleanup. */
    public static void write(String file, String text) throws CommandException {
        try {
            com.dogsbay.dogsbayaieditor.URLUtilities.save(Path.of(file).toUri().toURL(),
                    new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "UTF-8");
        } catch (IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
        }
    }

    /**
     * The review audit for the project that contains {@code file}: the
     * workspace root the editor itself recognises. With no project, nothing
     * is recorded, exactly as the in-editor path behaves.
     */
    public static ReviewAudit auditFor(Path file) {
        return new ReviewAudit(() -> com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.workspaceRootFor(file));
    }

    public static CommandResult ok(String message) {
        return CommandResult.ok(message);
    }
}
