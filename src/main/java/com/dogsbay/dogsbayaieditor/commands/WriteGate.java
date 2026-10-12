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
import java.util.function.BooleanSupplier;
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
 * <li><b>Conflict.</b> A document edit must follow a read by the same session
 *     (content or outline), and the document must still hold what that read
 *     returned: its buffer when it is open, else its file (when the writer
 *     lets agents edit files that are not open). Otherwise the agent is told to re-read; the refusal carries
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

    /** The writer's projects, as the gate needs them. */
    public interface Projects {
        /** The folder of the project named {@code name}, or null when there is none. */
        Path folder(String name);
    }

    /** Asks the writer a question about an agent's command, and waits for the answer. */
    public interface Approver {
        /** True when the writer allows it; false when they decline or cannot be asked. */
        boolean approve(AgentSession session, String question);
    }

    static final Duration CHAT_LEASE = Duration.ofMinutes(5);
    static final Duration EXTERNAL_LEASE = Duration.ofSeconds(90);

    private final WriteLease leases;
    private final Supplier<Path> projectRoot;
    private final Documents documents;
    private final BooleanSupplier closedFiles;
    private final Projects projects;
    private final Approver approver;
    /** session id -> (document key -> hash of what the session last read). */
    private final Map<String, Map<Path, String>> lastRead = new ConcurrentHashMap<>();

    public WriteGate(WriteLease leases, Supplier<Path> projectRoot, Documents documents) {
        this(leases, projectRoot, documents, () -> true);
    }

    /**
     * @param closedFiles whether agents may edit files that are not open in the editor (the writer's
     *                    setting): such an edit is then checked against the file, as an open document's is
     *                    against its buffer
     */
    public WriteGate(WriteLease leases, Supplier<Path> projectRoot, Documents documents, BooleanSupplier closedFiles) {
        this(leases, projectRoot, documents, closedFiles, name -> null, (session, question) -> false);
    }

    /**
     * @param projects the writer's projects, to know where opening one would move an agent
     * @param approver asks the writer before an agent moves out of the open project
     */
    public WriteGate(WriteLease leases, Supplier<Path> projectRoot, Documents documents, BooleanSupplier closedFiles,
            Projects projects, Approver approver) {
        this.projects = Objects.requireNonNull(projects);
        this.approver = Objects.requireNonNull(approver);
        this.closedFiles = Objects.requireNonNull(closedFiles);
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
        containArguments(session, command);
        if (command instanceof GetContentCommand c && c.readClosed() && c.file() != null
                && documents.content(c.file()) == null) {
            // Reading a file that is not open reads the disk (or opens it): only inside the project, as writes
            contain(session, List.of(real(absolute(c.file()))));
            return;
        }
        if (command instanceof CreateProjectCommand || command instanceof OpenProjectCommand) {
            boundary(session, command);
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
        noteRead(session, file, text(file));
    }

    /** The current text of {@code file} (null = active): its buffer, or its file when agents may edit it closed. */
    public String currentContent(Path file) throws CommandException {
        return text(file);
    }

    /** The buffer's text when the document is open; else the file's, when agents may edit closed files; else null. */
    private String text(Path lookup) throws CommandException {
        String open = documents.content(lookup);
        if (open != null || lookup == null || !closedFiles.getAsBoolean()) {
            return open;
        }
        Path file = absolute(lookup);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return com.dogsbay.xml.review.DecodedFile.read(file).text();
        } catch (IOException e) {
            return null;
        }
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
                    + " may only work inside an open project");
        }
        Path realRoot = real(root);
        for (Path p : paths) {
            if (p == ACTIVE) {
                continue;
            }
            if (!real(p).startsWith(realRoot)) {
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                        "'" + p + "' is outside the project '" + root + "'; "
                        + session.displayName() + " may only work inside it");
            }
        }
    }

    /**
     * Creating or opening a project moves an agent's sandbox to that project's folder, so an agent that could
     * do it freely would have no boundary. Moving out of the open project is the writer's decision: they are
     * asked. A project inside the open one only narrows the sandbox, and an unknown name or the open project
     * moves nothing, so those are not asked. The built-in agent's actions are approved one by one already.
     */
    private void boundary(AgentSession session, Command<?> command) throws CommandException {
        if (session.kind() == SessionKind.BUILTIN) {
            return;
        }
        String name;
        Path folder;
        String action;
        if (command instanceof CreateProjectCommand c) {
            name = c.name();
            folder = c.folderPath() == null ? null : real(absolute(c.folderPath()));
            action = "create the project \"" + name + "\" and switch the editor to it"
                    + (c.houseRules() == null || c.houseRules().isBlank() ? ""
                            : ", recording its house rules in the folder's .dogsbay/config.xml");
        } else {
            OpenProjectCommand c = (OpenProjectCommand) command;
            name = c.name();
            Path f = name == null ? null : projects.folder(name);
            folder = f == null ? null : real(absolute(f));
            action = "switch the editor to the project \"" + name + "\"";
        }
        if (folder == null) {
            return; // nothing to move to: the command itself says what is wrong
        }
        Path root = projectRoot.get();
        if (root != null && folder.startsWith(real(root))) {
            return;
        }
        String question = session.displayName() + " wants to " + action + ", in\n\n  " + folder + "\n\n"
                + "It would then be able to edit files there"
                + (root == null ? "." : " instead of in\n\n  " + real(root) + "\n\n") + "Allow it?";
        if (!approver.approve(session, question)) {
            throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED,
                    "The writer did not allow " + session.displayName() + " to " + action + " (" + folder
                    + "), which is outside the open project; nothing changed. Ask the writer to open it themselves.");
        }
    }

    /** Record components that name files or folders a command reads or writes. */
    private static final java.util.Set<String> PATH_ARGUMENTS = java.util.Set.of("file", "root", "input", "xslt",
            "schema", "schematron", "rootMap", "map", "catalogs", "ditaval", "output", "outputBaseDir", "policy",
            "newPath", "toMap", "filePattern", "template");

    /**
     * Hosted and external agents read, as they write, only inside the project: every path a command is given
     * is contained, not only the ones it writes (review, 2026-09-18: a report template, an XSLT input or a
     * health check's root could read any file on the machine). An agent may not choose the DITA-OT
     * installation a build runs, since that runs code. The built-in agent is approved call by call.
     */
    private void containArguments(AgentSession session, Command<?> command) throws CommandException {
        if (session.kind() == SessionKind.BUILTIN || !(command instanceof Record record)
                || command instanceof CreateProjectCommand || command instanceof OpenProjectCommand) {
            return;
        }
        for (java.lang.reflect.RecordComponent rc : record.getClass().getRecordComponents()) {
            Object value;
            try {
                value = rc.getAccessor().invoke(record);
            } catch (ReflectiveOperationException e) {
                continue;
            }
            if ("ditaOtHome".equals(rc.getName()) && value instanceof String home && !home.isBlank()) {
                throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED, session.displayName()
                        + " may not choose the DITA-OT installation a build runs; leave ditaOtHome out to use the "
                        + "project's");
            }
            if (!PATH_ARGUMENTS.contains(rc.getName())) {
                continue;
            }
            for (String given : pathsOf(rc.getName(), value)) {
                Path p;
                try {
                    p = Path.of(given);
                } catch (RuntimeException e) {
                    continue; // not a path: the command says what is wrong with it
                }
                try {
                    contain(session, List.of(real(absolute(p))));
                } catch (CommandException e) {
                    throw !p.isAbsolute() ? new CommandException(e.getCode(), e.getMessage() + " (" + rc.getName()
                            + " \"" + given + "\" is relative, so it was taken from " + absolute(Path.of(""))
                            + "; give absolute paths)") : e;
                }
            }
        }
    }

    /** The paths a command argument names: none for a blank, {@code none}, a built-in template name or a scope word. */
    private static List<String> pathsOf(String name, Object value) {
        List<String> out = new java.util.ArrayList<>();
        if (value instanceof Path p) {
            out.add(p.toString());
        } else if (value instanceof java.util.Collection<?> c) {
            for (Object o : c) {
                out.addAll(pathsOf(name, o));
            }
        } else if (value instanceof String s && !s.isBlank() && !s.trim().equalsIgnoreCase("none")) {
            String v = s.trim();
            if ("template".equals(name) && !(v.contains("/") || v.contains("\\") || v.contains("."))) {
                return out; // a built-in template, by name
            }
            if ("filePattern".equals(name)) {
                // a glob: its fixed part is where it reads
                int glob = v.length();
                for (char ch : new char[] {'*', '?', '[', '{'}) {
                    int i = v.indexOf(ch);
                    if (i >= 0) {
                        glob = Math.min(glob, i);
                    }
                }
                v = v.substring(0, glob);
                int sep = Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\'));
                v = glob < s.trim().length() ? (sep < 0 ? "." : v.substring(0, Math.max(sep, 1))) : v;
            }
            out.add(v);
        }
        return out;
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
        String current = text(lookup);
        if (current == null) {
            if (lookup != null && !Files.exists(absolute(lookup))) {
                throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND, "No such file: " + lookup);
            }
            if (lookup != null && Files.isRegularFile(absolute(lookup)) && !closedFiles.getAsBoolean()) {
                throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
                        "'" + lookup + "' is not open in the editor, and the writer has set agents to edit only "
                        + "open documents: read it with get_document_content (which opens it) or open_document, "
                        + "then edit it");
            }
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
        if (lookup != null && documents.content(lookup) == null) {
            // A closed file: the write must find it still holding what was checked here (FileEdits)
            FileEdits.verified(absolute(lookup), seen);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /**
     * Stands in for an untitled active document, which has no path yet. Compared by
     * identity, and relative where every real key is absolute, so it never matches a
     * file. No characters Windows forbids in a path: this loads with the class.
     */
    static final Path ACTIVE = Path.of("active-document");

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
