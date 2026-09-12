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

import java.util.List;
import java.util.Map;

import com.dogsbay.agent.acp.AcpWire.McpServerConfig;

/**
 * Builds the {@code mcpServers} list handed to a hosted agent at
 * {@code session/new}, so the agent gets the editor's typed commands. The
 * decisions, after OpenKnowledge's implementation:
 *
 * <ol>
 * <li>HTTP when the agent advertised the {@code http} MCP capability;
 *     stdio otherwise, through {@link McpStdioBridge}. Without the fallback
 *     the editor's tools are silently absent from agents that only speak
 *     stdio MCP.</li>
 * <li>The session's own bearer token rides along, as a header or an
 *     environment variable, so every tool call is attributed to this
 *     session and no other.</li>
 * <li>Nothing when the editor's server is off, or when the harness already
 *     loads the editor's MCP config itself (the caller decides that).</li>
 * </ol>
 */
public final class McpInjection {

    public static final String SERVER_NAME = "dogsbay-editor";
    public static final String HOSTED_HEADER = "X-DogsBay-Hosted-Agent";
    public static final String TOKEN_ENV = "DOGSBAY_MCP_TOKEN";
    public static final String URL_ENV = "DOGSBAY_MCP_URL";

    private McpInjection() {
    }

    /**
     * @param mcpUrl        the editor's {@code /mcp} endpoint, or null when the server is off
     * @param sessionToken  the hosted session's bearer token
     * @param agentSpeaksHttp the agent's {@code mcpCapabilities.http}
     * @param bridgeCommand how to launch {@link McpStdioBridge} on this machine
     */
    public static List<McpServerConfig> servers(String mcpUrl, String sessionToken, boolean agentSpeaksHttp,
            List<String> bridgeCommand) {
        if (mcpUrl == null || sessionToken == null) {
            return List.of();
        }
        if (agentSpeaksHttp) {
            return List.of(new McpServerConfig.Http(SERVER_NAME, mcpUrl,
                    Map.of("Authorization", "Bearer " + sessionToken, HOSTED_HEADER, "1")));
        }
        if (bridgeCommand == null || bridgeCommand.isEmpty()) {
            return List.of();
        }
        return List.of(new McpServerConfig.Stdio(SERVER_NAME, bridgeCommand.get(0),
                bridgeCommand.subList(1, bridgeCommand.size()),
                Map.of(URL_ENV, mcpUrl, TOKEN_ENV, sessionToken)));
    }

    /**
     * The command that runs the bridge with this JVM's runtime and classpath.
     * {@code java.home} is used rather than the current process command: under
     * a jpackage launcher the latter is the editor itself, which would start a
     * second editor instead of the bridge.
     */
    public static List<String> bridgeCommand() {
        String cp = System.getProperty("java.class.path", "");
        if (cp.isBlank()) {
            return List.of();
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        String launcher = java.nio.file.Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toString();
        return List.of(launcher, "-cp", cp, McpStdioBridge.class.getName());
    }
}
