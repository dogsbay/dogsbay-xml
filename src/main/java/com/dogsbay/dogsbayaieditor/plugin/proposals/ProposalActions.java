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

import java.io.IOException;
import java.nio.file.Files;
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

    /**
     * Accept every change, or one author's, in a file that is not open in the
     * editor: the file is read, decided and written back in its own encoding.
     *
     * @return null on success (including when there was nothing to decide), else a message for the user
     */
    public String acceptAllOnDisk(Path file, String author) {
        return applyOnDisk(file, author, true, -1);
    }

    /**
     * As {@link #acceptAllOnDisk(Path, String)}, only when the file still has
     * {@code expected} such changes: the user confirmed a count from a scan that may
     * be older than the file, and an agent may have proposed more since.
     */
    public String acceptAllOnDisk(Path file, String author, int expected) {
        return applyOnDisk(file, author, true, expected);
    }

    /** As {@link #acceptAllOnDisk(Path, String)}, rejecting. */
    public String rejectAllOnDisk(Path file, String author) {
        return applyOnDisk(file, author, false, -1);
    }

    /** As {@link #acceptAllOnDisk(Path, String, int)}, rejecting. */
    public String rejectAllOnDisk(Path file, String author, int expected) {
        return applyOnDisk(file, author, false, expected);
    }

    private String applyOnDisk(Path file, String author, boolean accept, int expected) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)) {
            return name + " no longer exists.";
        }
        if (!Files.isWritable(file)) {
            return name + " is read-only.";
        }
        java.nio.file.attribute.FileTime readAt;
        com.dogsbay.xml.review.DecodedFile decoded;
        try {
            readAt = Files.getLastModifiedTime(file);
            decoded = ProjectProposals.readDecoded(file);
        } catch (IOException | RuntimeException e) {
            return "Could not read " + name + ": " + e.getMessage();
        }
        List<Proposal> affected = ProposalIndex.scan(decoded.text(), author).stream().filter(Proposal::isChange).toList();
        if (expected >= 0 && affected.size() != expected) {
            return name + " changed since the list was made; nothing was decided.";
        }
        if (affected.isEmpty()) {
            return null;
        }
        String result;
        byte[] bytes;
        try {
            result = accept ? ProposalOps.acceptAll(decoded.text(), author) : ProposalOps.rejectAll(decoded.text(), author);
            bytes = decoded.encode(result);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return e.getMessage();   // refused as a whole: the file is left as it was
        } catch (java.nio.charset.CharacterCodingException e) {
            return name + " would contain characters its encoding (" + decoded.charset() + ") cannot store.";
        }
        String failed = replaceIfUnchanged(file, bytes, readAt);
        if (failed != null) {
            return failed;
        }
        for (Proposal p : affected) {
            audit.record(decidedBy.get(), accept ? "accept" : "reject", p, file);
        }
        return null;
    }

    /**
     * Write {@code bytes} over {@code file} through a sibling temporary file, so a
     * failed write never leaves a truncated file, and only when the file has not been
     * modified since {@code readAt}, so an agent's write in between is not lost.
     *
     * @return null when written, else a message for the user
     */
    private static String replaceIfUnchanged(Path file, byte[] bytes, java.nio.file.attribute.FileTime readAt) {
        String name = file.getFileName().toString();
        Path temp = file.resolveSibling("." + name + ".dogsbay-decide");
        try {
            Files.write(temp, bytes);
            try {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file));
            } catch (UnsupportedOperationException | IOException ignore) {
                // not a POSIX file system: the new file keeps the default permissions
            }
            if (!Files.getLastModifiedTime(file).equals(readAt)) {
                Files.deleteIfExists(temp);
                return name + " changed while it was being decided; nothing was written.";
            }
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignore) {
                // best effort
            }
            return "Could not save " + name + ": " + e.getMessage();
        }
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
