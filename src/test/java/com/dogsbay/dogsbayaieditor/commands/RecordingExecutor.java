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

import java.util.ArrayList;
import java.util.List;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionContext;

/**
 * Test double: records each command with the session bound at execution
 * time, and returns null. Shared by the MCP and JSON-RPC session tests.
 */
public final class RecordingExecutor implements CommandExecutor {

    public record Call(Command<?> command, AgentSession session) {}

    public final List<Call> calls = new ArrayList<>();

    @Override
    public <R> R execute(Command<R> command) {
        calls.add(new Call(command, SessionContext.current()));
        return null;
    }
}
