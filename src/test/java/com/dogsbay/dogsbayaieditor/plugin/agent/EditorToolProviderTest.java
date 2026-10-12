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

import org.junit.jupiter.api.Test;

import com.xagent.tool.AgentTool;

/**
 * The MCP tool catalog is built without a live editor (it's static metadata), so
 * the read-only filter is testable with a null executor — no full editor needed.
 */
class EditorToolProviderTest {

    @Test
    void exposesAllToolsClassifiedReadOnlyVsMutating() {
        List<AgentTool> tools = EditorToolProvider.tools(null, () -> null);
        var byName = tools.stream().collect(
                java.util.stream.Collectors.toMap(AgentTool::name, t -> t));

        // read-only tools present and flagged read-only (run without approval)
        for (String ro : List.of("validate_document", "xpath_query", "where_used",
                "get_document_content", "get_document_outline", "list_open_documents",
                "author_outline", "search_project", "open_document", "live_preview_status")) {
            assertThat(byName).containsKey(ro);
            assertThat(byName.get(ro).isReadOnly()).as(ro + " read-only").isTrue();
        }

        // mutating tools NOW present, flagged NOT read-only (so approval prompts)
        for (String mut : List.of("set_document_content", "save_document", "edit_document", "edit_structure",
                "replace_selection", "rename_file", "delete_file", "split_topic", "keyify", "live_preview")) {
            assertThat(byName).containsKey(mut);
            assertThat(byName.get(mut).isReadOnly()).as(mut + " requires approval").isFalse();
        }

        // The Author view's editing tools are not offered: they rewrite the whole file from the
        // view's model (re-indenting it, collapsing whitespace), so a one-block change lands as a
        // whole-file diff. edit_document makes the surgical change instead. The Author view keeps
        // them in the editor and its CLI, and agents still read it with author_outline.
        assertThat(byName).doesNotContainKeys("author_insert_block", "author_set_text")
                .containsKeys("author_outline", "author_issues");
    }
}
