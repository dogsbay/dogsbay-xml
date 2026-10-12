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

import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

import com.dogsbay.agent.AgentContext;
import com.dogsbay.agent.AgentHost;
import com.xagent.tool.AgentTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointToolHooksTest {

    private static AgentHost host(Path workingDir, Path activeFile) {
        return new AgentHost() {
            public Path workingDirectory() { return workingDir; }
            public AgentContext currentContext() { return AgentContext.empty(); }
            public List<AgentTool> editorTools() { return List.of(); }
            public Window dialogParent() { return null; }
            public Path activeDocumentFile() { return activeFile; }
        };
    }

    private CheckpointToolHooks hooks(Path workingDir, Path activeFile) {
        return new CheckpointToolHooks(new TurnCheckpoint(workingDir.resolve(".cp")),
                host(workingDir, activeFile));
    }

    @Test
    @DisplayName("in-buffer content tools are NOT captured (they undo with the editor)")
    void contentToolNotCaptured() {
        Path active = Path.of("/proj/topic.dita");
        List<Path> files = hooks(Path.of("/proj"), active)
                .targetFiles("replace_selection", "{\"text\":\"<p>x</p>\"}");
        assertThat(files).isEmpty();   // no path argument → nothing to checkpoint on disk
    }

    @Test
    @DisplayName("path-bearing tools capture their file arguments, resolved against the project")
    void fileArgsResolvedAgainstWorkingDir() {
        List<Path> files = hooks(Path.of("/proj"), null)
                .targetFiles("rename_file", "{\"from\":\"a.dita\",\"to\":\"sub/b.dita\"}");
        assertThat(files).containsExactly(
                Path.of("/proj/a.dita"), Path.of("/proj/sub/b.dita"));
    }

    @Test
    @DisplayName("absolute path arguments are kept as-is")
    void absolutePathsKept() {
        List<Path> files = hooks(Path.of("/proj"), null)
                .targetFiles("write", "{\"path\":\"/etc/other.xml\"}");
        assertThat(files).containsExactly(Path.of("/etc/other.xml"));
    }

    @Test
    @DisplayName("a tool with no file arguments and no active doc captures nothing")
    void noFiles() {
        assertThat(hooks(Path.of("/proj"), null).targetFiles("grep", "{\"pattern\":\"x\"}"))
                .isEmpty();
    }
}
