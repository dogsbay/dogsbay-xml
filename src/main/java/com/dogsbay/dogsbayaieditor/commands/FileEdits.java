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
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.xml.review.DecodedFile;

/**
 * Edits to a document that is not open in the editor: made to the file, with the same review marks,
 * refusals and exact revert as edits to an open buffer. Shared by the editor (a closed file) and the
 * headless executor (every file). Files are read and written in the encoding they declare.
 */
final class FileEdits {

    /** One writer to files at a time, so the checks that a file is unchanged and still closed go with the write. */
    private static final Object LOCK = new Object();

    /**
     * What the write gate checked an agent had read, for the command running on this thread: the file's real
     * path and a hash of the text. The file must still hold that text when it is written.
     */
    private static final ThreadLocal<Map<Path, String>> VERIFIED = ThreadLocal.withInitial(HashMap::new);

    private FileEdits() {
    }

    /** The write gate checked that {@code file} held {@code hash} when the agent's command was allowed. */
    static void verified(Path file, String hash) {
        VERIFIED.get().put(WriteGate.real(file), hash);
    }

    /** The command on this thread finished: forget what the gate checked for it. */
    static void clearVerified() {
        VERIFIED.remove();
    }

    /** A file's text, in the encoding it declares. */
    static String read(Path file) throws CommandException {
        return decode(file).text();
    }

    private static DecodedFile decode(Path file) throws CommandException {
        if (!Files.isRegularFile(file)) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND, "No such file: " + file);
        }
        try {
            return DecodedFile.read(file);
        } catch (CharacterCodingException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, file
                    + " is not valid text in the encoding it declares (or UTF-8, when it declares none)");
        } catch (IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "Could not read " + file + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * Put {@code modified} into {@code file}, which read {@code baseline}: as review proposals when an agent
     * writes a DITA document with proposals on, as given otherwise (see {@link AgentProposalPolicy}).
     *
     * @param snapshots where to record the write for an exact review_revert, or null
     * @param isOpen    whether a file is open in the editor, checked again just before writing; null without one
     */
    static CommandResult writeAgentAware(Path file, String baseline, String modified, boolean proposalsEnabled,
            ProposalSnapshots snapshots, Predicate<Path> isOpen) throws CommandException {
        AgentSession session = SessionContext.isBound() ? SessionContext.current() : null;
        AgentProposalPolicy.Outcome outcome = new AgentProposalPolicy().decide(session, file, baseline, modified,
                proposalsEnabled, AgentProposalPolicy.validatorFor(file));
        if (outcome.refused()) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    outcome.note() + "\nCurrent content:\n" + baseline);
        }
        writeIfUnchanged(file, baseline, outcome.text(), isOpen);
        if (snapshots != null && session != null && session.isAgent()) {
            // after the write, so a refused write leaves the record as it was
            snapshots.recordWrite(session.identity(), file, baseline, outcome.text(),
                    !com.dogsbay.xml.review.ProposalIndex.scan(baseline, session.identity()).isEmpty());
        }
        return CommandResult.ok(outcome.asProposals() ? "Content updated: " + outcome.note()
                : outcome.note() != null ? "Content updated (" + outcome.note() + ")" : "Content updated");
    }

    /**
     * Write {@code text} to {@code file}, in its encoding, if it still holds {@code expected} (and, for an
     * agent, what the write gate checked it read) and is still not open in the editor; otherwise refuse, as a
     * buffer edit refuses when the buffer moved.
     *
     * @param isOpen whether a file is open in the editor, or null without one
     */
    static void writeIfUnchanged(Path file, String expected, String text, Predicate<Path> isOpen)
            throws CommandException {
        synchronized (LOCK) {
            boolean open;
            try {
                open = isOpen != null && isOpen.test(file);
            } catch (RuntimeException e) {
                throw new CommandException(CommandException.ErrorCode.CONFLICT, e.getMessage());
            }
            if (open) {
                throw new CommandException(CommandException.ErrorCode.CONFLICT, file + " is open in the editor (with "
                        + "unsaved changes, or opened while the edit was being prepared); save or close it there, or "
                        + "read it again and apply your edit again");
            }
            DecodedFile now = decode(file);
            String gate = VERIFIED.get().get(WriteGate.real(file));
            if (!now.text().equals(expected) || (gate != null && !gate.equals(WriteGate.hash(expected)))) {
                throw new CommandException(CommandException.ErrorCode.CONFLICT,
                        "The file changed while the edit was being prepared; here is its current content, "
                        + "re-read it and apply your edit again:\n" + now.text());
            }
            byte[] bytes;
            try {
                bytes = now.encode(text);
            } catch (CharacterCodingException e) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, file + " is stored as "
                        + now.charset() + ", which cannot hold a character of the edit; nothing was changed");
            }
            try {
                com.dogsbay.dogsbayaieditor.URLUtilities.save(file.toUri().toURL(),
                        new java.io.ByteArrayInputStream(bytes), now.charset().name());
            } catch (IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
            }
        }
    }
}
