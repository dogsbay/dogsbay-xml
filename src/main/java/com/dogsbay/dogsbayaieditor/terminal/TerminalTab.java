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

package com.dogsbay.dogsbayaieditor.terminal;

import com.jediterm.terminal.ui.JediTermWidget;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Individual terminal tab containing a JediTerm terminal widget.
 * Simplified implementation for JediTerm 3.48 API.
 */
public class TerminalTab extends JPanel {
    private JediTermWidget terminalWidget;
    private PtyProcess process;
    private String name;
    private File workingDirectory;

    /**
     * Create a new terminal tab.
     * 
     * @param name             Terminal name for display
     * @param workingDirectory Initial working directory
     */
    private TerminalContainerPanel parent;

    /**
     * Create a new terminal tab.
     * 
     * @param parent           Parent container
     * @param name             Terminal name for display
     * @param workingDirectory Initial working directory
     */
    public TerminalTab(TerminalContainerPanel parent, String name, File workingDirectory) {
        this.parent = parent;
        this.name = name;
        this.workingDirectory = workingDirectory != null ? workingDirectory : getDefaultWorkingDirectory();

        setLayout(new BorderLayout());
        initializeTerminal();
    }

    /**
     * Initialize the terminal widget and start the shell process.
     */
    private void initializeTerminal() {
        try {
            // Create terminal widget with settings
            TerminalSettings settings = new TerminalSettings();
            terminalWidget = new JediTermWidget(settings);
            terminalWidget.setNextProvider(new SendSelectionProvider());
            add(terminalWidget, BorderLayout.CENTER);

            // Start shell process
            startShell();
        } catch (Exception e) {
            showError("Failed to initialize terminal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** The terminal's selected text, or null when nothing is selected. */
    public String getSelectedText() {
        if (terminalWidget == null || terminalWidget.getTerminalPanel() == null) {
            return null;
        }
        com.jediterm.terminal.model.TerminalSelection sel = terminalWidget.getTerminalPanel().getSelection();
        if (sel == null) {
            return null;
        }
        String text = com.jediterm.terminal.model.SelectionUtil.getSelectionText(sel,
                terminalWidget.getTerminalPanel().getTerminalTextBuffer());
        return text == null || text.isBlank() ? null : text;
    }

    /** Adds "Send selection to AI Agent" to the terminal's right-click menu while an agent is available. */
    private final class SendSelectionProvider extends com.jediterm.terminal.ui.TerminalActionProviderBase {
        @Override
        public java.util.List<com.jediterm.terminal.ui.TerminalAction> getActions() {
            com.jediterm.terminal.ui.TerminalAction send = new com.jediterm.terminal.ui.TerminalAction(
                    new com.jediterm.terminal.ui.TerminalActionPresentation(
                            com.dogsbay.dogsbayaieditor.SelectionToAgent.LABEL, java.util.List.of()),
                    e -> com.dogsbay.dogsbayaieditor.SelectionToAgent.send(getSelectedText()))
                    .withEnabledSupplier(() -> com.dogsbay.dogsbayaieditor.SelectionToAgent.available()
                            && getSelectedText() != null);
            return java.util.List.of(send);
        }
    }

    /**
     * Start the shell process with PTY.
     */
    private void startShell() {
        try {
            String[] command = getShellCommand();
            Map<String, String> environment = buildEnvironment();

            // Create PTY process using pty4j
            process = new PtyProcessBuilder()
                    .setCommand(command)
                    .setDirectory(workingDirectory.getAbsolutePath())
                    .setEnvironment(environment)
                    .setInitialColumns(80)
                    .setInitialRows(24)
                    .start();

            // Create TtyConnector and connect to terminal
            // Use our custom implementation
            PtyProcessTtyConnector connector = new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);

            // Start terminal session
            terminalWidget.createTerminalSession(connector);
            terminalWidget.start();

        } catch (IOException e) {
            showError("Failed to start shell: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get the shell command appropriate for the current OS.
     */
    private String[] getShellCommand() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: use cmd.exe
            return new String[] { "cmd.exe" };
        } else if (os.contains("mac")) {
            // macOS: use user's default shell or zsh
            String shell = System.getenv("SHELL");
            if (shell != null && !shell.isEmpty()) {
                return new String[] { shell, "-i", "-l" };
            }
            return new String[] { "/bin/zsh", "-i", "-l" };
        } else {
            // Linux/Unix: use user's default shell or bash
            String shell = System.getenv("SHELL");
            if (shell != null && !shell.isEmpty()) {
                return new String[] { shell, "-i" };
            }
            return new String[] { "/bin/bash", "-i" };
        }
    }

    /**
     * Build environment variables for the shell process.
     */
    private Map<String, String> buildEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());

        // Set TERM to enable colors and proper terminal emulation
        env.put("TERM", "xterm-256color");

        return env;
    }

    /**
     * Get default working directory (user home).
     */
    private File getDefaultWorkingDirectory() {
        return new File(System.getProperty("user.home"));
    }

    /**
     * Show an error message in the terminal panel.
     */
    private void showError(String message) {
        removeAll();
        JTextArea errorArea = new JTextArea(message);
        errorArea.setEditable(false);
        errorArea.setBackground(new Color(60, 30, 30));
        errorArea.setForeground(new Color(255, 100, 100));
        errorArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(errorArea), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Clear the terminal buffer.
     */
    public void clear() {
        if (terminalWidget != null && terminalWidget.getTerminalPanel() != null) {
            try {
                terminalWidget.getTerminalPanel().clearBuffer();
            } catch (Exception e) {
                // Ignore if clear fails
                e.printStackTrace();
            }
        }
    }

    /**
     * Kill the running process forcefully.
     */
    public void killProcess() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * Check if the shell process is still running.
     */
    public boolean isProcessRunning() {
        return process != null && process.isAlive();
    }

    /**
     * Clean up resources when closing the terminal.
     */
    public void dispose() {
        if (terminalWidget != null) {
            try {
                terminalWidget.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * Get the terminal name.
     */
    public String getTerminalName() {
        return name;
    }

    /**
     * Set the terminal name.
     */
    public void setTerminalName(String name) {
        this.name = name;
    }

    /**
     * Get the working directory.
     */
    public File getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Request focus on the terminal.
     */
    public void focusTerminal() {
        if (terminalWidget != null) {
            terminalWidget.requestFocusInWindow();
        }
    }
}
