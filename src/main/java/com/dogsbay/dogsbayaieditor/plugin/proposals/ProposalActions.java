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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import com.dogsbay.dogsbayaieditor.commands.AgentProposalPolicy;
import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ProposalIndex;
import com.dogsbay.xml.review.ProposalOps;
import com.dogsbay.xml.review.ReviewAudit;

/**
 * Accept and reject applied to the live editor buffer: the whole-document
 * result from the review model is spliced in as one undoable edit covering
 * only the span that changed, and every decision is written to the review
 * audit log. Swing-document only; no editor types, so it is testable.
 */
public final class ProposalActions {

    /** Brackets one decision so the editor's undo sees a single step. */
    public interface Compound {
        void begin();

        void end();

        Compound NONE = new Compound() {
            @Override public void begin() { }
            @Override public void end() { }
        };
    }

    private final ReviewAudit audit;
    private final Supplier<String> decidedBy;
    private final Compound compound;

    public ProposalActions(Supplier<Path> projectRoot, Supplier<String> decidedBy) {
        this(projectRoot, decidedBy, Compound.NONE);
    }

    public ProposalActions(Supplier<Path> projectRoot, Supplier<String> decidedBy, Compound compound) {
        this.audit = new ReviewAudit(projectRoot);
        this.decidedBy = decidedBy;
        this.compound = compound;
    }

    public static String text(Document doc) {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Proposal> list(Document doc) {
        return ProposalIndex.scan(text(doc));
    }

    /** @return null on success, else a message for the user */
    public String accept(Document doc, Proposal p, Path file) {
        Proposal live = relocate(doc, p);
        if (live == null) {
            return STALE;
        }
        return apply(doc, () -> ProposalOps.accept(text(doc), live), "accept", List.of(live), file);
    }

    public String reject(Document doc, Proposal p, Path file) {
        Proposal live = relocate(doc, p);
        if (live == null) {
            return STALE;
        }
        return apply(doc, () -> ProposalOps.reject(text(doc), live), "reject", List.of(live), file);
    }

    static final String STALE = "The document changed since the list was made; it has been refreshed, please choose again.";

    /**
     * The proposal as it is in the document now. A row in the panel may be a
     * few hundred milliseconds old; an offset alone could point at a
     * different element by the time the button is pressed, so the proposal
     * is matched on what it is (kind, author, element, text), preferring the
     * one nearest its old position.
     */
    static Proposal relocate(Document doc, Proposal p) {
        Proposal best = null;
        for (Proposal q : ProposalIndex.scan(text(doc))) {
            if (q.kind() == p.kind() && java.util.Objects.equals(q.author(), p.author())
                    && java.util.Objects.equals(q.element(), p.element()) && java.util.Objects.equals(q.text(), p.text())) {
                if (best == null || Math.abs(q.start() - p.start()) < Math.abs(best.start() - p.start())) {
                    best = q;
                }
            }
        }
        return best;
    }

    public String acceptAll(Document doc, String author, Path file) {
        List<Proposal> affected = ProposalIndex.scan(text(doc), author).stream().filter(Proposal::isChange).toList();
        return apply(doc, () -> ProposalOps.acceptAll(text(doc), author), "accept", affected, file);
    }

    public String rejectAll(Document doc, String author, Path file) {
        List<Proposal> affected = ProposalIndex.scan(text(doc), author).stream().filter(Proposal::isChange).toList();
        return apply(doc, () -> ProposalOps.rejectAll(text(doc), author), "reject", affected, file);
    }

    public String resolve(Document doc, Proposal comment, String disposition, Path file) {
        Proposal live = relocate(doc, comment);
        if (live == null) {
            return STALE;
        }
        return apply(doc, () -> ProposalOps.resolveComment(text(doc), live, disposition), "resolve:" + disposition,
                List.of(live), file);
    }

    private String apply(Document doc, Supplier<String> op, String decision, List<Proposal> affected, Path file) {
        String result;
        try {
            result = op.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return e.getMessage();
        }
        compound.begin();
        try {
            AgentProposalPolicy.replaceMinimal(doc, result);
        } catch (BadLocationException e) {
            return "Could not apply: " + e.getMessage();
        } finally {
            compound.end();
        }
        for (Proposal p : affected) {
            audit.record(decidedBy.get(), decision, p, file);
        }
        return null;
    }

    /** The proposal whose span contains {@code offset}, innermost first, or null. */
    public static Proposal at(List<Proposal> proposals, int offset) {
        Proposal best = null;
        for (Proposal p : proposals) {
            if (p.start() <= offset && offset < p.end()) {
                if (best == null || p.end() - p.start() < best.end() - best.start()) {
                    best = p;
                }
            }
        }
        return best;
    }
}
