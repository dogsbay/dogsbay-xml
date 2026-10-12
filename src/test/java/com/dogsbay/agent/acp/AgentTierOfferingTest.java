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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.session.CapabilityTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The offered set per tier, proven on the wire: what {@code initialize}
 * advertises, and what the client actually serves when the agent asks
 * anyway. This is the table the docs quote.
 */
class AgentTierOfferingTest {

    static final class MemoryFs implements AcpClient.FileSystem {
        final Map<Path, String> files = new ConcurrentHashMap<>(Map.of(Path.of("/p/a"), "x"));
        @Override public String read(String s, Path p, Integer l, Integer n) { return files.get(p); }
        @Override public void write(String s, Path p, String c) { files.put(p, c); }
    }

    @ParameterizedTest
    @EnumSource(CapabilityTier.class)
    void advertisedAndServedFileMethodsFollowTheTier(CapabilityTier tier) throws Exception {
        try (FakeAcpAgent agent = new FakeAcpAgent()) {
            agent.on("initialize", p -> FakeAcpAgent.JSON.createObjectNode().put("protocolVersion", 1));
            MemoryFs fs = new MemoryFs();
            try (AcpClient client = new AcpClient(agent.clientReads, agent.clientWrites, Tiers.capabilities(tier),
                    (s, u) -> { }, r -> null, fs)) {
                client.initialize("t", "1");
                JsonNode caps = agent.requestsFor("initialize").get(0).at("/params/clientCapabilities");
                assertThat(caps.at("/fs/readTextFile").asBoolean()).isEqualTo(tier.allowsFileReads());
                assertThat(caps.at("/fs/writeTextFile").asBoolean()).isEqualTo(tier.allowsFileWrites());
                assertThat(caps.at("/terminal").asBoolean()).as("terminals are never offered").isFalse();

                ObjectNode read = FakeAcpAgent.JSON.createObjectNode().put("sessionId", "s").put("path", "/p/a");
                ObjectNode write = FakeAcpAgent.JSON.createObjectNode().put("sessionId", "s").put("path", "/p/b").put("content", "y");
                if (tier.allowsFileReads()) {
                    assertThat(agent.request("fs/read_text_file", read).get("content").asText()).isEqualTo("x");
                } else {
                    assertThatThrownBy(() -> agent.request("fs/read_text_file", read)).hasMessageContaining("-32601");
                }
                if (tier.allowsFileWrites()) {
                    agent.request("fs/write_text_file", write);
                    assertThat(fs.files).containsEntry(Path.of("/p/b"), "y");
                } else {
                    assertThatThrownBy(() -> agent.request("fs/write_text_file", write)).hasMessageContaining("-32601");
                    assertThat(fs.files).doesNotContainKey(Path.of("/p/b"));
                }
            }
        }
    }

    @Test
    void protocolTwoAgentsAreDeveloperTierByConstruction() {
        assertThat(Tiers.isDeveloperByConstruction(new AgentInfo(2, "x", "1", java.util.List.of(), true, false, false))).isTrue();
        assertThat(Tiers.isDeveloperByConstruction(new AgentInfo(1, "x", "1", java.util.List.of(), true, false, false))).isFalse();
        assertThat(Tiers.badge(CapabilityTier.T3_DEVELOPER)).isEqualTo("T3");
    }
}
