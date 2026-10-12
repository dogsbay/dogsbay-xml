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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostedAgentSessionMentionsTest {

    @TempDir
    Path root;

    @Test
    void mentionsOfProjectFilesBecomeAttachmentsOthersStayText() throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("topics/a.dita"), "<topic/>");
        Files.writeString(root.getParent().resolve("outside.dita"), "no");

        var got = HostedAgentSession.attachments(
                "Check @topics/a.dita and @missing.dita and @../outside.dita, email me@example.com", root);

        assertThat(got).hasSize(1);
        assertThat(HostedAgentSession.attachments("Please validate @topics/a.dita.", root))
                .as("a sentence-ending period is not part of the path").hasSize(1);
        assertThat(got.get(0).path()).isEqualTo(root.resolve("topics/a.dita"));
        assertThat(got.get(0).text()).isEqualTo("<topic/>");
        assertThat(got.get(0).mimeType()).isEqualTo("application/xml");
        assertThat(HostedAgentSession.attachments("nothing here", null)).isEmpty();
    }

    @Test
    void aDroppedFileBecomesAMentionOnlyWhenTheSyntaxCanCarryIt() throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("topics/a.dita"), "<topic/>");
        Files.writeString(root.resolve("topics/my topic.dita"), "<topic/>");
        Files.writeString(root.getParent().resolve("outside.dita"), "no");

        assertThat(HostedAgentSession.mentionFor(root.resolve("topics/a.dita").toFile(), root)).isEqualTo("@topics/a.dita");
        assertThat(HostedAgentSession.mentionFor(root.resolve("topics/my topic.dita").toFile(), root))
                .as("a space would be cut off by the mention regex on send").isNull();
        assertThat(HostedAgentSession.mentionFor(root.getParent().resolve("outside.dita").toFile(), root)).isNull();
        assertThat(HostedAgentSession.mentionFor(root.resolve("topics").toFile(), root)).isNull();
        assertThat(HostedAgentSession.mentionFor(root.resolve("topics/a.dita").toFile(), null)).isNull();
    }

    @Test
    void preferredSignInIsTheAccountFlowNotTheApiKey() {
        var apiKey = new com.dogsbay.agent.acp.AcpWire.AuthMethod("api-key", "API Key", "Use an API key");
        var chatGpt = new com.dogsbay.agent.acp.AcpWire.AuthMethod("chat-gpt", "ChatGPT", "Use ChatGPT");
        var oauth = new com.dogsbay.agent.acp.AcpWire.AuthMethod("oauth", null, null);
        var mystery = new com.dogsbay.agent.acp.AcpWire.AuthMethod("device", "Device", null);

        assertThat(HostedAgentSession.preferredAuthMethod(java.util.List.of(apiKey, chatGpt))).isEqualTo(chatGpt);
        assertThat(HostedAgentSession.preferredAuthMethod(java.util.List.of(apiKey, oauth))).isEqualTo(oauth);
        assertThat(HostedAgentSession.preferredAuthMethod(java.util.List.of(apiKey, mystery))).isEqualTo(mystery);
        assertThat(HostedAgentSession.preferredAuthMethod(java.util.List.of(apiKey))).isEqualTo(apiKey);
    }

    @Test
    void authRequiredIsRecognisedByCodeOrWording() {
        assertThat(HostedAgentSession.isAuthRequired(new com.dogsbay.agent.acp.JsonRpcConnection.RpcException(-32000, "x"))).isTrue();
        assertThat(HostedAgentSession.isAuthRequired(new com.dogsbay.agent.acp.JsonRpcConnection.RpcException(-32603, "Authentication required"))).isTrue();
        assertThat(HostedAgentSession.isAuthRequired(new com.dogsbay.agent.acp.JsonRpcConnection.RpcException(-32603, "model unavailable"))).isFalse();
        assertThat(HostedAgentSession.isAuthRequired(new com.dogsbay.agent.acp.JsonRpcConnection.RpcException(-32603, "authoring model unavailable")))
                .as("'auth' inside another word is not a sign-in request").isFalse();
        assertThat(HostedAgentSession.isAuthRequired(new com.dogsbay.agent.acp.JsonRpcConnection.RpcException(-32603, "Not logged in"))).isTrue();
    }

    @Test
    void mimeTypesFollowTheExtension() {
        assertThat(HostedAgentSession.mime(Path.of("a.dita"))).isEqualTo("application/xml");
        assertThat(HostedAgentSession.mime(Path.of("m.ditamap"))).isEqualTo("application/xml");
        assertThat(HostedAgentSession.mime(Path.of("r.md"))).isEqualTo("text/markdown");
        assertThat(HostedAgentSession.mime(Path.of("n.txt"))).isEqualTo("text/plain");
    }
}
