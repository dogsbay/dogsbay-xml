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

import java.nio.file.Path;

/**
 * What a hosted agent is told on its first turn. ACP gives the client no
 * system prompt, so this rides in front of the first user message, the way
 * the built-in chat prepends editor context. It is the hosted equivalent of
 * the built-in agent's DITA skills: which editor tools exist, what "validate"
 * means here, and to prefer them over a shell. Short on purpose.
 */
final class HostedBrief {

    static final String HEADER = "## DogsBay XML editor\n";

    private HostedBrief() {
    }

    /**
     * The user's own words from a prompt that may start with the brief: a
     * replayed first turn comes back from the agent with the brief in front,
     * and the transcript shows what the user typed, as it did live.
     */
    static String strip(String prompt) {
        if (prompt == null || !prompt.startsWith(HEADER)) {
            return prompt;
        }
        int end = prompt.indexOf("\n\n");
        return end < 0 ? "" : prompt.substring(end + 2);
    }

    /**
     * @param projectRoot the open project, or null
     * @param activeFile  the document active in the editor, or null
     * @param toolsInjected whether the editor's MCP server is available to the agent
     */
    static String text(Path projectRoot, Path activeFile, boolean toolsInjected) {
        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("You are running inside DogsBay XML, a DITA editor");
        if (projectRoot != null) {
            sb.append(", with the project at ").append(projectRoot);
        }
        sb.append(".\n");
        if (activeFile != null) {
            sb.append("- Active document: ").append(activeFile).append('\n');
        }
        if (toolsInjected) {
            sb.append("- The MCP server \"dogsbay-editor\" gives you the editor's own tools. Prefer them to a shell: ")
              .append("they see unsaved buffers and are attributed and audited to your session.\n")
              .append("- ").append(com.dogsbay.agent.AgentGuidance.CHECKS).append('\n')
              // The brief rides in front of every first turn, so the structure and report
              // guidance is the short form; the server's instructions carry the full text.
              .append("- For project structure call project_graph; for a standalone HTML page, render_report.\n")
              .append("- Refactorings (rename_key, rename_file, retarget, keyify, …) are dry runs unless apply is set; ")
              .append("show the plan first.\n")
              .append("- Edits you make with set_document_content or replace_selection to a DITA document land as ")
              .append("review proposals: inserted text and elements carry status=\"new\" and deleted ones ")
              .append("status=\"deleted\", with your id in rev. Do not remove or rewrite those marks (yours or ")
              .append("anyone's), and do not treat them as errors; the writer accepts or rejects them. Prefer these two ")
              .append("commands for content edits; author_set_text and the refactorings apply directly. ")
              .append("review_list shows the open proposals; review_comment leaves a recommendation without ")
              .append("changing text. You cannot accept or reject proposals; only the writer can.\n")
              .append("- Before editing a document with set_document_content or author_set_text, read it in the same ")
              .append("session (get_document_content or author_outline); the editor refuses edits to a document ")
              .append("you have not read, or that changed since you read it, and returns the current content.\n");
        } else {
            sb.append("- The editor's tools are not available in this session (its integration server was off ")
              .append("when the session started). They cannot appear later: you were given your tools at ")
              .append("session start and there is no way to add more. If the user turns the integration server ")
              .append("on, say plainly that this session cannot use it and that a new session is needed — do ")
              .append("not offer to verify, reconnect or refresh, and do not retry.\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
