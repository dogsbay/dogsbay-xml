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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.acp.AcpWire.McpServerConfig;

class McpInjectionTest {

    @Test
    void httpWhenTheAgentSpeaksItWithTheSessionToken() {
        List<McpServerConfig> s = McpInjection.servers("http://127.0.0.1:19601/mcp", "dbs_t", true, List.of("java"));
        assertThat(s).hasSize(1);
        McpServerConfig.Http h = (McpServerConfig.Http) s.get(0);
        assertThat(h.url()).isEqualTo("http://127.0.0.1:19601/mcp");
        assertThat(h.headers()).containsEntry("Authorization", "Bearer dbs_t").containsEntry(McpInjection.HOSTED_HEADER, "1");
    }

    @Test
    void stdioBridgeOtherwiseCarryingUrlAndTokenInTheEnvironment() {
        List<McpServerConfig> s = McpInjection.servers("http://127.0.0.1:19601/mcp", "dbs_t", false,
                List.of("/usr/bin/java", "-cp", "x.jar", McpStdioBridge.class.getName()));
        McpServerConfig.Stdio b = (McpServerConfig.Stdio) s.get(0);
        assertThat(b.command()).isEqualTo("/usr/bin/java");
        assertThat(b.args()).containsExactly("-cp", "x.jar", McpStdioBridge.class.getName());
        assertThat(b.env()).containsEntry(McpInjection.URL_ENV, "http://127.0.0.1:19601/mcp")
                .containsEntry(McpInjection.TOKEN_ENV, "dbs_t");
    }

    @Test
    void nothingWhenTheServerIsOffOrTheBridgeCannotBeLaunched() {
        assertThat(McpInjection.servers(null, "t", true, List.of("java"))).isEmpty();
        assertThat(McpInjection.servers("http://x/mcp", null, true, List.of("java"))).isEmpty();
        assertThat(McpInjection.servers("http://x/mcp", "t", false, List.of())).isEmpty();
    }

    @Test
    void bridgeCommandUsesThisJvm() {
        List<String> c = McpInjection.bridgeCommand();
        assertThat(c).isNotEmpty();
        assertThat(c.get(0)).contains("java");
        assertThat(c).contains("-cp").last().isEqualTo(McpStdioBridge.class.getName());
    }
}
