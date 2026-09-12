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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AgentSessionRegistryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);
    private final AgentSessionRegistry registry = new AgentSessionRegistry(clock, "alice");

    @Test
    void userSessionAlwaysExistsAndIsFirst() {
        AgentSession user = registry.user();
        assertThat(user.kind()).isEqualTo(SessionKind.USER);
        assertThat(user.identity()).isEqualTo("user:alice");
        assertThat(user.token()).isNull();
        assertThat(registry.list()).containsExactly(user);
        assertThat(registry.get("user")).contains(user);
    }

    @Test
    void openingTheUserKindIsRejected() {
        assertThatThrownBy(() -> registry.open(SessionKind.USER, "x", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void httpReachableKindsGetTokensOthersDoNot() {
        AgentSession builtin = registry.open(SessionKind.BUILTIN, "AI Agent", "ai:xagent/m", null);
        AgentSession mcp = registry.open(SessionKind.EXTERNAL_MCP, "Claude Code", null, null);
        AgentSession rpc = registry.open(SessionKind.EXTERNAL_RPC, "cli", null, null);
        AgentSession acp = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp",
                CapabilityTier.T3_DEVELOPER);

        assertThat(builtin.token()).isNull();
        assertThat(mcp.token()).startsWith("dbs_").hasSizeGreaterThan(40);
        assertThat(rpc.token()).startsWith("dbs_");
        assertThat(acp.token()).startsWith("dbs_");
        assertThat(mcp.token()).isNotEqualTo(rpc.token());

        assertThat(registry.byToken(mcp.token())).contains(mcp);
        assertThat(registry.byToken(rpc.token())).contains(rpc);
        assertThat(registry.byToken("dbs_not-a-token")).isEmpty();
        assertThat(registry.byToken(null)).isEmpty();
    }

    @Test
    void defaultsFillInNameIdentityAndTier() {
        AgentSession s = registry.open(SessionKind.EXTERNAL_MCP, " ", " ", null);
        assertThat(s.displayName()).isEqualTo(s.id());
        assertThat(s.identity()).isEqualTo("mcp:" + s.id());
        assertThat(s.tier()).isEqualTo(CapabilityTier.T1_COMMANDS);
        assertThat(s.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void idsAreUniqueKindPrefixedAndNotGuessable() {
        AgentSession a = registry.open(SessionKind.BUILTIN, "a", null, null);
        AgentSession b = registry.open(SessionKind.BUILTIN, "b", null, null);
        assertThat(a.id()).startsWith("builtin-").hasSizeGreaterThan("builtin-".length() + 12);
        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(a.id()).doesNotMatch(".*-[0-9]+$");
    }

    @Test
    void idleSessionsOfAKindAreClosedOthersKept() {
        WriteLeaseTest.ManualClock clock = new WriteLeaseTest.ManualClock();
        AgentSessionRegistry r = new AgentSessionRegistry(clock, "u");
        AgentSession stale = r.open(SessionKind.EXTERNAL_MCP, "old", null, null);
        AgentSession builtin = r.open(SessionKind.BUILTIN, "chat", null, null);
        clock.advance(java.time.Duration.ofHours(3));
        AgentSession fresh = r.open(SessionKind.EXTERNAL_MCP, "new", null, null);
        clock.advance(java.time.Duration.ofHours(2));
        r.touch(fresh.id());
        clock.advance(java.time.Duration.ofHours(3));

        assertThat(r.closeIdle(SessionKind.EXTERNAL_MCP, java.time.Duration.ofHours(4)))
                .containsExactly(stale);
        assertThat(r.get(stale.id())).isEmpty();
        assertThat(r.get(fresh.id())).isPresent();
        assertThat(r.get(builtin.id())).as("other kinds untouched").isPresent();
    }

    @Test
    void closeRemovesSessionAndItsToken() {
        AgentSession mcp = registry.open(SessionKind.EXTERNAL_MCP, "c", null, null);
        assertThat(registry.close(mcp.id())).contains(mcp);
        assertThat(registry.get(mcp.id())).isEmpty();
        assertThat(registry.byToken(mcp.token())).isEmpty();
        assertThat(registry.close(mcp.id())).isEmpty();
    }

    @Test
    void userSessionCannotBeClosed() {
        assertThat(registry.close("user")).isEmpty();
        assertThat(registry.get("user")).isPresent();
    }

    @Test
    void updateIdentityReplacesTheSessionAndKeepsTheToken() {
        AgentSession s = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
        AgentSession updated = registry.updateIdentity(s.id(), "ai:claude-acp/opus").orElseThrow();
        assertThat(updated.identity()).isEqualTo("ai:claude-acp/opus");
        assertThat(updated.token()).isEqualTo(s.token());
        assertThat(registry.byToken(s.token())).contains(updated);
        assertThat(registry.updateIdentity("nope", "x")).isEmpty();
        registry.close(s.id());
        assertThat(registry.updateIdentity(s.id(), "late")).as("closed session is a no-op").isEmpty();
    }

    @Test
    void listenersSeeOpenUpdateCloseAndAreIsolatedFromEachOther() {
        List<SessionEvent> seen = new ArrayList<>();
        registry.addListener(e -> { throw new IllegalStateException("bad listener"); });
        registry.addListener(seen::add);

        AgentSession s = registry.open(SessionKind.BUILTIN, "a", null, null);
        registry.updateIdentity(s.id(), "ai:xagent/x");
        registry.close(s.id());

        assertThat(seen).hasSize(3);
        assertThat(seen.get(0)).isInstanceOf(SessionEvent.Opened.class);
        assertThat(seen.get(1)).isInstanceOf(SessionEvent.Updated.class);
        assertThat(seen.get(2)).isInstanceOf(SessionEvent.Closed.class);
        assertThat(seen.get(1).session().identity()).isEqualTo("ai:xagent/x");
    }

    @Test
    void listOrdersUserFirstThenByCreation() {
        Clock ticking = new Clock() {
            private long t = 0;
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochSecond(t++); }
        };
        AgentSessionRegistry r = new AgentSessionRegistry(ticking, "u");
        AgentSession a = r.open(SessionKind.EXTERNAL_MCP, "a", null, null);
        AgentSession b = r.open(SessionKind.BUILTIN, "b", null, null);
        assertThat(r.list()).containsExactly(r.user(), a, b);
    }

    @Test
    void redactedDropsTheTokenOnly() {
        AgentSession s = registry.open(SessionKind.EXTERNAL_RPC, "cli", null, null);
        AgentSession r = s.redacted();
        assertThat(r.token()).isNull();
        assertThat(r.id()).isEqualTo(s.id());
        assertThat(r.identity()).isEqualTo(s.identity());
        assertThat(registry.user().redacted()).isSameAs(registry.user());
    }
}
