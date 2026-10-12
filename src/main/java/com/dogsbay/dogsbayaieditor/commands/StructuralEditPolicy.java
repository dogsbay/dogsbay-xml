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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.xml.edit.StructuralEdit;
import com.dogsbay.xml.review.ChangeMarkup;
import com.dogsbay.xml.review.ErrorReporting;
import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ProposalIndex;
import com.dogsbay.xml.review.ProposalOps;

/**
 * What an {@link EditStructureCommand} writes: the steps applied, refused when they add a validation
 * error, and as review proposals when an agent makes them to a DITA document with proposals on. The
 * marks come from the steps; they are kept when they add no error of their own, else the plain result
 * is written with a note. Shared by the editor (open documents) and the headless executor (files).
 */
final class StructuralEditPolicy {

    /** The text to write, whether it holds proposals, what each step did, and a note. */
    record Outcome(String text, boolean asProposals, List<String> changes, String note) {
    }

    private StructuralEditPolicy() {
    }

    static Outcome decide(AgentSession session, Path file, String baseline, List<StructuralEdit.Step> steps,
            boolean proposalsEnabled, Predicate<String> validator) throws CommandException {
        if (steps == null || steps.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "steps is empty: nothing to do");
        }
        StructuralEdit.Result plain;
        try {
            plain = StructuralEdit.apply(baseline, steps, null);
        } catch (StructuralEdit.StepException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage() + ". Nothing was changed.");
        }
        ErrorReporting reporting = validator instanceof ErrorReporting r ? r : null;
        List<String> plainErrors = reporting == null ? List.of() : reporting.errors(plain.text());
        if (reporting != null) {
            List<String> added = added(plainErrors, reporting.errors(baseline));
            if (!added.isEmpty()) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "The steps would add " + (added.size() == 1 ? "an error" : added.size() + " errors")
                        + ", so nothing was changed: " + String.join("; ", added));
            }
        }
        boolean agent = session != null && session.isAgent();
        boolean dita = file != null && com.dogsbay.dogsbayaieditor.validate.DocumentValidator.isDitaFile(file);
        if (!agent || !proposalsEnabled || !dita) {
            return new Outcome(plain.text(), false, plain.changes(), null);
        }
        List<Proposal> others = ProposalIndex.scan(baseline).stream()
                .filter(p -> !session.identity().equals(p.author())).toList();
        String marked;
        try {
            marked = StructuralEdit.apply(baseline, steps, session.identity()).text();
        } catch (StructuralEdit.StepException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
        String missing = AgentProposalPolicy.missingProposals(others, marked);
        if (missing != null) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT, missing);
        }
        String why = null;
        if (!exact(marked, plain.text(), baseline, session.identity())) {
            why = "these steps could not be marked so that accepting or rejecting them restores the text exactly";
        } else if (reporting != null && !ChangeMarkup.within(reporting.errors(marked), plainErrors)) {
            why = "marking these steps would add a validation error here";
        }
        if (why != null) {
            // Unmarked, what the steps remove is gone rather than kept as deleted: refuse when that would take
            // another author's proposals with it, as edit_document's writes do
            String lost = AgentProposalPolicy.missingProposals(others, plain.text());
            if (lost != null) {
                throw new CommandException(CommandException.ErrorCode.CONFLICT, lost);
            }
            return new Outcome(plain.text(), false, plain.changes(), "applied without review marks: " + why);
        }
        return new Outcome(marked, true, plain.changes(), null);
    }

    /**
     * Whether the marks are exact: accepting the author's proposals gives the plain edit, and rejecting them
     * gives the text before, byte for byte. Both sides are compared with the author's earlier proposals
     * (from calls before this one) settled the same way, so those do not count against these marks.
     */
    static boolean exact(String marked, String plain, String baseline, String author) {
        try {
            return ProposalOps.acceptAll(marked, author).equals(ProposalOps.acceptAll(plain, author))
                    && ProposalOps.rejectAll(marked, author).equals(ProposalOps.rejectAll(baseline, author));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The errors in {@code after} that {@code before} does not have, as often as they are new. */
    private static List<String> added(List<String> after, List<String> before) {
        List<String> remaining = new ArrayList<>(before);
        List<String> out = new ArrayList<>();
        for (String e : after) {
            if (!remaining.remove(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** The command's answer: what each step did, and how it landed. */
    static String message(Outcome o, boolean dryRun, String author) {
        StringBuilder sb = new StringBuilder();
        int n = o.changes().size();
        sb.append(dryRun ? "Dry run, nothing written: " : "").append(n).append(n == 1 ? " step " : " steps ")
                .append(dryRun ? "would apply" : "applied");
        if (o.asProposals()) {
            sb.append(" as review proposals with rev=\"").append(author)
                    .append("\"; the writer accepts or rejects them, and review_revert takes them back");
        }
        if (o.note() != null) {
            sb.append(" (").append(o.note()).append(')');
        }
        sb.append(':');
        for (String c : o.changes()) {
            sb.append("\n- ").append(c);
        }
        return sb.toString();
    }
}
