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

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** What the chat panel shows under a failure: the agent's own last words. */
class HostedAgentReportTest {

    private static final List<String> NPX = List.of("npx", "-y", "@agentclientprotocol/claude-agent-acp@0.78.0");

    @Test
    void showsTheLastLinesTheAgentPrinted() {
        List<String> lines = IntStream.rangeClosed(1, 12).mapToObj(i -> "line " + i).toList();

        String report = HostedAgentSession.report(NPX, lines);

        assertThat(report).startsWith("\nThe agent reported:").contains("    line 12").contains("    line 5")
                .doesNotContain("line 4\n").doesNotContain("npx found the package");
    }

    @Test
    void explainsAnIncompleteNpxDownload() {
        String report = HostedAgentSession.report(NPX, List.of("sh: line 1: claude-agent-acp: command not found"));

        assertThat(report).contains("command not found").contains("~/.npm/_npx").contains("interrupted");
        assertThat(HostedAgentSession.report(List.of("C:\\nodejs\\npx.cmd", "-y", "x"),
                List.of("'x' is not recognized as an internal or external command,"))).contains("npm-cache\\_npx");
    }

    @Test
    void recognisesDashsNotFoundMessage() {
        assertThat(HostedAgentSession.report(NPX, List.of("sh: 1: claude-agent-acp: not found")))
                .as("Debian and Ubuntu run npm scripts with dash").contains("~/.npm/_npx");
    }

    @Test
    void findsTheCommandAnNpxPackageRuns() {
        assertThat(HostedAgentSession.npxBinName(NPX)).isEqualTo("claude-agent-acp");
        assertThat(HostedAgentSession.npxBinName(List.of("npx", "--yes", "gemini-cli@1.0"))).isEqualTo("gemini-cli");
        assertThat(HostedAgentSession.npxBinName(List.of("npx"))).isNull();
    }

    @Test
    void noHintForOtherLaunchersOrMessages() {
        assertThat(HostedAgentSession.report(List.of("codex-acp"), List.of("sh: codex-acp: command not found")))
                .doesNotContain("~/.npm/_npx");
        assertThat(HostedAgentSession.report(NPX, List.of("Error: invalid API key"))).doesNotContain("~/.npm/_npx");
        assertThat(HostedAgentSession.report(NPX, List.of("Error: model not found")))
                .as("a not-found that does not name the agent's command").doesNotContain("~/.npm/_npx");
        assertThat(HostedAgentSession.report(NPX, List.of())).isEmpty();
    }
}
