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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The recent ACP sessions each hosted agent had in a project, newest first,
 * so a new tab can pick a conversation up where it stopped. Lives in
 * {@code .dogsbay/local/hosted-sessions.json}, a folder that ignores itself
 * in git: session ids are this machine's, not the team's.
 */
public final class HostedSessionStore {

    public static final String DIR = ".dogsbay/local";
    public static final String FILE = "hosted-sessions.json";

    /** What was remembered for one agent. */
    public record Saved(String sessionId, Instant at, String tier, String title) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Sessions connect on their own threads; a read-modify-write of one file must not interleave. */
    private static final Object LOCK = new Object();

    private HostedSessionStore() {
    }

    /** How many conversations are kept per agent. */
    public static final int KEEP = 8;

    /** The newest remembered session for {@code agentId}, if any. */
    public static Optional<Saved> last(Path root, String agentId) {
        List<Saved> all = recent(root, agentId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** The remembered sessions for {@code agentId}, newest first. */
    public static List<Saved> recent(Path root, String agentId) {
        List<Saved> out = new ArrayList<>();
        for (JsonNode s : sessionsOf(readAll(root), agentId)) {
            if (!s.hasNonNull("sessionId") || s.get("sessionId").asText().isBlank()) {
                continue;
            }
            try {
                out.add(new Saved(s.get("sessionId").asText(), Instant.parse(s.path("at").asText()),
                        s.path("tier").asText(null), s.path("title").asText(null)));
            } catch (RuntimeException bad) {
                // one unreadable entry does not hide the others
            }
        }
        return out;
    }

    /** Record {@code sessionId} as the agent's newest; an existing entry keeps its name and moves to the front. */
    public static void remember(Path root, String agentId, String sessionId, String tier) {
        synchronized (LOCK) {
            ObjectNode all = (ObjectNode) readAll(root);
            ArrayNode sessions = sessionsOf(all, agentId);
            String title = null;
            for (int i = 0; i < sessions.size(); i++) {
                if (sessionId.equals(sessions.get(i).path("sessionId").asText())) {
                    title = sessions.get(i).path("title").asText(null);
                    sessions.remove(i);
                    break;
                }
            }
            ObjectNode s = JSON.createObjectNode();
            s.put("sessionId", sessionId);
            s.put("at", Instant.now().toString());
            if (tier != null) {
                s.put("tier", tier);
            }
            if (title != null) {
                s.put("title", title);
            }
            sessions.insert(0, s);
            while (sessions.size() > KEEP) {
                sessions.remove(sessions.size() - 1);
            }
            all.putObject(agentId).set("sessions", sessions);
            write(root, all);
        }
    }

    /** Give a remembered session a name; null or blank clears it. */
    public static void rename(Path root, String agentId, String sessionId, String title) {
        synchronized (LOCK) {
            ObjectNode all = (ObjectNode) readAll(root);
            ArrayNode sessions = sessionsOf(all, agentId);
            boolean changed = false;
            for (JsonNode s : sessions) {
                if (sessionId.equals(s.path("sessionId").asText()) && s instanceof ObjectNode o) {
                    if (title == null || title.isBlank()) {
                        o.remove("title");
                    } else {
                        o.put("title", title.strip());
                    }
                    changed = true;
                }
            }
            if (changed) {
                all.putObject(agentId).set("sessions", sessions);
                write(root, all);
            }
        }
    }

    /** Drop one remembered session. */
    public static void forget(Path root, String agentId, String sessionId) {
        synchronized (LOCK) {
            ObjectNode all = (ObjectNode) readAll(root);
            ArrayNode sessions = sessionsOf(all, agentId);
            boolean changed = false;
            for (int i = sessions.size() - 1; i >= 0; i--) {
                if (sessionId.equals(sessions.get(i).path("sessionId").asText())) {
                    sessions.remove(i);
                    changed = true;
                }
            }
            if (sessions.isEmpty()) {
                changed |= all.remove(agentId) != null;
            } else {
                all.putObject(agentId).set("sessions", sessions);
            }
            if (changed) {
                write(root, all);
            }
        }
    }

    /** Drop everything remembered for an agent. */
    public static void forget(Path root, String agentId) {
        synchronized (LOCK) {
            ObjectNode all = (ObjectNode) readAll(root);
            if (all.remove(agentId) != null) {
                write(root, all);
            }
        }
    }

    /** Every remembered agent id in the project. */
    public static List<String> agents(Path root) {
        List<String> out = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = readAll(root).fields(); it.hasNext();) {
            out.add(it.next().getKey());
        }
        return out;
    }

    /** The agent's session list, as a fresh array; the first layout kept one session per agent. */
    private static ArrayNode sessionsOf(JsonNode all, String agentId) {
        ArrayNode out = JSON.createArrayNode();
        JsonNode a = all.path(agentId);
        if (a.path("sessions").isArray()) {
            a.path("sessions").forEach(out::add);
        } else if (a.hasNonNull("sessionId")) {
            out.add(a);
        }
        return out;
    }

    private static JsonNode readAll(Path root) {
        Path f = root == null ? null : root.resolve(DIR).resolve(FILE);
        if (f == null || !Files.isRegularFile(f)) {
            return JSON.createObjectNode();
        }
        try {
            JsonNode n = JSON.readTree(Files.readString(f, StandardCharsets.UTF_8));
            return n != null && n.isObject() ? n : JSON.createObjectNode();
        } catch (IOException | RuntimeException e) {
            return JSON.createObjectNode();
        }
    }

    private static void write(Path root, ObjectNode all) {
        if (root == null) {
            return;
        }
        try {
            Path dir = root.resolve(DIR);
            Files.createDirectories(dir);
            Path ignore = dir.resolve(".gitignore");
            if (!Files.exists(ignore)) {
                Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
            }
            Path tmp = dir.resolve(FILE + ".tmp");
            Files.writeString(tmp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(all),
                    StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[hosted-sessions] could not write " + FILE + ": " + e.getMessage());
        }
    }
}
