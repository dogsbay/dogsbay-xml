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
package com.dogsbay.agent.session;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Append-only provenance record of what every agent session did.
 *
 * <p>One JSON object per line in {@code <root>/.dogsbay/agent-audit/commands.jsonl},
 * where {@code root} is the project root at the time of the entry (a supplier,
 * because the open project changes). Only agent sessions are recorded; the
 * user's own commands are not. Entries never contain the session token.
 *
 * <p>The folder carries a {@code .gitignore} that excludes everything, written
 * on first use, so the log never lands in the team's repository by accident.
 *
 * <p>Writes are serialised and use {@code APPEND}, which is atomic per line on
 * local file systems. A failure to write is reported to the failure hook and
 * never propagates into the command that caused it.
 */
public final class AuditLog {

    public static final String DIR = ".dogsbay/agent-audit";
    public static final String FILE = "commands.jsonl";

    /** One recorded command. {@code files} may be empty; {@code detail} may be null. */
    public record Entry(
            Instant at,
            String sessionId,
            SessionKind kind,
            String identity,
            String displayName,
            String command,
            List<String> files,
            boolean dryRun,
            String outcome,
            String detail) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<Path> root;
    private final Clock clock;
    private final java.util.function.Consumer<Exception> onFailure;
    private final Object lock = new Object();

    public AuditLog(Supplier<Path> projectRoot) {
        this(projectRoot, Clock.systemUTC(), e -> { });
    }

    public AuditLog(Supplier<Path> projectRoot, Clock clock,
            java.util.function.Consumer<Exception> onFailure) {
        this.root = Objects.requireNonNull(projectRoot);
        this.clock = Objects.requireNonNull(clock);
        this.onFailure = Objects.requireNonNull(onFailure);
    }

    /** Record a command run by {@code session}. No-op for the user session. */
    public void record(AgentSession session, String command, List<String> files,
            boolean dryRun, String outcome, String detail) {
        if (session == null || !session.isAgent()) {
            return;
        }
        Entry entry = new Entry(clock.instant(), session.id(), session.kind(),
                session.identity(), session.displayName(), command,
                files == null ? List.of() : List.copyOf(files), dryRun, outcome, detail);
        append(entry);
    }

    public void append(Entry entry) {
        Path dir = root.get();
        if (dir == null) {
            return;
        }
        try {
            Path file = dir.resolve(DIR).resolve(FILE);
            String line = MAPPER.writeValueAsString(toJson(entry)) + "\n";
            synchronized (lock) {
                Path parent = file.getParent();
                if (!Files.isDirectory(parent)) {
                    Files.createDirectories(parent);
                }
                // .dogsbay/ is the committed team folder; the log is per machine
                // and grows without bound, so it excludes itself from git.
                Path ignore = parent.resolve(".gitignore");
                if (!Files.exists(ignore)) {
                    Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
                }
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException | RuntimeException e) {
            onFailure.accept(e);
        }
    }

    /**
     * Read every entry under {@code projectRoot}, oldest first; empty when
     * none. A line that cannot be parsed (a write cut short by a crash, an
     * unknown kind from a newer build) is skipped, not fatal: a torn last
     * line must not make the whole history unreadable.
     */
    public static List<Entry> read(Path projectRoot) {
        Path file = projectRoot.resolve(DIR).resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<Entry> out = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    out.add(fromJson(MAPPER.readTree(line)));
                } catch (IOException | RuntimeException skip) {
                    // unreadable line
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SessionKind kindOf(String name) {
        try {
            return SessionKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SessionKind.EXTERNAL_MCP;   // a kind this build does not know; still an agent
        }
    }

    static ObjectNode toJson(Entry e) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("at", e.at().toString());
        n.put("session", e.sessionId());
        n.put("kind", e.kind().name());
        n.put("identity", e.identity());
        n.put("name", e.displayName());
        n.put("command", e.command());
        var files = n.putArray("files");
        e.files().forEach(files::add);
        n.put("dryRun", e.dryRun());
        n.put("outcome", e.outcome());
        if (e.detail() != null) {
            n.put("detail", e.detail());
        }
        return n;
    }

    static Entry fromJson(JsonNode n) {
        List<String> files = new ArrayList<>();
        if (n.has("files")) {
            n.get("files").forEach(f -> files.add(f.asText()));
        }
        return new Entry(
                Instant.parse(n.path("at").asText()),
                n.path("session").asText(),
                kindOf(n.path("kind").asText()),
                n.path("identity").asText(),
                n.path("name").asText(),
                n.path("command").asText(),
                files,
                n.path("dryRun").asBoolean(false),
                n.path("outcome").asText(),
                n.hasNonNull("detail") ? n.get("detail").asText() : null);
    }
}
