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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies which tool calls produce a before/after change preview (#3). The
 * full preview needs a live editor for the "before" text, so this covers the
 * pure tool→proposed-text decision.
 */
class EditorAgentHostPreviewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode args(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("content tools expose their proposed replacement text")
    void contentTools() {
        assertThat(EditorAgentHost.proposedAfterText("replace_selection", args("{\"text\":\"<p>new</p>\"}")))
                .isEqualTo("<p>new</p>");
        assertThat(EditorAgentHost.proposedAfterText("set_document_content", args("{\"content\":\"<x/>\"}")))
                .isEqualTo("<x/>");
    }

    @Test
    @DisplayName("non-content / structural tools have no inline diff preview")
    void noPreviewForOtherTools() {
        assertThat(EditorAgentHost.proposedAfterText("rename_file",
                args("{\"from\":\"a.dita\",\"to\":\"b.dita\"}"))).isNull();
        assertThat(EditorAgentHost.proposedAfterText("bash", args("{\"command\":\"ls\"}"))).isNull();
        // missing / non-textual field → no preview
        assertThat(EditorAgentHost.proposedAfterText("replace_selection", args("{}"))).isNull();
    }
}
