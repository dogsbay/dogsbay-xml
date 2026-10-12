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
package com.dogsbay.dogsbayaieditor.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.ListDocumentsCommand;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;

class JsonRpcHandlerSessionTest {

    private final RecordingExecutor executor = new RecordingExecutor();
    private final JsonRpcHandler handler = new JsonRpcHandler(executor);
    private final AgentSessionRegistry registry = new AgentSessionRegistry();

    @Test
    void handleBindsTheGivenSessionForTheCommand() {
        AgentSession cli = registry.open(SessionKind.EXTERNAL_RPC, "cli", "rpc:cli", null);
        String response = handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"listDocuments","params":{}}""", cli);

        assertThat(response).contains("\"result\"");
        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).command()).isInstanceOf(ListDocumentsCommand.class);
        assertThat(executor.calls.get(0).session()).isEqualTo(cli);
        assertThat(SessionContext.isBound()).isFalse();
    }

    @Test
    void handleWithoutASessionUsesWhateverIsBoundOrTheUser() {
        handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"listDocuments","params":{}}""");
        assertThat(executor.calls.get(0).session().kind()).isEqualTo(SessionKind.USER);

        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        SessionContext.run(agent, () -> handler.handle("""
            {"jsonrpc":"2.0","id":2,"method":"listDocuments","params":{}}"""));
        assertThat(executor.calls.get(1).session()).isEqualTo(agent);
    }

    @Test
    void unknownMethodsStillReportAnErrorAndUnbind() {
        AgentSession cli = registry.open(SessionKind.EXTERNAL_RPC, "cli", null, null);
        String response = handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"nope","params":{}}""", cli);
        assertThat(response).contains("Unknown method");
        assertThat(SessionContext.isBound()).isFalse();
    }
}
