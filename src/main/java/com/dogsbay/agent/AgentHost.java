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

package com.dogsbay.agent;

import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

import com.xagent.tool.AgentTool;

/**
 * The narrow contract the agent chat UI needs from its environment.
 *
 * <p>Standalone-clean seam (mirrors {@code com.dogsbay.xml.author.AuthorHost}):
 * the {@code com.dogsbay.agent} module depends only on {@code xagent-core} and
 * this SPI, never on {@code com.dogsbay.dogsbayaieditor}
 * ({@code AgentPackageDependencyTest} enforces it). Two implementations:
 * {@code EditorAgentHost} (live editor) and
 * {@link com.dogsbay.agent.app.StandaloneAgentHost} (filesystem / dev harness).
 *
 * <p>The methods map to the three data seams in
 * {@code plans/phase-5-xagent-plugin.md}: working directory (the folder),
 * {@link #currentContext()} (ambient push), and {@link #editorTools()} (live
 * document pull).
 */
public interface AgentHost {

    /** Working directory / project root the agent operates in (data seam 1). */
    Path workingDirectory();

    /** Ambient context injected per turn — active file, selection, … (seam 2). */
    AgentContext currentContext();

    /** Editor tools exposed to the agent; empty when standalone (seam 3). */
    List<AgentTool> editorTools();

    /** Parent window for modal dialogs (permission prompt, file chooser); may be null. */
    Window dialogParent();

    /**
     * A before/after preview for a mutating tool call so the permission prompt can
     * show the actual change instead of a yes/no, or {@code null} for tools with no
     * previewable diff (the prompt then falls back to a textual summary). Default:
     * no preview.
     *
     * @param toolName      the tool about to run
     * @param argumentsJson its raw JSON arguments
     */
    default ToolChangePreview previewToolChange(String toolName, String argumentsJson) {
        return null;
    }

    /**
     * Validate the document(s) a mutating tool just changed and return a concise
     * feedback summary the agent reads on the next iteration, so it can self-correct
     * a broken edit in the same turn — or {@code null} when the tool isn't a
     * validatable content edit. Default: no validation (standalone host).
     *
     * @param toolName the mutating tool that just succeeded
     */
    default String validateAfterMutation(String toolName) {
        return null;
    }

    /**
     * The active document's file, used to checkpoint content edits whose tool
     * arguments carry no path (e.g. {@code replace_selection}). Default: none.
     */
    default Path activeDocumentFile() {
        return null;
    }

    /**
     * Force the given files' open buffers to match disk, after a revert restored
     * them. Authoritative: the user asked to undo the turn, so a buffer holding
     * the reverted-away content is stale by definition and is overwritten without
     * prompting. Default: no-op (standalone host).
     *
     * @param files the files whose content was restored on disk
     */
    default void reloadFiles(List<Path> files) {
        // no-op
    }

    /**
     * Note that a turn changed files on disk, so open buffers can catch up.
     *
     * <p>Advisory, unlike {@link #reloadFiles}: the user did not ask for their
     * buffers to be replaced, so this must not discard unsaved work. Hosts are
     * expected to reload clean buffers and prompt (or defer) for dirty ones.
     *
     * @param files the files the turn touched; may include files it left unchanged
     */
    default void filesChangedOnDisk(List<Path> files) {
        // no-op
    }

    /**
     * The chat switched provider or model. Hosts that attribute the agent's
     * work (audit log, proposals) update the agent's identity here.
     */
    default void agentModelChanged(String provider, String model) {
        // no-op
    }

    /** A chat turn finished; the agent holds no documents until its next turn. */
    default void turnEnded() {
        // no-op
    }

    /** The chat is closing; release anything the host holds for it. */
    default void dispose() {
        // no-op
    }
}
