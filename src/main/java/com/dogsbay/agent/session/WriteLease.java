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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-document write leases. A session takes a lease on first write and
 * releases it when its turn ends; another session writing the same document
 * meanwhile is refused with the holder's name. Leases expire so a crashed
 * agent cannot pin a file.
 *
 * <p>The user session never needs a lease and is never refused: a person
 * editing a topic that an agent holds is the agent's problem, and the
 * content conflict gate is what tells the agent. Enforced by
 * {@code com.dogsbay.dogsbayaieditor.commands.WriteGate}.
 */
public final class WriteLease {

    /** Why a write was refused. */
    public record Refusal(Path file, AgentSession holder, Instant expiresAt) {
        public String message() {
            return "'" + file.getFileName() + "' is being edited by " + holder.displayName()
                    + " (" + holder.identity() + "); try again after their turn ends";
        }
    }

    private record Lease(AgentSession holder, Instant expiresAt) {}

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Duration ttl;
    private final Map<Path, Lease> leases = new ConcurrentHashMap<>();

    public WriteLease() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    public WriteLease(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock);
        this.ttl = Objects.requireNonNull(ttl);
    }

    /**
     * Try to take or renew the lease on {@code file} for {@code session}.
     *
     * @return empty when the write may proceed, or the refusal to report
     */
    public Optional<Refusal> acquire(Path file, AgentSession session) {
        return acquire(file, session, ttl);
    }

    /** As {@link #acquire(Path, AgentSession)} with a lease length for this caller. */
    public Optional<Refusal> acquire(Path file, AgentSession session, Duration ttl) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(session);
        Objects.requireNonNull(ttl);
        if (!session.isAgent()) {
            return Optional.empty();
        }
        Path key = normalise(file);
        Instant now = clock.instant();
        Lease[] refused = new Lease[1];
        leases.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)
                    || existing.holder().id().equals(session.id())) {
                return new Lease(session, now.plus(ttl));
            }
            refused[0] = existing;
            return existing;
        });
        return refused[0] == null ? Optional.empty()
                : Optional.of(new Refusal(key, refused[0].holder(), refused[0].expiresAt()));
    }

    /** Who holds the lease on {@code file}, if anyone and if not expired. */
    public Optional<AgentSession> holder(Path file) {
        Lease lease = leases.get(normalise(file));
        if (lease == null || lease.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(lease.holder());
    }

    /** Release every lease held by {@code session}; call at turn end and on close. */
    public List<Path> releaseAll(AgentSession session) {
        Objects.requireNonNull(session);
        List<Path> released = new java.util.ArrayList<>();
        leases.entrySet().removeIf(e -> {
            boolean mine = e.getValue().holder().id().equals(session.id());
            if (mine) {
                released.add(e.getKey());
            }
            return mine;
        });
        return Collections.unmodifiableList(released);
    }

    public void release(Path file, AgentSession session) {
        leases.computeIfPresent(normalise(file),
                (k, l) -> l.holder().id().equals(session.id()) ? null : l);
    }

    /** Files currently leased by {@code session}. */
    public List<Path> held(AgentSession session) {
        Instant now = clock.instant();
        return leases.entrySet().stream()
                .filter(e -> e.getValue().holder().id().equals(session.id()))
                .filter(e -> !e.getValue().expiresAt().isBefore(now))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static Path normalise(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
