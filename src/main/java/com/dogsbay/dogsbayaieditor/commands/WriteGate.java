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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.agent.session.WriteLease;

/**
 * The one choke point every mutating command from an agent session passes
 * before it runs. Three checks, in order:
 *
 * <ol>
 * <li><b>Containment.</b> Hosted and external sessions may only touch paths
 *     inside the open project (symlinks resolved). The built-in agent is the
 *     user's own, approval-gated, and is not contained.</li>
 * <li><b>Conflict.</b> A buffer edit must follow a read by the same session
 *     (content or outline), and the buffer must still hold what that read
 *     returned. Otherwise the agent is told to re-read; the refusal carries
 *     the current content.</li>
 * <li><b>Lease.</b> One agent session per document at a time. Chat agents
 *     release at turn end; external clients have no turns, so their leases
 *     are short and renew on each write. Taken last, so a refusal pins
 *     nothing, and released again if the command then fails.</li>
 * </ol>
 *
 * <p>Swing-free. The caller supplies the current buffer content on demand.
 */
public final class WriteGate {

    /** How the gate sees the editor. */
    public interface Documents {
        /** The active document's path, or null when none is open. */
        Path activeDocument();

        /** The current text of {@code file} (null = active), or null when not open. */
        String content(Path file) throws CommandException;
    }

    static final Duration CHAT_LEASE = Duration.ofMinutes(5);
    static final Duration EXTERNAL_LEASE = Duration.ofSeconds(90);

    private final WriteLease leases;
    private final Supplier<Path> projectRoot;
    private final Documents documents;
    /** session id -> (document key -> hash of what the session last read). */
    private final Map<String, Map<Path, String>> lastRead = new ConcurrentHashMap<>();

    public WriteGate(WriteLease leases, Supplier<Path> projectRoot, Documents documents) {
        this.leases = Objects.requireNonNull(leases);
        this.projectRoot = Objects.requireNonNull(projectRoot);
        this.documents = Objects.requireNonNull(documents);
    }

    /** Remember what {@code session} just read, so a later edit can be checked. */
    public void noteRead(AgentSession session, Path fileOrNull, String content) {
        if (!session.isAgent() || content == null) {
            return;
        }
        try {
            Path key = key(fileOrNull);
            if (key != null) {
                lastRead.computeIfAbsent(session.id(), k -> new ConcurrentHashMap<>()).put(key, hash(content));
            }
        } catch (CommandException e) {
            // nothing to remember
        }
    }

    /** Refuse or allow {@code command} for {@code session}. */
    public void check(AgentSession session, Command<?> command) throws CommandException {
        if (!session.isAgent()) {
            return;
        }
        CommandTargets targets = CommandTargets.of(command);
        if (!targets.isMutating()) {
            return;
        }
        List<Path> paths = resolve(targets);
        contain(session, paths);
        // Conflict before lease, so a refused edit pins nothing.
        if (targets.scope() == CommandTargets.Scope.BUFFER) {
            Path lookup = targets.activeDocument() ? null : targets.files().get(0);
            conflict(session, lookup, key(lookup));
        }
        for (Path p : paths) {
            lease(session, p);
        }
    }

    /** The command failed after passing the gate: it changed nothing, so hold nothing for it. */
    public void abandon(AgentSession session, Command<?> command) {
        if (!session.isAgent()) {
            return;
        }
        for (Path p : resolve(CommandTargets.of(command))) {
            leases.release(p, session);
        }
    }

    /** After a buffer edit succeeds, the new content is what the session last saw. */
    public void noteWritten(AgentSession session, Command<?> command) throws CommandException {
        if (!session.isAgent()) {
            return;
        }
        CommandTargets targets = CommandTargets.of(command);
        if (targets.scope() != CommandTargets.Scope.BUFFER) {
            return;
        }
        Path file = targets.activeDocument() ? null : targets.files().get(0);
        noteRead(session, file, documents.content(file));
    }

    /** The editor's current text for {@code file} (null = active), or null. */
    public String currentContent(Path file) throws CommandException {
        return documents.content(file);
    }

    /** The chat turn ended: release every lease the session holds. */
    public void turnEnded(AgentSession session) {
        leases.releaseAll(session);
    }

