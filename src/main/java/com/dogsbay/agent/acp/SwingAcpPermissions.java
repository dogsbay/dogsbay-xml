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
package com.dogsbay.agent.acp;

import java.awt.Window;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.acp.AcpWire.PermissionOption;
import com.dogsbay.agent.acp.AcpWire.PermissionRequest;

/**
 * Asks the user the agent's permission question with the agent's own
 * options as buttons. Runs on the EDT; a headless or interrupted prompt
 * cancels, which the agent treats as a refusal.
 */
public final class SwingAcpPermissions implements AcpClient.PermissionPrompter {

    private final Supplier<Window> parent;
    private final String agentName;

    public SwingAcpPermissions(Supplier<Window> parent, String agentName) {
        this.parent = parent;
        this.agentName = agentName;
    }

    @Override
    public String choose(PermissionRequest request) {
        final String[] result = {null};
        Runnable ask = () -> result[0] = prompt(request);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                ask.run();
            } else {
                SwingUtilities.invokeAndWait(ask);
            }
        } catch (Exception e) {
            return null;
        }
        return result[0];
    }

    private String prompt(PermissionRequest request) {
        List<PermissionOption> options = request.options();
        if (options.isEmpty()) {
            return null;
        }
        // Distinct objects per option even when two share a display name, so the
        // clicked button maps back by identity, never by equal text.
        Object[] labels = new Object[options.size()];
        for (int i = 0; i < labels.length; i++) {
            PermissionOption o = options.get(i);
            labels[i] = new String(o.name() != null ? o.name() : o.optionId());
        }
        String title = request.toolCall().title() != null ? request.toolCall().title() : "a tool";
        StringBuilder msg = new StringBuilder(agentName + " wants to run:\n\n  " + title);
        if (!request.toolCall().locations().isEmpty()) {
            msg.append("\n\n  ").append(String.join("\n  ", request.toolCall().locations()));
        }
        // A permission question the user cannot see is a hung agent: keep the
        // dialog on top and in front of everything, whatever the window stack.
        JOptionPane pane = new JOptionPane(msg.toString(), JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, labels, labels[0]);
        javax.swing.JDialog dialog = pane.createDialog(parent != null ? parent.get() : null,
                "Permission — " + agentName);
        dialog.setAlwaysOnTop(true);
        dialog.toFront();
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == value) {
                return options.get(i).optionId();
            }
        }
        return null;
    }
}
