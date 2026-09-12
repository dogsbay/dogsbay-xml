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

package com.dogsbay.dogsbayaieditor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Welcome / getting-started tab shown on first launch. Actionable, not prose:
 * open the bundled sample, open a folder, and — the key onboarding step — turn on
 * the AI integration (MCP) server with the connect command shown inline.
 */
public class WelcomePanel extends JPanel {

    /** Tab title (also used to find/replace an existing Welcome tab). */
    public static final String TAB_NAME = "Welcome";

    private final transient DogsBayAIEditor editor;

    public WelcomePanel(DogsBayAIEditor editor) {
        super(new GridBagLayout());
        this.editor = editor;

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("Welcome to DogsBay XML");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(title);

        JLabel sub = new JLabel("An AI-assisted XML & DITA editor.");
        sub.setForeground(UIManager.getColor("Label.disabledForeground"));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setBorder(new EmptyBorder(2, 0, 18, 0));
        col.add(sub);

        col.add(section("Get started"));
        col.add(button("Open Sample Project",
                "Copy the bundled Audacity DITA sample and open it",
                e -> editor.getOpenSampleProjectAction().actionPerformed(null)));
        col.add(Box.createVerticalStrut(6));
        col.add(button("Open Folder…", "Open an existing project folder",
                e -> editor.getOpenFolderAction().actionPerformed(null)));

        col.add(section("Turn on AI"));
        JLabel aiHint = hint("Enable the integration server so Claude Code / MCP clients "
                + "can drive the editor.");
        col.add(aiHint);
        col.add(button("Enable AI integration server", "Start the MCP/REST server and show the connect command",
                e -> enableMcpServer()));

        col.add(section("Learn"));
        col.add(hint("Docs: command-line tools (docs/command-line-tools.md), "
                + "MCP integration (docs/mcp-integration.md), DITA authoring & publishing."));

        col.add(Box.createVerticalStrut(20));
        JCheckBox showOnStartup = new JCheckBox("Show welcome on startup",
                editor.getProperties().isShowWelcomeOnStartup());
        showOnStartup.setAlignmentX(Component.LEFT_ALIGNMENT);
        showOnStartup.addActionListener(e ->
                editor.getProperties().setShowWelcomeOnStartup(showOnStartup.isSelected()));
        col.add(showOnStartup);

        add(col);
    }

    private JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(18, 0, 6, 0));
        return l;
    }

    private JLabel hint(String text) {
        JLabel l = new JLabel("<html><body style='width:380px'>" + text + "</body></html>");
        l.setForeground(UIManager.getColor("Label.disabledForeground"));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JButton button(String text, String tooltip, java.awt.event.ActionListener onClick) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(280, b.getPreferredSize().height));
        b.addActionListener(onClick);
        return b;
    }

    /** Flip the pref, (re)start the server, and show the connect command. */
    private void enableMcpServer() {
        var mgr = editor.getIpcServerManager();
        try {
            editor.getProperties().setMcpServerEnabled(true);
            if (mgr != null) {
                mgr.stop();
                mgr.start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(editor,
                    "Could not start the integration server:\n" + ex.getMessage(),
                    "Enable AI", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // start() is best-effort and swallows bind failures, so confirm the server
        // is actually listening (getPort() is -1 when stopped) before claiming success.
        int port = mgr != null ? mgr.getPort() : -1;
        if (port <= 0) {
            JOptionPane.showMessageDialog(editor,
                    "Could not start the integration server — the port may be in use.\n"
                            + "You can retry from Settings → Server.",
                    "Enable AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String token = mgr.getAuthToken();
        String message = "AI integration server enabled:\n  http://localhost:" + port + "/mcp\n\n"
                + "Connect Claude Code with:\n"
                + "claude mcp add --transport http dogsbay-editor http://localhost:" + port
                + "/mcp --header \"Authorization: Bearer " + (token != null ? token : "<token>") + "\"";

        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(560, 160));
        JOptionPane.showMessageDialog(editor, sp, "AI integration server", JOptionPane.INFORMATION_MESSAGE);
    }
}