    /** The session is gone: release leases and forget its reads. */
    public void sessionClosed(AgentSession session) {
        leases.releaseAll(session);
        lastRead.remove(session.id());
    }

    // ── checks ──────────────────────────────────────────────────────────

    private void contain(AgentSession session, List<Path> paths) throws CommandException {
        if (session.kind() == SessionKind.BUILTIN || paths.isEmpty()) {
            return;
        }
        Path root = projectRoot.get();
        if (root == null) {
            throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                    "No project is open; " + session.displayName()
                    + " may only write inside an open project");
        }
        Path realRoot = real(root);
        for (Path p : paths) {
            if (p == ACTIVE) {
                continue;
            }
            if (!real(p).startsWith(realRoot)) {
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                        "'" + p + "' is outside the project '" + root + "'; "
                        + session.displayName() + " may only write inside it");
            }
        }
    }

    private void lease(AgentSession session, Path file) throws CommandException {
        Duration ttl = session.kind() == SessionKind.BUILTIN || session.kind() == SessionKind.ACP_HOSTED
                ? CHAT_LEASE : EXTERNAL_LEASE;
        var refusal = leases.acquire(file, session, ttl);
        if (refusal.isPresent()) {
            throw new CommandException(CommandException.ErrorCode.LOCKED, refusal.get().message());
        }
    }

    /**
     * @param lookup the path as the caller gave it (null = active), which is
     *               how the editor finds open documents
     * @param key    the document's identity for reads and leases
     */
    private void conflict(AgentSession session, Path lookup, Path key) throws CommandException {
        if (key == null) {
            throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
                    "No active document to edit");
        }
        String current = documents.content(lookup);
        if (current == null) {
            throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
                    "'" + (lookup == null ? "the active document" : lookup) + "' is not open in the editor");
        }
        String seen = lastRead.getOrDefault(session.id(), Map.of()).get(key);
        if (seen == null) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    "Read the document before editing it (get its content or outline first), so the "
                    + "edit can be checked against what you saw");
        }
        if (!seen.equals(hash(current))) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    "The document changed since you read it; here is its current content, "
                    + "re-read it and apply your edit again:\n" + current);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Stands in for an untitled active document, which has no path yet. */
    static final Path ACTIVE = Path.of("<active-document>");

    /**
     * The identity of a document for leases and reads: its real path; the
     * active document's real path; or {@link #ACTIVE} for an untitled active
     * document. Null when nothing is active.
     */
    private Path key(Path fileOrNull) throws CommandException {
        if (fileOrNull != null) {
            return real(absolute(fileOrNull));
        }
        Path active = documents.activeDocument();
        if (active != null) {
            return real(active);
        }
        return documents.content(null) != null ? ACTIVE : null;
    }

    private List<Path> resolve(CommandTargets targets) {
        List<Path> out = new java.util.ArrayList<>();
        for (Path p : targets.allPaths()) {
            out.add(real(absolute(p)));
        }
        if (targets.activeDocument()) {
            Path active = documents.activeDocument();
            out.add(active != null ? real(active) : ACTIVE);
        }
        return out;
    }

    /**
     * Relative paths resolve exactly as the executors resolve them, against
     * the JVM's working directory, so the gate contains and leases the file
     * that will actually be written. Agents are expected to send absolute
     * paths; the built-in agent's tool layer absolutises existing ones.
     */
    private static Path absolute(Path p) {
        return p.toAbsolutePath().normalize();
    }

    /** Real path when it exists, else the real path of the nearest existing ancestor plus the rest. */
    public static Path real(Path p) {
        if (p == ACTIVE) {
            return p;
        }
        Path abs = p.toAbsolutePath().normalize();
        Path probe = abs;
        Path rest = null;
        while (probe != null && !Files.exists(probe)) {
            rest = rest == null ? probe.getFileName() : probe.getFileName().resolve(rest);
            probe = probe.getParent();
        }
        if (probe == null) {
            return abs;
        }
        try {
            Path realProbe = probe.toRealPath();
            return rest == null ? realProbe : realProbe.resolve(rest);
        } catch (IOException e) {
            return abs;
        }
    }

    static String hash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
