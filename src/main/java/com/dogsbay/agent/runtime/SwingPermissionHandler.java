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
import java.util.function.Supplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.AgentHost;
import com.dogsbay.agent.ToolChangePreview;
import com.dogsbay.agent.ui.ToolChangeDialog;
import com.xagent.permission.PermissionDecision;
import com.xagent.permission.PermissionHandler;
import com.xagent.permission.PermissionRequest;

/**
 * Swing implementation of xagent's {@link PermissionHandler}: when a non
 * read-only tool needs approval, show a modal dialog and map the choice to a
 * {@link PermissionDecision}. Read-only tools never reach here (PermissionToolHooks
 * bypasses them), so this gates writes/edits/bash and editor mutations.
 *
 * <p>When the host can produce a {@link ToolChangePreview} for the call (e.g.
 * {@code replace_selection}), the prompt shows the actual before/after change
 * instead of a yes/no; otherwise it falls back to a textual summary.
 *
 * <p>{@code handle} is invoked off the EDT (on the agent's loop thread), so the
 * dialog is shown via {@link SwingUtilities#invokeAndWait}.
 */
public final class SwingPermissionHandler implements PermissionHandler {

    private final Supplier<Window> parent;
    private final AgentHost host;   // supplies change previews; may be null

    public SwingPermissionHandler(Supplier<Window> parent) {
        this(parent, null);
    }

    public SwingPermissionHandler(Supplier<Window> parent, AgentHost host) {
        this.parent = parent;
        this.host = host;
    }

    @Override
    public PermissionDecision handle(PermissionRequest request) {
        final PermissionDecision[] result = {PermissionDecision.DENY};
        Runnable ask = () -> result[0] = prompt(request);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                ask.run();
            } else {
                SwingUtilities.invokeAndWait(ask);
            }
        } catch (Exception e) {
            return PermissionDecision.DENY;   // headless / interrupted → safe default
        }
        return result[0];
    }

    private PermissionDecision prompt(PermissionRequest request) {
        Window owner = parent != null ? parent.get() : null;

        // Prefer showing the actual change (before/after) when the host can build
        // one for this tool; the option order matches mapChoice.
        ToolChangePreview preview = (host != null)
                ? host.previewToolChange(request.toolName(), request.arguments()) : null;
        if (preview != null) {
            return mapChoice(ToolChangeDialog.confirm(owner, preview));
        }

        Object[] options = {"Allow", "Allow for session", "Always allow", "Deny"};
        int choice = JOptionPane.showOptionDialog(
                owner,
                "The agent wants to run a tool that can make changes:\n\n  "
                        + request.summary() + "\n\nAllow it?",
                "Tool permission — " + request.toolName(),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        return mapChoice(choice);
    }

    /** Map a JOptionPane option index to a decision (closed dialog → DENY). */
    static PermissionDecision mapChoice(int choice) {
        return switch (choice) {
            case 0 -> PermissionDecision.ALLOW;
            case 1 -> PermissionDecision.ALLOW_SESSION;
            case 2 -> PermissionDecision.ALLOW_ALWAYS;
            default -> PermissionDecision.DENY;
        };
    }
}
