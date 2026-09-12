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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.acp.AgentRegistryCatalog;
import com.dogsbay.agent.acp.Tiers;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * The agent sidebar: one tab per session. The first tab is the built-in
 * chat; the {@code +} button lists the registry agents installed on this
 * machine and opens a hosted session for the one chosen. Closing a tab
 * ends that session.
 */
public final class AgentSidebarPanel extends JPanel {

    private final DogsBayAIEditor editor;
    private final AgentRegistryCatalog catalog;
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<HostedAgentSession> hosted = new ArrayList<>();
    /** Per tab: how to redraw its header (title, badge, unread dot). */
    private final java.util.Map<HostedAgentSession, Runnable> headers = new java.util.HashMap<>();
    private final java.util.Set<HostedAgentSession> unread = new java.util.HashSet<>();

    public AgentSidebarPanel(DogsBayAIEditor editor, AgentRegistryCatalog catalog, JComponent builtIn) {
        super(new BorderLayout());
        this.editor = editor;
        this.catalog = catalog;
        tabs.addChangeListener(e -> markRead(selectedHosted()));

        JButton add = new JButton("+");
        add.setToolTipText("Start another agent");
        add.addActionListener(e -> showAgentMenu(add));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        bar.add(add);
        add(bar, BorderLayout.NORTH);

        tabs.addTab("AI Agent", builtIn);
        if (builtIn instanceof com.dogsbay.agent.ui.AgentChatPanel chat) {
            tabs.setTabComponentAt(0, builtInHeader(chat));
        }
        add(tabs, BorderLayout.CENTER);
    }

    private boolean builtInUnread;

