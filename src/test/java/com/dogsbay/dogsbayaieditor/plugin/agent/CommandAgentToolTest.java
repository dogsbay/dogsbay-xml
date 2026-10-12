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
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.xagent.tool.AgentToolResult;

class CommandAgentToolTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void executesViaDispatcherAndSerializesResult() throws Exception {
        JsonNode schema = M.createObjectNode();
        // dispatcher echoes a typed result record-like object
        CommandAgentTool.Dispatcher dispatcher = (method, args) -> {
            assertThat(method).isEqualTo("validate");
            return M.readTree("{\"valid\":true,\"errors\":[]}");
        };
        CommandAgentTool tool = new CommandAgentTool(
                "validate_document", "Validate XML", schema, "validate", true, dispatcher, () -> null);

        assertThat(tool.name()).isEqualTo("validate_document");
        assertThat(tool.isReadOnly()).isTrue();

        AgentToolResult result = tool.execute("c1", M.readTree("{\"file\":\"x.xml\"}"), () -> false, r -> {});
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("\"valid\":true");
    }

    @Test
    void dispatcherFailureBecomesToolError() throws Exception {
        CommandAgentTool.Dispatcher failing = (method, args) -> {
            throw new IllegalStateException("FILE_NOT_FOUND");
        };
        CommandAgentTool tool = new CommandAgentTool(
                "document_info", "Info", M.createObjectNode(), "info", true, failing, () -> null);

        AgentToolResult result = tool.execute("c2", M.createObjectNode(), () -> false, r -> {});
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("FILE_NOT_FOUND");
    }

    @Test
    void resolvesRelativePathArgsAgainstWorkingDir(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("guide.ditamap"), "<map/>");
        AtomicReference<JsonNode> seen = new AtomicReference<>();
        CommandAgentTool.Dispatcher capture = (method, args) -> {
            seen.set(args);
            return "{}";
        };
        CommandAgentTool tool = new CommandAgentTool(
                "project_health", "Health", M.createObjectNode(), "health", true, capture, () -> tmp);

        tool.execute("c3",
                M.readTree("{\"map\":\"guide.ditamap\",\"root\":\".\",\"xpath\":\"//x[@a]\"}"),
                () -> false, r -> {});

        JsonNode args = seen.get();
        assertThat(args.get("map").asText())          // relative file → absolute
                .isEqualTo(tmp.resolve("guide.ditamap").toString());
        assertThat(args.get("root").asText())         // "." → project root
                .isEqualTo(tmp.toString());
        assertThat(args.get("xpath").asText())        // XPath left untouched
                .isEqualTo("//x[@a]");
    }
}
