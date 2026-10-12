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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The set of live sessions: who can call the command engine right now.
 *
 * <p>Thread-safe. No Swing. One instance per editor process (headless
 * callers may create their own). Tokens are minted here and looked up by
 * the HTTP server in constant time via {@link #byToken(String)}.
 */
public final class AgentSessionRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Map<String, AgentSession> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByToken = new ConcurrentHashMap<>();
    private final List<Consumer<SessionEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private final AgentSession user;

    public AgentSessionRegistry() {
        this(Clock.systemUTC(), System.getProperty("user.name", "user"));
    }

    public AgentSessionRegistry(Clock clock, String userName) {
        this.clock = Objects.requireNonNull(clock, "clock");
        String name = userName == null || userName.isBlank() ? "user" : userName.trim();
        this.user = new AgentSession("user", SessionKind.USER, name, "user:" + name,
                CapabilityTier.T3_DEVELOPER, clock.instant(), null);
        byId.put(user.id(), user);
    }

    /** The always-present session for the person at the keyboard. */
    public AgentSession user() {
        return user;
    }

    /**
     * Open a session. HTTP-reachable kinds ({@link SessionKind#ACP_HOSTED},
     * {@link SessionKind#EXTERNAL_MCP}, {@link SessionKind#EXTERNAL_RPC}) get
     * a fresh bearer token; the others get none.
     */
    public AgentSession open(SessionKind kind, String displayName, String identity,
            CapabilityTier tier) {
        Objects.requireNonNull(kind, "kind");
        if (kind == SessionKind.USER) {
            throw new IllegalArgumentException("the user session is implicit; use user()");
        }
        // Ids are echoed by MCP clients as Mcp-Session-Id, so they must not be
        // guessable: a client must not be able to name another client's session.
        String id = kind.name().toLowerCase() + "-" + randomSuffix();
        String token = needsToken(kind) ? mintToken() : null;
        AgentSession session = new AgentSession(id, kind,
                displayName == null || displayName.isBlank() ? id : displayName,
                identity == null || identity.isBlank() ? defaultIdentity(kind, id) : identity,
                tier == null ? CapabilityTier.T1_COMMANDS : tier,
                clock.instant(), token);
        byId.put(id, session);
        if (token != null) {
            idByToken.put(token, id);
        }
        fire(new SessionEvent.Opened(session));
        return session;
    }

    /**
     * Replace a session's identity (e.g. the built-in agent switched model).
     * Empty when the session is already closed: callbacks that outlive their
     * owner must not fail for that.
     */
    public Optional<AgentSession> updateIdentity(String id, String identity) {
        AgentSession updated = id == null ? null
                : byId.computeIfPresent(id, (k, s) -> s.withIdentity(identity));
        if (updated == null) {
            return Optional.empty();
        }
        fire(new SessionEvent.Updated(updated));
        return Optional.of(updated);
    }

    /** Mark {@code id} as active now, for idle sweeps. Unknown ids are ignored. */
    public void touch(String id) {
        if (id != null && byId.containsKey(id)) {
            lastSeen.put(id, clock.instant());
        }
    }

    /**
     * Close every session of {@code kind} not seen for longer than
     * {@code idle}. A session never touched counts from its creation.
     */
    public List<AgentSession> closeIdle(SessionKind kind, java.time.Duration idle) {
        Instant cutoff = clock.instant().minus(idle);
        List<AgentSession> closed = new ArrayList<>();
        for (AgentSession s : List.copyOf(byId.values())) {
            if (s.kind() != kind) {
                continue;
            }
            Instant seen = lastSeen.getOrDefault(s.id(), s.createdAt());
            if (seen.isBefore(cutoff)) {
                close(s.id()).ifPresent(closed::add);
            }
        }
        return closed;
    }

    /**
     * Close every session of {@code kind} that was never {@link #touch}ed and
     * is older than {@code age}: opened by a handshake whose client never
     * came back.
     */
    public List<AgentSession> closeUntouched(SessionKind kind, java.time.Duration age) {
        Instant cutoff = clock.instant().minus(age);
        List<AgentSession> closed = new ArrayList<>();
        for (AgentSession s : List.copyOf(byId.values())) {
            if (s.kind() == kind && !lastSeen.containsKey(s.id()) && s.createdAt().isBefore(cutoff)) {
                close(s.id()).ifPresent(closed::add);
            }
        }
        return closed;
    }

    public Optional<AgentSession> get(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** Resolve a per-session bearer token. Never matches the master token. */
    public Optional<AgentSession> byToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String id = idByToken.get(token);
        return id == null ? Optional.empty() : get(id);
    }

    /** Close a session. Closing the user session or an unknown id is a no-op. */
    public Optional<AgentSession> close(String id) {
        if (id == null || id.equals(user.id())) {
            return Optional.empty();
        }
        AgentSession removed = byId.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        if (removed.token() != null) {
            idByToken.remove(removed.token());
        }
        lastSeen.remove(id);
        fire(new SessionEvent.Closed(removed));
        return Optional.of(removed);
    }

    /** All live sessions, the user first, then by creation time. */
    public List<AgentSession> list() {
        List<AgentSession> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparing((AgentSession s) -> s.kind() != SessionKind.USER)
                .thenComparing(AgentSession::createdAt)
                .thenComparing(AgentSession::id));
        return Collections.unmodifiableList(all);
    }

    public void addListener(Consumer<SessionEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Consumer<SessionEvent> listener) {
        listeners.remove(listener);
    }

    private void fire(SessionEvent event) {
        for (Consumer<SessionEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (RuntimeException e) {
                // a misbehaving listener must not break the registry
            }
        }
    }

    private static boolean needsToken(SessionKind kind) {
        return kind == SessionKind.ACP_HOSTED || kind == SessionKind.EXTERNAL_MCP
                || kind == SessionKind.EXTERNAL_RPC;
    }

    private static String defaultIdentity(SessionKind kind, String id) {
        return switch (kind) {
            case USER -> "user:unknown";
            case BUILTIN -> "ai:xagent";
            case ACP_HOSTED -> "ai:" + id;
            case EXTERNAL_MCP -> "mcp:" + id;
            case EXTERNAL_RPC -> "rpc:" + id;
        };
    }

    private static String randomSuffix() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String mintToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "dbs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
