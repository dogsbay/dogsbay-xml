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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class WriteLeaseTest {

    /** A clock the test advances by hand. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final ManualClock clock = new ManualClock();
    private final AgentSessionRegistry registry = new AgentSessionRegistry(clock, "u");
    private final WriteLease leases = new WriteLease(clock, Duration.ofMinutes(5));
    private final Path topic = Path.of("/proj/topics/a.dita");

    @Test
    void firstWriterTakesTheLeaseAndCanRenewIt() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        assertThat(leases.acquire(topic, a)).isEmpty();
        assertThat(leases.acquire(topic, a)).isEmpty();
        assertThat(leases.holder(topic)).contains(a);
        assertThat(leases.held(a)).containsExactly(topic.toAbsolutePath().normalize());
    }

    @Test
    void secondAgentIsRefusedWithTheHoldersName() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "Claude", "ai:claude", null);
        AgentSession b = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex", null);
        leases.acquire(topic, a);

        var refusal = leases.acquire(topic, b);
        assertThat(refusal).isPresent();
        assertThat(refusal.get().holder()).isEqualTo(a);
        assertThat(refusal.get().message()).contains("a.dita").contains("Claude").contains("ai:claude");
        assertThat(leases.holder(topic)).contains(a);
    }

    @Test
    void theUserIsNeverRefusedAndNeverHoldsALease() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        leases.acquire(topic, a);
        assertThat(leases.acquire(topic, registry.user())).isEmpty();
        assertThat(leases.holder(topic)).contains(a);
        assertThat(leases.held(registry.user())).isEmpty();
    }

    @Test
    void leasesExpire() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        AgentSession b = registry.open(SessionKind.BUILTIN, "B", null, null);
        leases.acquire(topic, a);
        clock.advance(Duration.ofMinutes(6));
        assertThat(leases.holder(topic)).isEmpty();
        assertThat(leases.acquire(topic, b)).isEmpty();
        assertThat(leases.holder(topic)).contains(b);
    }

    @Test
    void releaseAllFreesEveryFileTheSessionHeld() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        AgentSession b = registry.open(SessionKind.BUILTIN, "B", null, null);
        Path other = Path.of("/proj/topics/b.dita");
        leases.acquire(topic, a);
        leases.acquire(other, a);
        Path theirs = Path.of("/proj/topics/c.dita");
        leases.acquire(theirs, b);

        assertThat(leases.releaseAll(a)).hasSize(2);
        assertThat(leases.holder(topic)).isEmpty();
        assertThat(leases.holder(other)).isEmpty();
        assertThat(leases.holder(theirs)).contains(b);
    }

    @Test
    void releaseOnlyHonoursTheHolder() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        AgentSession b = registry.open(SessionKind.BUILTIN, "B", null, null);
        leases.acquire(topic, a);
        leases.release(topic, b);
        assertThat(leases.holder(topic)).contains(a);
        leases.release(topic, a);
        assertThat(leases.holder(topic)).isEmpty();
    }

    @Test
    void pathsAreNormalised() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", null, null);
        leases.acquire(Path.of("/proj/topics/../topics/a.dita"), a);
        assertThat(leases.holder(topic)).contains(a);
    }
}
