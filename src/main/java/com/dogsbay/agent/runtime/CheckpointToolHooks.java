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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.dogsbay.agent.AgentHost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xagent.core.ToolHookResult;
import com.xagent.core.ToolHooks;

/**
 * Captures the files a mutating tool is about to change into the
 * {@link TurnCheckpoint}, before the tool runs, so the turn can be reverted
 * (plans/agent-turn-checkpoint-undo.md). Capture happens in {@code beforeToolCall}
 * (where the arguments — and thus the target paths — are available) and never
 * blocks or modifies the call.
 */
public final class CheckpointToolHooks implements ToolHooks {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Argument fields that name a file a tool will create or change. */
    private static final String[] FILE_FIELDS = {"file", "file_path", "path", "from", "to", "target"};

    private final TurnCheckpoint checkpoint;
    private final AgentHost host;

    public CheckpointToolHooks(TurnCheckpoint checkpoint, AgentHost host) {
        this.checkpoint = checkpoint;
        this.host = host;
    }

    @Override
    public ToolHookResult beforeToolCall(String toolName, String arguments) {
        try {
            for (Path file : targetFiles(toolName, arguments)) {
                checkpoint.capture(file);
            }
        } catch (Exception ignore) {
            // capture is best-effort; never let it interfere with the tool call
        }
        return ToolHookResult.ALLOW;
    }

    /**
     * The files a tool call will create or change on disk, from its path-like
     * arguments. In-buffer edits to the active document (e.g. replace_selection,
     * which carries no path) are deliberately NOT captured here — those undo with
     * the editor's normal undo; the checkpoint exists for filesystem changes that
     * aren't on the undo stack (new files, renames, multi-file refactors).
     */
    List<Path> targetFiles(String toolName, String argumentsJson) {
        List<Path> files = new ArrayList<>();
        Path workingDir = safeWorkingDir();
        try {
            JsonNode args = MAPPER.readTree(argumentsJson);
            for (String field : FILE_FIELDS) {
                JsonNode value = args.path(field);
                if (value.isTextual() && !value.asText().isBlank()) {
                    Path p = Path.of(value.asText());
                    files.add(p.isAbsolute() || workingDir == null ? p : workingDir.resolve(p));
                }
            }
        } catch (Exception ignore) {
            // unparseable arguments → only the active doc (if any) is captured
        }
        return files;
    }

    private Path safeWorkingDir() {
        try {
            return host.workingDirectory();
        } catch (Exception e) {
            return null;
        }
    }
}
