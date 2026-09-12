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

package com.dogsbay.agent.app;

import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

import com.dogsbay.agent.AgentContext;
import com.dogsbay.agent.AgentHost;
import com.xagent.tool.AgentTool;

/**
 * {@link AgentHost} backed by the filesystem: the agent works against a working
 * directory with only xagent's built-in tools (no editor tools). Used by
 * {@link AgentApp} as the standalone / dev-test harness.
 */
public final class StandaloneAgentHost implements AgentHost {

    private final Path workingDirectory;
    private volatile Window dialogParent;

    public StandaloneAgentHost(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    void setDialogParent(Window dialogParent) {
        this.dialogParent = dialogParent;
    }

    @Override
    public Path workingDirectory() {
        return workingDirectory;
    }

    @Override
    public AgentContext currentContext() {
        return AgentContext.empty();
    }

    @Override
    public List<AgentTool> editorTools() {
        return List.of();
    }

    @Override
    public Window dialogParent() {
        return dialogParent;
    }
}
