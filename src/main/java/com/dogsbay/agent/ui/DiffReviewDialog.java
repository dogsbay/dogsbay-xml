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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Modal review of an AI-suggested change: original (read-only) beside the
 * suggestion (editable, so the user can tweak before applying), with
 * Accept/Reject. Returns the text to apply, or {@code null} if rejected.
 * Standalone-clean — no editor dependencies.
 */
public final class DiffReviewDialog {

    private DiffReviewDialog() {}

    /** @return the (possibly edited) suggestion to apply, or null if rejected. */
    public static String review(Window parent, String title, String original, String suggested) {
        return review(parent, title, original, suggested, null);
    }

    /**
     * @param metaLine a small footer line identifying the model/cost (e.g.
     *                 "openai-codex · gpt-5.4 · 1,234 tokens · included"), or null.
     * @return the (possibly edited) suggestion to apply, or null if rejected.
     */
    public static String review(Window parent, String title, String original, String suggested,
            String metaLine) {
        JTextArea left = textArea(original, false);
        JTextArea right = textArea(suggested, true);

        JPanel grid = new JPanel(new GridLayout(1, 2, 8, 0));
        grid.add(titled("Original", left));
        grid.add(titled("Suggested (editable)", right));
        grid.setPreferredSize(new Dimension(820, 420));

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(grid, BorderLayout.CENTER);
        if (metaLine != null && !metaLine.isBlank()) {
            JLabel meta = new JLabel(metaLine);
            meta.setFont(meta.getFont().deriveFont(Font.PLAIN, meta.getFont().getSize2D() - 1f));
            meta.setForeground(java.awt.Color.GRAY);
            content.add(meta, BorderLayout.SOUTH);
        }

        Object[] options = {"Accept", "Reject"};
        int choice = JOptionPane.showOptionDialog(
                parent, content, "AI: " + title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        return choice == JOptionPane.OK_OPTION ? right.getText() : null;
    }

    private static JTextArea textArea(String text, boolean editable) {
        JTextArea area = new JTextArea(text);
        area.setEditable(editable);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private static JPanel titled(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(
                area.getBackground().darker(), 1));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
}