    /** The built-in agent's tab: a dot when a turn ended out of view, export and copy on right-click. */
    private JComponent builtInHeader(com.dogsbay.agent.ui.AgentChatPanel chat) {
        JLabel label = new JLabel(chat.sessionTitle());
        Runnable redraw = () -> {
            label.setText((builtInUnread ? "● " : "") + chat.sessionTitle());
            label.setToolTipText(builtInUnread ? chat.sessionTitle() + " finished a turn"
                    : "Double-click to rename; right-click for export and more");
        };
        redraw.run();
        chat.onTitleChanged(() -> SwingUtilities.invokeLater(redraw));
        chat.onTurnEnded(() -> {
            if (tabs.getSelectedComponent() != chat || !chat.isShowing()) {
                builtInUnread = true;
                redraw.run();
            }
        });
        Runnable read = () -> {
            if (builtInUnread) {
                builtInUnread = false;
                redraw.run();
            }
        };
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == chat) {
                read.run();
            }
        });
        chat.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && chat.isShowing()
                    && tabs.getSelectedComponent() == chat) {
                read.run();
            }
        });
        java.awt.event.MouseAdapter menu = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    chat.promptForSessionTitle(SwingUtilities.getWindowAncestor(AgentSidebarPanel.this));
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    tabs.setSelectedComponent(chat);
                }
            }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu m = new JPopupMenu();
                    JMenuItem rename = new JMenuItem("Rename session…");
                    rename.addActionListener(a -> chat.promptForSessionTitle(
                            SwingUtilities.getWindowAncestor(AgentSidebarPanel.this)));
                    m.add(rename);
                    JMenuItem export = new JMenuItem("Export transcript…");
                    export.addActionListener(a -> exportText(chat.sessionTitle(), builtInMarkdown(chat)));
                    m.add(export);
                    JMenuItem copy = new JMenuItem("Copy transcript");
                    copy.addActionListener(a -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(builtInMarkdown(chat)), null));
                    m.add(copy);
                    m.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        label.addMouseListener(menu);
        return label;
    }

    private String builtInMarkdown(com.dogsbay.agent.ui.AgentChatPanel chat) {
        java.nio.file.Path project = editor.projectRootForAgents();
        return com.dogsbay.agent.ui.TranscriptExport.markdown(chat.sessionTitle(), "AI Agent (built-in)", null,
                project == null ? null : project.toString(), java.time.Instant.now(), chat.transcriptText());
    }

    private void showAgentMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        List<AgentRegistryCatalog.Entry> ready = catalog.ready();
        List<AgentRegistryCatalog.Entry> downloadable = catalog.downloadable();
        if (ready.isEmpty() && downloadable.isEmpty()) {
            JMenuItem none = new JMenuItem("No ACP agents can run here (install an agent, or npx / uvx)");
            none.setEnabled(false);
            menu.add(none);
        }
        if (!ready.isEmpty()) {
            JMenuItem header = new JMenuItem("Installed on this machine");
            header.setEnabled(false);
            menu.add(header);
            java.nio.file.Path root = editor.projectRootForAgents();
            for (AgentRegistryCatalog.Entry entry : ready) {
                JMenuItem item = new JMenuItem(entry.name());
                item.setToolTipText(entry.description());
                item.addActionListener(e -> open(entry));
                menu.add(item);
                List<HostedSessionStore.Saved> recent = HostedSessionStore.recent(root, entry.id());
                for (HostedSessionStore.Saved saved : recent.subList(0, Math.min(5, recent.size()))) {
                    String what = saved.title() != null && !saved.title().isBlank()
                            ? "\"" + saved.title() + "\"" : entry.name();
                    JMenuItem resume = new JMenuItem("    Resume " + what + " (" + ago(saved.at()) + ")");
                    resume.setToolTipText("Pick up that conversation with " + entry.name()
                            + " in this project; the agent replays its history");
                    resume.addActionListener(e -> open(entry, saved.sessionId()));
                    menu.add(resume);
                }
            }
        }
        if (!downloadable.isEmpty()) {
            javax.swing.JMenu more = new javax.swing.JMenu("More agents (downloaded on first use)");
            for (AgentRegistryCatalog.Entry entry : downloadable) {
                JMenuItem item = new JMenuItem(entry.name());
                item.setToolTipText((entry.description() == null ? "" : entry.description() + " — ")
                        + "first start downloads the adapter and asks you to sign in");
                item.addActionListener(e -> open(entry));
                more.add(item);
            }
            menu.add(more);
        }
        List<AgentRegistryCatalog.Entry> installable = catalog.installable();
        if (!installable.isEmpty()) {
            javax.swing.JMenu install = new javax.swing.JMenu("Install an agent");
            for (AgentRegistryCatalog.Entry entry : installable) {
                JMenuItem item = new JMenuItem(entry.name());
                item.setToolTipText((entry.description() == null ? "" : entry.description() + " — ")
                        + "downloads the tool into ~/.dogsbay/agents; nothing else on this machine changes");
                item.addActionListener(e -> install(entry));
                install.add(item);
            }
            menu.add(install);
        }
        menu.addSeparator();
        JMenuItem refresh = new JMenuItem("Refresh agent registry");
        refresh.addActionListener(e -> Thread.ofVirtual().start(catalog::refresh));
        menu.add(refresh);
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Download a binary agent into the editor's managed folder, then offer to start it. */
    private void install(AgentRegistryCatalog.Entry entry) {
        Window parent = SwingUtilities.getWindowAncestor(this);
        var b = (com.dogsbay.agent.acp.AgentRegistryCatalog.Distribution.Binary) entry.distribution();
        int ok = javax.swing.JOptionPane.showConfirmDialog(parent,
                "Download and install " + entry.name() + (entry.version() != null ? " " + entry.version() : "") + "?\n\n"
                        + "From: " + b.archiveUrl() + "\n"
                        + "Into: " + com.dogsbay.agent.acp.AgentInstaller.home().resolve(entry.id()) + "\n"
                        + (b.sha256() != null ? "The download is checked against the registry's checksum.\n" : "")
                        + "\nDelete that folder to uninstall.",
                "Install " + entry.name(), javax.swing.JOptionPane.OK_CANCEL_OPTION);
        if (ok != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        javax.swing.JDialog progress = new javax.swing.JDialog(parent, "Installing " + entry.name(),
                java.awt.Dialog.ModalityType.MODELESS);
        JLabel line = new JLabel("Starting…");
        line.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 24, 16, 24));
        progress.add(line);
        progress.pack();
        progress.setSize(Math.max(progress.getWidth(), 420), progress.getHeight());
        progress.setLocationRelativeTo(parent);
        progress.setVisible(true);
        Thread.ofVirtual().name("install-" + entry.id()).start(() -> {
            try {
                new com.dogsbay.agent.acp.AgentInstaller().install(entry,
                        msg -> SwingUtilities.invokeLater(() -> line.setText(msg)));
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    int start = javax.swing.JOptionPane.showConfirmDialog(parent,
                            entry.name() + " is installed. Start it now?", "Installed",
                            javax.swing.JOptionPane.YES_NO_OPTION);
                    if (start == javax.swing.JOptionPane.YES_OPTION) {
                        open(entry);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    progress.dispose();
                    javax.swing.JOptionPane.showMessageDialog(parent,
                            "Could not install " + entry.name() + ":\n" + e.getMessage(), "Install failed",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /** "3 minutes ago", "2 hours ago", "yesterday", "4 days ago". */
    static String ago(java.time.Instant at) {
        long minutes = java.time.Duration.between(at, java.time.Instant.now()).toMinutes();
        if (minutes < 2) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " minutes ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    /** Open a hosted session for {@code entry} after asking for its tier. */
    public void open(AgentRegistryCatalog.Entry entry) {
        open(entry, null);
    }

    /** As {@link #open(AgentRegistryCatalog.Entry)}, resuming {@code resumeSessionId} when not null. */
    public void open(AgentRegistryCatalog.Entry entry, String resumeSessionId) {
        Window parent = SwingUtilities.getWindowAncestor(this);
        boolean savedKey = com.dogsbay.agent.acp.SavedKeys
                .savedKey(com.dogsbay.agent.secret.SecretStore.keychain(), entry.id()).isPresent();
        HostedAgentSession.StartChoice choice = HostedAgentSession.chooseStart(parent, entry,
                editor.getProperties().getAcpDefaultTier(), savedKey);
        if (choice == null) {
            return;
        }
        CapabilityTier tier = choice.tier();
        HostedAgentSession[] holder = new HostedAgentSession[1];
        HostedAgentSession s = new HostedAgentSession(editor, entry, tier, choice.passSavedKey(), () -> parent,
                () -> SwingUtilities.invokeLater(() -> removeTab(holder[0])));
        holder[0] = s;
        if (resumeSessionId != null) {
            s.resume(resumeSessionId);
        }
        hosted.add(s);
        tabs.addTab(entry.name(), s.panel());
        int index = tabs.indexOfComponent(s.panel());
        tabs.setTabComponentAt(index, tabHeader(s, Tiers.badge(tier)));
        tabs.setSelectedIndex(index);
        s.start();
    }

    /** Tell every hosted session that the integration server's state changed. */
    public void integrationServerChanged() {
        for (HostedAgentSession s : new ArrayList<>(hosted)) {
            s.integrationServerChanged();
        }
    }

    private JComponent tabHeader(HostedAgentSession s, String badge) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        JLabel label = new JLabel();
        header.add(label);
        Runnable redraw = () -> {
            boolean dot = unread.contains(s);
            label.setText((dot ? "● " : "") + s.title() + " · " + badge);
            label.setToolTipText(dot ? s.title() + " finished a turn"
                    : "Double-click to rename; right-click for export and more");
        };
        headers.put(s, redraw);
        redraw.run();
        s.onTitleChanged(redraw);
        // A turn that ends while the tab is out of view leaves a dot until the tab is looked at.
        s.onTurnEnded(() -> {
            if (tabs.getSelectedComponent() != s.panel() || !s.panel().isShowing()) {
                unread.add(s);
                redraw.run();
            }
        });
        s.panel().addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && s.panel().isShowing()) {
                markRead(s);   // the sidebar came back with this tab in front
            }
        });
        JButton close = new JButton("×");
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setMargin(new java.awt.Insets(0, 2, 0, 2));
        close.setToolTipText("End this agent session");
        close.addActionListener(e -> s.close());
        header.add(close);
        java.awt.event.MouseAdapter menu = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    rename(s);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    int i = tabs.indexOfComponent(s.panel());
                    if (i >= 0) {
                        tabs.setSelectedIndex(i);
                    }
                }
            }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    tabMenu(s).show(e.getComponent(), e.getX(), e.getY());
                }
            }
        };
        header.addMouseListener(menu);
        label.addMouseListener(menu);
        return header;
    }

    private JPopupMenu tabMenu(HostedAgentSession s) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename session…");
        rename.addActionListener(e -> rename(s));
        menu.add(rename);
        JMenuItem export = new JMenuItem("Export transcript…");
        export.addActionListener(e -> export(s));
        menu.add(export);
        JMenuItem copy = new JMenuItem("Copy transcript");
        copy.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(s.exportMarkdown()), null));
        menu.add(copy);
        menu.addSeparator();
        JMenuItem close = new JMenuItem("Close session");
        close.addActionListener(e -> s.close());
        menu.add(close);
        return menu;
    }

    private void rename(HostedAgentSession s) {
        Window parent = SwingUtilities.getWindowAncestor(this);
        String name = (String) javax.swing.JOptionPane.showInputDialog(parent,
                "Name for this session (blank restores the agent's name):", "Rename session",
                javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, s.title());
        if (name != null) {
            s.rename(name);
        }
    }

    private void export(HostedAgentSession s) {
        exportText(s.title(), s.exportMarkdown());
    }

    private void exportText(String title, String markdown) {
        Window parent = SwingUtilities.getWindowAncestor(this);
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Export transcript");
        java.nio.file.Path root = editor.projectRootForAgents();
        if (root != null) {
            chooser.setCurrentDirectory(root.toFile());
        }
        chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(),
                com.dogsbay.agent.ui.TranscriptExport.fileName(title, java.time.Instant.now())));
        if (chooser.showSaveDialog(parent) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            java.nio.file.Files.writeString(chooser.getSelectedFile().toPath(), markdown,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(parent, "Could not write the file: " + ex.getMessage(),
                    "Export transcript", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeTab(HostedAgentSession s) {
        int index = tabs.indexOfComponent(s.panel());
        if (index >= 0) {
            tabs.removeTabAt(index);
        }
        hosted.remove(s);
        headers.remove(s);
        unread.remove(s);
    }

    /** Send {@code text} as a prompt to whichever agent tab is in front: a hosted one or the built-in. */
    public void sendToCurrentAgent(String text) {
        HostedAgentSession s = selectedHosted();
        if (s != null) {
            s.panel().submitText(text);
            return;
        }
        if (tabs.getSelectedComponent() instanceof com.dogsbay.agent.ui.AgentChatPanel chat) {
            chat.submitText(text);
        } else if (tabs.getTabCount() > 0 && tabs.getComponentAt(0) instanceof com.dogsbay.agent.ui.AgentChatPanel chat) {
            tabs.setSelectedIndex(0);
            chat.submitText(text);
        }
    }

    private HostedAgentSession selectedHosted() {
        Component c = tabs.getSelectedComponent();
        for (HostedAgentSession s : hosted) {
            if (s.panel() == c) {
                return s;
            }
        }
        return null;
    }

    private void markRead(HostedAgentSession s) {
        if (s != null && unread.remove(s)) {
            Runnable redraw = headers.get(s);
            if (redraw != null) {
                redraw.run();
            }
        }
    }

    public int hostedCount() {
        return hosted.size();
    }

    /** Close every hosted session (plugin deactivation, editor exit). */
    public void closeAll() {
        for (HostedAgentSession s : List.copyOf(hosted)) {
            s.close();
        }
    }
}
