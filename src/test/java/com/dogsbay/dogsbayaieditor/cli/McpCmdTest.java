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

package com.dogsbay.dogsbayaieditor.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.mcp.McpStdioServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The editor's tools over stdio without the editor (dogsbay-xml mcp), end to end. */
class McpCmdTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOPIC = "<topic id=\"a\"><title>A</title><body><p>Old words.</p></body></topic>";

    @TempDir Path project;
    private Path file;

    @BeforeEach
    void setUp() throws Exception {
        project = project.toRealPath();
        file = Files.writeString(project.resolve("a.dita"), TOPIC);
    }

    /** Send the requests to a fresh server; return one answer per line written. */
    private List<JsonNode> session(boolean proposals, Set<Path> unsaved, String... requests) throws Exception {
        StringBuilder in = new StringBuilder("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":"
                + "{\"clientInfo\":{\"name\":\"Test Client\"}}}\n{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");
        for (String r : requests) {
            in.append(r).append('\n');
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new McpStdioServer(McpCmd.server(project, proposals, () -> unsaved)).run(
                new BufferedReader(new StringReader(in.toString())), new PrintStream(out, true, StandardCharsets.UTF_8));
        List<JsonNode> answers = new ArrayList<>();
        for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
            answers.add(JSON.readTree(line)); // every line is a JSON-RPC message: nothing else reaches stdout
        }
        return answers;
    }

    private static String call(int id, String tool, String args) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":" + args + "}}";
    }

    private String fileArg() {
        return JSON.valueToTree(file.toString()).toString();
    }

    private static String text(JsonNode answer) {
        return answer.path("result").path("content").path(0).path("text").asText();
    }

    private static boolean isError(JsonNode answer) {
        return answer.path("result").path("isError").asBoolean(false) || answer.has("error");
    }

    @Test
    void offersTheToolsThatWorkWithoutTheEditorAndSaysSo() throws Exception {
        List<JsonNode> a = session(true, Set.of(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertThat(a).hasSize(2).as("the notification is not answered");
        assertThat(a.get(0).path("result").path("instructions").asText()).contains("without the editor")
                .contains(project.toString()).contains("review proposals");
        List<String> names = new ArrayList<>();
        a.get(1).path("result").path("tools").forEach(t -> names.add(t.path("name").asText()));
        assertThat(names).contains("get_document_content", "edit_document", "edit_structure", "review_revert",
                "validate_document", "project_health", "live_preview", "live_preview_status")
                .doesNotContain("open_document", "get_cursor", "author_outline", "open_project");
    }

    @Test
    void anAgentReadsThenEditsAFileAsProposalsAndCanTakeThemBack() throws Exception {
        List<JsonNode> a = session(true, Set.of(),
                call(1, "edit_document", "{\"file\":" + fileArg() + ",\"find\":\"Old\",\"replace\":\"New\"}"),
                call(2, "get_document_content", "{\"file\":" + fileArg() + "}"),
                call(3, "edit_document", "{\"file\":" + fileArg() + ",\"find\":\"Old\",\"replace\":\"New\"}"));
        assertThat(isError(a.get(1))).as("edit before a read").isTrue();
        assertThat(text(a.get(1))).contains("Read the document before editing it");
        assertThat(text(a.get(3))).contains("rev=\\\"mcp:test-client\\\"");
        assertThat(Files.readString(file)).contains("rev=\"mcp:test-client review-mark\"");

        List<JsonNode> b = session(true, Set.of(),
                call(1, "get_document_content", "{\"file\":" + fileArg() + "}"),
                call(2, "review_revert", "{\"file\":" + fileArg() + "}"));
        assertThat(isError(b.get(2))).isFalse();
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
        assertThat(Files.exists(project.resolve(".dogsbay/agent-audit/commands.jsonl"))).isTrue();
    }

    @Test
    void withProposalsOffAnEditIsPlainAndRevertStillTakesItBack() throws Exception {
        List<JsonNode> a = session(false, Set.of(),
                call(1, "get_document_content", "{\"file\":" + fileArg() + "}"),
                call(2, "edit_document", "{\"file\":" + fileArg() + ",\"find\":\"Old\",\"replace\":\"New\"}"));
        assertThat(isError(a.get(2))).isFalse();
        assertThat(Files.readString(file)).isEqualTo(TOPIC.replace("Old", "New"));
        assertThat(a.get(0).path("result").path("instructions").asText()).contains("plain text");
    }

    @Test
    void theFolderIsTheSandboxAndAnUnsavedEditorTabIsNotWrittenUnder() throws Exception {
        Path outside = Files.createTempFile("outside", ".dita");
        try {
            List<JsonNode> a = session(true, Set.of(file),
                    call(1, "get_document_content", "{\"file\":" + JSON.valueToTree(outside.toString()) + "}"),
                    call(2, "get_document_content", "{\"file\":" + fileArg() + "}"),
                    call(3, "edit_document", "{\"file\":" + fileArg() + ",\"find\":\"Old\",\"replace\":\"New\"}"),
                    call(4, "open_document", "{\"file\":" + fileArg() + "}"));
            assertThat(text(a.get(1))).contains("outside the project");
            assertThat(text(a.get(3))).contains("open with unsaved changes");
            assertThat(Files.readString(file)).isEqualTo(TOPIC);
            assertThat(a.get(4).path("error").path("message").asText()).contains("Unknown tool: open_document");
        } finally {
            Files.deleteIfExists(outside);
        }
    }
    @Test
    void notificationsAreNotAnsweredAndBadInputGetsAProperError() throws Exception {
        String in = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":3}}\n"
                + "this is not json\n"
                + "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new McpStdioServer(McpCmd.server(project, true, Set::of)).run(new BufferedReader(new StringReader(in)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        String[] lines = out.toString(StandardCharsets.UTF_8).split("\n");
        assertThat(lines).hasSize(4).as("initialize, parse error, batch refusal, ping; the notification unanswered");
        assertThat(JSON.readTree(lines[1]).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(JSON.readTree(lines[1]).has("id")).isTrue();
        assertThat(JSON.readTree(lines[2]).path("error").path("message").asText()).contains("Batches");
        assertThat(JSON.readTree(lines[3]).path("id").asInt()).isEqualTo(2);
    }
}
