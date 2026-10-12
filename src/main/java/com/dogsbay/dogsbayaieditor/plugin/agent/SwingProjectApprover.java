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

import java.awt.Window;
import java.util.function.Supplier;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.dogsbayaieditor.commands.WriteGate;

/**
 * Asks the writer, in a dialog kept in front of everything, whether an agent may move out of the open
 * project (see {@code WriteGate.boundary}). Deny is the default button; closing the dialog, an interrupted
 * wait or a headless editor also deny.
 */
public final class SwingProjectApprover implements WriteGate.Approver {

    private final Supplier<Window> parent;

    public SwingProjectApprover(Supplier<Window> parent) {
        this.parent = parent;
    }

    @Override
    public boolean approve(AgentSession session, String question) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return false;
        }
        boolean[] allowed = {false};
        Runnable ask = () -> allowed[0] = ask(session, question);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                ask.run();
            } else {
                SwingUtilities.invokeAndWait(ask);
            }
        } catch (Exception e) {
            return false;
        }
        return allowed[0];
    }

    private boolean ask(AgentSession session, String question) {
        String[] options = {"Allow", "Deny"};
        JOptionPane pane = new JOptionPane(question, JOptionPane.QUESTION_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                options, options[1]);
        JDialog dialog = pane.createDialog(parent != null ? parent.get() : null,
                "Change project? — " + session.displayName());
        // A question the writer cannot see is a hung agent: keep it in front, whatever the window stack
        dialog.setAlwaysOnTop(true);
        dialog.toFront();
        dialog.setVisible(true);
        dialog.dispose();
        return options[0].equals(pane.getValue());
    }
}
