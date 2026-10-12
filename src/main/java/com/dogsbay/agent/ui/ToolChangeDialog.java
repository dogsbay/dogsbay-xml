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

package com.dogsbay.agent.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.dogsbay.agent.ToolChangePreview;

/**
 * Modal before/after preview of a mutating tool call the agent wants to run, so
 * the user approves the actual change rather than a blind yes/no. Read-only —
 * this is approval, not editing. Standalone-clean (no editor dependencies).
 *
 * <p>Returns the chosen option index, matching
 * {@code SwingPermissionHandler.mapChoice}: 0 Apply, 1 Allow for session,
 * 2 Always allow, 3 / -1 (closed) Deny.
 */
public final class ToolChangeDialog {

    private ToolChangeDialog() {}

    /** @return the option index (see class doc); -1 if the dialog was closed. */
    public static int confirm(Window parent, ToolChangePreview preview) {
        JPanel grid = new JPanel(new GridLayout(1, 2, 8, 0));
        grid.add(titled("Before", preview.before()));
        grid.add(titled("After (proposed)", preview.after()));
        grid.setPreferredSize(new Dimension(820, 420));

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(new JLabel("The agent wants to change: " + preview.title()), BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);

        Object[] options = {"Apply", "Allow for session", "Always allow", "Deny"};
        return JOptionPane.showOptionDialog(
                parent, content, "Tool permission — " + preview.title(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
    }

    private static JComponent titled(String title, String text) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }
}
