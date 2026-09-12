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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.terminal.actions.*;

import javax.swing.*;
import java.awt.*;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.io.File;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Container panel for managing multiple terminal tabs.
 * Provides toolbar with terminal management actions and nested tabs for
 * terminal instances.
 */
public class TerminalContainerPanel extends JPanel {
    private JTabbedPane terminalTabs;
    private List<TerminalTab> terminals;
    private DogsBayAIEditor parent;
    private JToolBar toolbar;
    private int terminalCounter = 0;

    private static final ImageIcon TERMINAL_ICON = DogsBayImageLoader.get()
            .getImage("com/dogsbay/dogsbayaieditor/icons/JSConsole16.gif"); // Fallback until Terminal icon created

    /**
     * Create a new terminal container panel.
     * 
     * @param parent The parent DogsBayAIEditor instance
     */
    public TerminalContainerPanel(DogsBayAIEditor parent) {
        this.parent = parent;
        this.terminals = new ArrayList<>();

        setLayout(new BorderLayout());

        // Create toolbar
        toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        // Create tabbed pane for terminals
        terminalTabs = new JTabbedPane(JTabbedPane.BOTTOM);
        terminalTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(terminalTabs, BorderLayout.CENTER);

        // Add tab close support
        addTabCloseSupport();

        // The first terminal is created when the panel is first shown, not at
        // startup: by then the project has been opened, so the shell starts in
        // the project folder instead of the user's home.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing() && terminals.isEmpty()) {
                createNewTerminal();
            }
        });
    }

    /**
     * Create toolbar with terminal actions.
     */
    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        // Add actions
        toolbar.add(new NewTerminalAction(this));
        toolbar.add(new CloseTerminalAction(this));
        toolbar.addSeparator();
        toolbar.add(new ClearTerminalAction(this));
        toolbar.add(new KillProcessAction(this));

        return toolbar;
    }

    /**
     * Create a new terminal tab.
     */
    public void createNewTerminal() {
        SwingUtilities.invokeLater(() -> {
            terminalCounter++;
            String name = "Terminal " + terminalCounter;
            File workingDir = getWorkingDirectory();

            TerminalTab terminal = new TerminalTab(this, name, workingDir);
            terminals.add(terminal);

            int index = terminalTabs.getTabCount();
            terminalTabs.addTab(name, getTerminalIcon(), terminal);
            terminalTabs.setTabComponentAt(index, createTabComponent(name, index));
            terminalTabs.setSelectedIndex(index);

            // Focus the terminal
            terminal.focusTerminal();
        });
    }

    /**
     * The initial working directory for a new terminal: the current project's
     * folder, else the folder open in the File Explorer, else the current
     * document's folder, else the user's home.
     */
    private File getWorkingDirectory() {
        File project = null;
        File explorerRoot = null;
        File documentDir = null;
        try {
            if (parent.getProjectSwitcher() != null) {
                ProjectProperties p = parent.getProjectSwitcher().getCurrentProject();
                if (p != null && p.getFolderPath() != null && !p.getFolderPath().isEmpty()) {
                    project = new File(p.getFolderPath());
                }
            }
            if (parent.getFileExplorer() != null) {
                explorerRoot = parent.getFileExplorer().getRootDirectory();
            }
            DogsBayDocument doc = parent.getDocument();
            URL url = doc != null ? doc.getURL() : null;
            File file = url != null && "file".equals(url.getProtocol()) ? new File(url.toURI()) : null;
            documentDir = file != null ? file.getParentFile() : null;
        } catch (Exception e) {
            // fall through to whatever was found
        }
        return chooseWorkingDirectory(project, explorerRoot, documentDir);
    }

    /** First existing directory of the candidates, in order; the user's home when none is. */
    static File chooseWorkingDirectory(File project, File explorerRoot, File documentDir) {
        for (File f : new File[] {project, explorerRoot, documentDir}) {
            if (f != null && f.isDirectory()) {
                return f;
            }
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * Create a custom tab component with close button.
     */
    private JPanel createTabComponent(String title, int index) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);

        // Tab label
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        panel.add(label);

        // Close button
        JButton closeButton = new JButton("×");
        closeButton.setPreferredSize(new Dimension(17, 17));
        closeButton.setToolTipText("Close terminal");
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusable(false);
        closeButton.addActionListener(e -> closeTerminal(index));
        panel.add(closeButton);

        return panel;
    }

    /**
     * Get the terminal icon (or null if not available).
     */
    private ImageIcon getTerminalIcon() {
        try {
            return TERMINAL_ICON;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the default working directory for new terminals.
     */
    private File getDefaultWorkingDirectory() {
        // Try to use the user's home directory
        return new File(System.getProperty("user.home"));
    }

    /**
     * Close the currently selected terminal.
     */
    public void closeCurrentTerminal() {
        int index = terminalTabs.getSelectedIndex();
        if (index >= 0) {
            closeTerminal(index);
        }
    }

    /**
     * Close a specific terminal tab.
     * 
     * @param index Index of the tab to close
     */
    public void closeTerminal(int index) {
        if (index < 0 || index >= terminalTabs.getTabCount()) {
            return;
        }

        TerminalTab terminal = (TerminalTab) terminalTabs.getComponentAt(index);

        // Confirm if process is running
        if (terminal.isProcessRunning()) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Process is still running. Terminate it?",
                    "Close Terminal",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Clean up and remove
        terminal.dispose();
        terminals.remove(terminal);
        terminalTabs.removeTabAt(index);

        // Create new terminal if all closed
        if (terminalTabs.getTabCount() == 0) {
            createNewTerminal();
        }
    }

    /**
     * Clear the current terminal's buffer.
     */
    public void clearCurrentTerminal() {
        int index = terminalTabs.getSelectedIndex();
        if (index >= 0) {
            TerminalTab terminal = (TerminalTab) terminalTabs.getComponentAt(index);
            terminal.clear();
        }
    }

    /**
     * Kill the process in the current terminal.
     */
    public void killCurrentProcess() {
        int index = terminalTabs.getSelectedIndex();
        if (index >= 0) {
            TerminalTab terminal = (TerminalTab) terminalTabs.getComponentAt(index);
            if (terminal.isProcessRunning()) {
                int result = JOptionPane.showConfirmDialog(
                        this,
                        "Kill the running process?",
                        "Kill Process",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    terminal.killProcess();
                }
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "No process is currently running.",
                        "Kill Process",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Add middle-click to close support to tabs.
     */
    private void addTabCloseSupport() {
        terminalTabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) { // Middle click
                    int index = terminalTabs.indexAtLocation(e.getX(), e.getY());
                    if (index >= 0) {
                        closeTerminal(index);
                    }
                }
            }
        });
    }

    /**
     * Get the currently selected terminal.
     */
    public TerminalTab getCurrentTerminal() {
        int index = terminalTabs.getSelectedIndex();
        if (index >= 0) {
            return (TerminalTab) terminalTabs.getComponentAt(index);
        }
        return null;
    }

    /**
     * Get all terminals.
     */
    public List<TerminalTab> getTerminals() {
        return new ArrayList<>(terminals);
    }

    /**
     * Dispose all terminals and clean up resources.
     */
    public void dispose() {
        for (TerminalTab terminal : terminals) {
            terminal.dispose();
        }
        terminals.clear();
    }

    /**
     * Get the terminal tabs component.
     */
    public JTabbedPane getTerminalTabs() {
        return terminalTabs;
    }
}
