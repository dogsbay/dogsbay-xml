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
package com.dogsbay.agent.acp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.acp.AcpWire.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AcpWireTest {

    @Test
    void unknownUpdatesAndNonTextBlocksAreToleratedNotFatal() throws Exception {
        ObjectNode u = AcpWire.JSON.createObjectNode();
        u.put("sessionUpdate", "available_commands_update");
        assertThat(AcpWire.parseUpdate(u)).isInstanceOf(Update.Other.class);

        ObjectNode img = AcpWire.JSON.createObjectNode();
        img.put("sessionUpdate", "agent_message_chunk");
        img.putObject("content").put("type", "image").put("data", "...");
        assertThat(AcpWire.parseUpdate(img)).isEqualTo(new Update.AgentMessage("[image]"));
    }

    @Test
    void permissionRequestReadsV1ToolCallAndV2Subject() throws Exception {
        var v1 = AcpWire.JSON.readTree("""
            {"sessionId":"s","toolCall":{"toolCallId":"c","title":"Edit"},"options":[{"optionId":"a","kind":"allow_always"}]}""");
        var r1 = AcpWire.PermissionRequest.from(v1);
        assertThat(r1.toolCall().title()).isEqualTo("Edit");
        assertThat(r1.options().get(0).allows()).isTrue();
        assertThat(r1.options().get(0).isAlways()).isTrue();

        var v2 = AcpWire.JSON.readTree("""
            {"sessionId":"s","title":"Approve?","subject":{"type":"tool_call","toolCall":{"toolCallId":"c"}},
             "options":[{"optionId":"r","kind":"reject_once"}]}""");
        var r2 = AcpWire.PermissionRequest.from(v2);
        assertThat(r2.toolCall().id()).isEqualTo("c");
        assertThat(r2.toolCall().title()).isEqualTo("Approve?");
        assertThat(r2.options().get(0).allows()).isFalse();
    }

    @Test
    void stopReasonsMapAndUnknownIsOther() {
        assertThat(AcpWire.StopReason.from("end_turn")).isEqualTo(AcpWire.StopReason.END_TURN);
        assertThat(AcpWire.StopReason.from("refusal")).isEqualTo(AcpWire.StopReason.REFUSAL);
        assertThat(AcpWire.StopReason.from("weird")).isEqualTo(AcpWire.StopReason.OTHER);
        assertThat(AcpWire.StopReason.from(null)).isEqualTo(AcpWire.StopReason.OTHER);
    }
}
