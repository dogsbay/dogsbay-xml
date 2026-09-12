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

import java.time.Instant;
import java.util.Objects;

/**
 * One identified caller of the command engine: the user, the built-in agent,
 * a hosted ACP agent, or an external MCP/JSON-RPC client.
 *
 * <p>{@code identity} is the attribution string written into audit entries
 * and, by the proposals feature, into {@code @rev}: {@code user:<name>},
 * {@code ai:xagent/<model>}, {@code ai:<acp registry id>},
 * {@code mcp:<client name>}, {@code rpc:<client name>}.
 *
 * <p>{@code token} is the per-session bearer token the HTTP surface accepts
 * in place of the master token. It is {@code null} for sessions that never
 * reach the editor over HTTP (the user, the built-in agent). Sessions are
 * immutable; the registry replaces a session to change its identity.
 */
public record AgentSession(
        String id,
        SessionKind kind,
        String displayName,
        String identity,
        CapabilityTier tier,
        Instant createdAt,
        String token) {

    public AgentSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A copy with a different identity string (the model changed, say). */
    public AgentSession withIdentity(String newIdentity) {
        return new AgentSession(id, kind, displayName, newIdentity, tier, createdAt, token);
    }

    /** A copy without the token, safe to serialise into results and events. */
    public AgentSession redacted() {
        return token == null ? this
                : new AgentSession(id, kind, displayName, identity, tier, createdAt, null);
    }

    public boolean isAgent() {
        return kind.isAgent();
    }

    /**
     * The session an <em>unbound</em> thread resolves to. Not registered,
     * least-privileged tier: code that reaches the engine without binding a
     * session gets nothing an agent could not get. The editor substitutes the
     * registry's real user session for GUI-driven commands.
     */
    public static AgentSession anonymousUser() {
        return new AgentSession("user", SessionKind.USER, "User", "user:unknown",
                CapabilityTier.T1_COMMANDS, Instant.EPOCH, null);
    }
}
