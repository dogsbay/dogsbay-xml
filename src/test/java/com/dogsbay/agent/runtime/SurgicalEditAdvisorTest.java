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

package com.dogsbay.agent.runtime;

import com.xagent.tool.AgentToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurgicalEditAdvisorTest {

    private final SurgicalEditAdvisor advisor = new SurgicalEditAdvisor();

    @Test
    @DisplayName("whole-document tools get a surgical-edit reminder appended")
    void advisesOnWholeDocTools() {
        AgentToolResult out = advisor.afterToolCall("set_document_content",
                AgentToolResult.success("Document content set"));
        assertThat(out.content())
                .startsWith("Document content set")
                .contains("prefer the surgical `edit` tool");
    }

    @Test
    @DisplayName("surgical edit + read-only tools are untouched")
    void leavesOtherToolsAlone() {
        AgentToolResult edit = AgentToolResult.success("Edited 1 occurrence");
        assertThat(advisor.afterToolCall("edit", edit)).isSameAs(edit);
        AgentToolResult grep = AgentToolResult.success("3 matches");
        assertThat(advisor.afterToolCall("grep", grep)).isSameAs(grep);
    }

    @Test
    @DisplayName("a failed tool result is not annotated")
    void skipsErrors() {
        AgentToolResult err = AgentToolResult.error("denied");
        assertThat(advisor.afterToolCall("set_document_content", err)).isSameAs(err);
    }
}
