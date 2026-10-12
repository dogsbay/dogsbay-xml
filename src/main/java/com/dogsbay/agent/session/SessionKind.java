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

/**
 * Who is behind a session. Drives attribution, the audit log and, from
 * Phase 1 on, the write gate's path-containment rule.
 */
public enum SessionKind {
    /** The person at the keyboard, driving the GUI or the CLI directly. */
    USER,
    /** The built-in xagent chat in the sidebar. */
    BUILTIN,
    /** An Agent Client Protocol agent the editor spawned and hosts. */
    ACP_HOSTED,
    /** An external MCP client (Claude Code, Cursor, ...) on the {@code /mcp} endpoint. */
    EXTERNAL_MCP,
    /** An external JSON-RPC client (the CLI's editor commands, scripts) on {@code /rpc}. */
    EXTERNAL_RPC;

    /** True for every kind that is a program acting on the user's behalf. */
    public boolean isAgent() {
        return this != USER;
    }
}
