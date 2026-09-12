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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.dogsbay.agent.ProviderChoice;
import com.dogsbay.agent.secret.AgentCredentials;
import com.xagent.event.AgentEvent;
import com.xagent.message.AssistantMessage;
import com.xagent.tool.AgentToolResult;

/**
 * Minimal Swing chat panel for the agent: a provider bar (pick provider / paste
 * key / sign in to ChatGPT), a streaming transcript, and an input row. It only
 * renders the {@link AgentEvent} stream and reports user actions via
 * {@link Actions}; it owns no agent and no threading, so it works identically
 * embedded in the editor sidebar or in the standalone {@code AgentApp}.
 *
 * <p>Responsive with no credentials (lazy provider startup): input stays
 * enabled, a hint line guides setup, and a missing-key error re-enables input.
 * Call render methods on the EDT.
 */
public final class AgentChatPanel extends JPanel {

    /** User actions the host wires to the agent. */
    public interface Actions {
        void submit(String text);
        void chooseProvider(ProviderChoice choice);
        void login();          // sign in to ChatGPT (Codex OAuth)
        void command(String raw);   // a /slash command
        void sessions();       // open the session picker (new / resume)
        void abort();          // stop the in-flight turn

        /** Name this conversation; blank restores the automatic name. */
        default void renameSession(String title) { }

        /** The {@code @path} mention for a dropped file, or null when it cannot be attached. */
        default String mention(java.io.File file) {
            return null;
        }

        /** What the agent has stored to authenticate with this provider. */
        default AgentCredentials.State credential(String provider) {
            return AgentCredentials.State.MISSING;
        }

        /** Every provider with a key or a sign-in stored. */
        default List<String> storedCredentials() {
            return List.of();
        }

        /** Forget the stored key or sign-in for one provider. */
        default void forgetCredential(String provider) { }

        /** Forget every stored credential. Sessions and transcripts are left alone. */
        default void forgetAllCredentials() { }
    }

    private final JTextPane transcript = new JTextPane();
    private final javax.swing.JTextArea input = new javax.swing.JTextArea(1, 20);
    private final JPopupMenu completion = new JPopupMenu();
    private List<HostedChatPanel.Command> shown = List.of();
    private int selected = -1;
    private static final int MAX_ROWS = 6;
    private final List<Runnable> turnListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Runnable> titleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private String sessionTitle = DEFAULT_TITLE;

    /** What the built-in agent's tab is called before a conversation names itself. */
    public static final String DEFAULT_TITLE = "AI Agent";

    /** The tab's name: the session's own, or {@link #DEFAULT_TITLE}. */
    public String sessionTitle() {
        return sessionTitle == null || sessionTitle.isBlank() ? DEFAULT_TITLE : sessionTitle;
    }

    /** Set the tab's name and tell whoever draws it. */
    public void setSessionTitle(String title) {
        String next = title == null || title.isBlank() ? DEFAULT_TITLE : title.strip();
        if (!next.equals(sessionTitle)) {
            sessionTitle = next;
            titleListeners.forEach(Runnable::run);
        }
    }

    public void onTitleChanged(Runnable listener) {
        titleListeners.add(listener);
    }

    /** Ask for a new name, as the hosted tabs do, and pass it to the controller. */
    public void promptForSessionTitle(java.awt.Component parent) {
        String name = (String) JOptionPane.showInputDialog(parent,
                "Name for this session (blank restores the automatic name):", "Rename session",
                JOptionPane.PLAIN_MESSAGE, null, null,
                DEFAULT_TITLE.equals(sessionTitle()) ? "" : sessionTitle());
        if (name != null) {
            actions.renameSession(name);
        }
    }

    /** The built-in agent's own slash commands, for completion; the controller handles them. */
    static final List<HostedChatPanel.Command> BUILTIN_COMMANDS = List.of(
            new HostedChatPanel.Command("help", "List the available commands"),
            new HostedChatPanel.Command("init", "Survey the project and generate an AGENTS.md context file"),
            new HostedChatPanel.Command("clear", "Clear the transcript and the agent's conversation history"),
            new HostedChatPanel.Command("provider", "Show providers, or switch to one: /provider ollama"),
            new HostedChatPanel.Command("model", "Show the current model, or switch: /model claude-opus-4-8"),
            new HostedChatPanel.Command("login", "Sign in to ChatGPT in the browser; /login device for a code"),
            new HostedChatPanel.Command("logout", "Forget the ChatGPT sign-in"),
            new HostedChatPanel.Command("tools", "List the tools the agent can call"),
            new HostedChatPanel.Command("skills", "List the loaded skills"),
            new HostedChatPanel.Command("compact", "Summarise the conversation so far to free context"),
            new HostedChatPanel.Command("revert", "Undo the file changes of the last turn"),
            new HostedChatPanel.Command("export", "Export the conversation to a Markdown file in the project"),
            new HostedChatPanel.Command("new", "Start a new session"),
            new HostedChatPanel.Command("sessions", "Open the session picker"),
            new HostedChatPanel.Command("rename", "Name this session: /rename Audacity cleanup"),
            new HostedChatPanel.Command("resume", "Resume a session: /resume <id>"));
    private final JButton send = new JButton("Send");
    private final JButton stop = new JButton("Stop");
    private final JComboBox<String> providerCombo;
    private final JPasswordField keyField = new JPasswordField(14);
    private final JCheckBox rememberKey = new JCheckBox("Remember");   // opt-in key persistence
    private final JButton signIn = new JButton("Sign in to ChatGPT");
    private final JLabel credentialLabel = new JLabel(" ");
    private final JButton forgetCredential = new JButton("Forget key");
    private final JButton forgetAll = new JButton("Forget all sign-ins…");
    private JPanel credentialSection;   // titled box whose contents follow the provider
    private JPanel keyRow;              // the key box, hidden for providers that sign in
    private final JLabel modelLabel = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final Actions actions;
    private JDialog settingsDialog;   // modal provider/key/sign-in settings (gear-opened)
    private boolean streamedText;   // did this assistant message stream any text yet?
    private int messageStart = -1;
    private int messageEnd = -1;
    private int headerStart = -1;   // where "\nAgent: " begins, to drop it when nothing follows
    private final StringBuilder messageRaw = new StringBuilder();

    private String lastUsage = " ";

    public AgentChatPanel(List<String> providers, Actions actions) {
        super(new BorderLayout(0, 0));
        this.actions = actions;
        this.providerCombo = new JComboBox<>(providers.toArray(new String[0]));

        add(buildHeader(), BorderLayout.NORTH);

        transcript.setEditable(false);
        transcript.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        centre.add(buildEmptyState(), CARD_EMPTY);
        centre.add(new JScrollPane(transcript), CARD_TRANSCRIPT);
        add(centre, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setRows(1);
        input.setToolTipText("Enter sends; Shift+Enter starts a new line; drop a project file to mention it; "
                + "type / for commands");
        JScrollPane inputScroll = new JScrollPane(input, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor") != null
                        ? javax.swing.UIManager.getColor("Component.borderColor") : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        inputRow.add(inputScroll, BorderLayout.CENTER);
        input.setTransferHandler(new FileDropHandler(input.getTransferHandler()));
        transcript.setTransferHandler(new FileDropHandler(transcript.getTransferHandler()));
        // Enter sends (or picks a completion), Shift+Enter breaks the line, Tab completes a
        // /command or leaves the box, Up/Down walk the completions when they are open.
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "send");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("shift ENTER"), "newline");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("TAB"), "complete");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("UP"), "completion-up");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("DOWN"), "completion-down");
        input.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "completion-close");
        input.getActionMap().put("send", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (completion.isVisible() && selected >= 0) {
                    pick(shown.get(selected));
                } else {
                    submit();
                }
            }
        });
        input.getActionMap().put("newline", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { input.replaceSelection("\n"); }
        });
        input.getActionMap().put("complete", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { completeCommand(); }
        });
        input.getActionMap().put("completion-up", caretOrCompletion(-1, "caret-up"));
        input.getActionMap().put("completion-down", caretOrCompletion(1, "caret-down"));
        input.getActionMap().put("completion-close", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { completion.setVisible(false); }
        });
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onInputChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onInputChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onInputChanged(); }
        });
        JPanel sendButtons = new JPanel();
        sendButtons.add(stop);
        sendButtons.add(send);
        inputRow.add(sendButtons, BorderLayout.EAST);

        status.setFont(status.getFont().deriveFont(Font.PLAIN, status.getFont().getSize2D() - 1f));
        status.setForeground(mutedFg());
        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.NORTH);
        south.add(inputRow, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        stop.setEnabled(false);
        stop.addActionListener(e -> actions.abort());
        send.addActionListener(e -> submit());

        showEmptyState();
    }

    /** The built-in commands whose name starts with what was typed after the slash. */
    List<HostedChatPanel.Command> completions(String typed) {
        if (typed == null || !typed.startsWith("/") || typed.contains("\n") || typed.contains(" ")) {
            return List.of();
        }
        String prefix = typed.substring(1).toLowerCase();
        return BUILTIN_COMMANDS.stream().filter(c -> c.name().startsWith(prefix)).toList();
    }

    private void onInputChanged() {
        int rows = Math.max(1, Math.min(MAX_ROWS, input.getLineCount()));
        if (rows != input.getRows()) {
            input.setRows(rows);
            input.revalidate();
            revalidate();
        }
        shown = completions(input.getText());
        selected = -1;
        completion.setVisible(false);
        completion.removeAll();
        if (shown.isEmpty() || !input.isShowing()) {
            return;
        }
        for (HostedChatPanel.Command c : shown) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem("/" + c.name() + "  —  " + c.description());
            item.addActionListener(e -> pick(c));
            completion.add(item);
        }
        completion.setFocusable(false);
        completion.show(input, 0, -completion.getPreferredSize().height);
    }

    private void completeCommand() {
        if (shown.isEmpty()) {
            completion.setVisible(false);
            input.transferFocus();
            return;
        }
        pick(shown.get(Math.max(0, selected)));
    }

    private void pick(HostedChatPanel.Command c) {
        completion.setVisible(false);
        input.setText("/" + c.name() + " ");
        input.requestFocusInWindow();
    }

    private javax.swing.Action caretOrCompletion(int step, String caretAction) {
        return new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (completion.isVisible() && !shown.isEmpty()) {
                    selected = Math.floorMod(selected + step, shown.size());
                    for (int i = 0; i < completion.getComponentCount(); i++) {
                        if (completion.getComponent(i) instanceof javax.swing.JMenuItem item) {
                            item.setArmed(i == selected);
                        }
                    }
                } else {
                    javax.swing.Action a = input.getActionMap().get(caretAction);
                    if (a != null) {
                        a.actionPerformed(e);
                    }
                }
            }
        };
    }

    /** Called on the EDT when a turn ends, however it ended. */
    public void onTurnEnded(Runnable listener) {
        turnListeners.add(listener);
    }

    private Color mutedFg() {
        Color fg = foreground();
        return new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 150);
    }

    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(4, 2));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

        // Just a model/session banner (ellipsizes when narrow) and a compact icon bar
        // that never clips. All provider/key/sign-in config lives in a modal dialog
        // behind the ⚙, so the chat owns the panel.
        modelLabel.setFont(modelLabel.getFont().deriveFont(Font.BOLD));
        modelLabel.setForeground(accent());

        JButton sessionsButton = new JButton("Sessions");
        sessionsButton.setToolTipText("Sessions — start a new chat or resume one");
        sessionsButton.addActionListener(e -> actions.sessions());

        Icon gear = loadHeaderIcon("settings-gear.png");
        JButton settingsButton = (gear != null) ? new JButton(gear) : new JButton("⚙");
        settingsButton.setToolTipText("Agent settings — provider, API key & sign-in");
        settingsButton.addActionListener(e -> openSettings());

        JPanel icons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        icons.setOpaque(false);
        icons.add(sessionsButton);
        icons.add(settingsButton);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.add(modelLabel, BorderLayout.CENTER);
        top.add(icons, BorderLayout.EAST);

        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize2D() - 1f));
        hint.setForeground(new Color(0xB8860B));

        bar.add(top, BorderLayout.NORTH);
        bar.add(hint, BorderLayout.SOUTH);
        return bar;
    }

    /** Open the provider / key / sign-in config as a modal dialog (built once, reused). */
    private void openSettings() {
        if (settingsDialog == null) {
            Window owner = SwingUtilities.getWindowAncestor(this);
            settingsDialog = new JDialog(owner, "Agent Settings", Dialog.ModalityType.APPLICATION_MODAL);
            settingsDialog.setContentPane(buildSettingsContent());
            settingsDialog.pack();
            // Wide enough that a provider name and a key box are not squeezed
            // into a column; taller only if the user wants it.
            settingsDialog.setMinimumSize(new java.awt.Dimension(
                    Math.max(430, settingsDialog.getPreferredSize().width),
                    settingsDialog.getPreferredSize().height));
            settingsDialog.setSize(settingsDialog.getMinimumSize());
            settingsDialog.setResizable(true);
        }
        refreshCredentialState();
        settingsDialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        settingsDialog.setVisible(true);
    }

    /**
     * The provider / key / sign-in form.
     *
     * <p>
     * One section per question: which provider, then how it authenticates, then
     * what to do about it. The middle section changes with the provider, because
     * an API key box above a ChatGPT sign-in button asks you to ignore whichever
     * one does not apply. Package-private so tests can inspect it.
     */
    JPanel buildSettingsContent() {
        JPanel form = new JPanel(new BorderLayout(0, 12));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));

        form.add(buildProviderRow(), BorderLayout.NORTH);
        form.add(buildCredentialSection(), BorderLayout.CENTER);
        form.add(buildDialogButtons(), BorderLayout.SOUTH);

        providerCombo.addActionListener(e -> refreshCredentialState());
        refreshCredentialState();
        return form;
    }

    private JPanel buildProviderRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel("Provider:"), BorderLayout.WEST);
        providerCombo.setToolTipText("Which service answers your questions");
        row.add(providerCombo, BorderLayout.CENTER);
        return row;
    }

    /**
     * What this provider authenticates with: a key box, a sign-in button, or
     * neither. The title and the visible rows follow the provider.
     */
    private JPanel buildCredentialSection() {
        credentialSection = new JPanel(new GridBagLayout());
        credentialSection.setBorder(BorderFactory.createTitledBorder("Credentials"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0;
        g.weightx = 1;

        // What is stored. Without this the key box is blank whether or not a key
        // is saved, so there is no telling which.
        g.gridy = 0;
        credentialSection.add(credentialLabel, g);

        keyRow = new JPanel(new BorderLayout(6, 0));
        JPanel keyLabelled = new JPanel(new BorderLayout(6, 0));
        keyLabelled.add(new JLabel("New key:"), BorderLayout.WEST);
        keyField.setToolTipText("Paste a key to replace whatever is stored");
        keyLabelled.add(keyField, BorderLayout.CENTER);
        keyRow.add(keyLabelled, BorderLayout.CENTER);
        rememberKey.setToolTipText("Keep this key so it survives a restart: in the OS keychain, "
                + "or in ~/.xagent/settings.json as plain text when this machine has no keychain");
        keyRow.add(rememberKey, BorderLayout.EAST);
        g.gridy = 1;
        credentialSection.add(keyRow, g);

        signIn.addActionListener(e -> {
            setLoginEnabled(false);
            actions.login();
            hideSettings();   // sign-in continues in the browser; get out of the way
        });
        forgetCredential.addActionListener(e -> forgetSelectedProvider());
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionsRow.add(signIn);
        actionsRow.add(forgetCredential);
        g.gridy = 2;
        credentialSection.add(actionsRow, g);

        return credentialSection;
    }

    private JPanel buildDialogButtons() {
        JButton apply = new JButton("Apply");
        apply.setToolTipText("Use this provider, and save the key if one is typed");
        apply.addActionListener(e -> {
            String provider = (String) providerCombo.getSelectedItem();
            String key = new String(keyField.getPassword());
            keyField.setText("");
            actions.chooseProvider(new ProviderChoice(provider, key, rememberKey.isSelected()));
            hideSettings();
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> hideSettings());

        forgetAll.setToolTipText("Remove every stored API key and sign-in. Sessions are kept.");
        forgetAll.addActionListener(e -> forgetEveryProvider());

        JPanel buttons = new JPanel(new BorderLayout(12, 0));
        // The destructive one is kept away from the one people reach for.
        buttons.add(forgetAll, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(apply);
        right.add(close);
        buttons.add(right, BorderLayout.EAST);
        return buttons;
    }

    /**
     * Say what is stored for the provider in the combo, show only the controls
     * that apply to it, and offer the way out. Called whenever the answer could
     * have changed.
     */
    private void refreshCredentialState() {
        String provider = (String) providerCombo.getSelectedItem();
        if (provider == null) {
            // An empty combo has nothing to say, and saying it with the provider
            // name interpolated reads as "null needs no key."
            credentialLabel.setText(" ");
            keyRow.setVisible(false);
            signIn.setVisible(false);
            forgetCredential.setVisible(false);
            forgetAll.setEnabled(false);
            return;
        }
        AgentCredentials.State state = actions.credential(provider);
        boolean signInProvider = CODEX_PROVIDER.equals(provider);
        boolean stored = state == AgentCredentials.State.STORED;

        credentialLabel.setText(switch (state) {
            case NOT_NEEDED -> provider + " needs no key.";
            case MISSING -> signInProvider ? "Not signed in." : "No key stored for " + provider + ".";
            case STORED -> signInProvider ? "Signed in to ChatGPT."
                    : "A key is stored for " + provider + ".";
        });

        if (credentialSection != null) {
            ((javax.swing.border.TitledBorder) credentialSection.getBorder()).setTitle(switch (state) {
                case NOT_NEEDED -> "Credentials";
                default -> signInProvider ? "ChatGPT sign-in" : "API key";
            });
        }
        if (keyRow != null) {
            keyRow.setVisible(state != AgentCredentials.State.NOT_NEEDED && !signInProvider);
        }
        signIn.setVisible(signInProvider && !stored);

        forgetCredential.setText(signInProvider ? "Sign out" : "Forget key");
        forgetCredential.setVisible(state != AgentCredentials.State.NOT_NEEDED);
        forgetCredential.setEnabled(stored);
        forgetAll.setEnabled(!actions.storedCredentials().isEmpty());

        if (credentialSection != null && settingsDialog != null && settingsDialog.isVisible()) {
            // The section changes height with the provider. Grow to fit it, but
            // never shrink back over a size the user chose by dragging.
            credentialSection.revalidate();
            int wanted = settingsDialog.getPreferredSize().height;
            if (settingsDialog.getHeight() < wanted) {
                settingsDialog.setSize(settingsDialog.getWidth(), wanted);
            }
            settingsDialog.repaint();
        }
    }

    /** The provider that signs in rather than holding a key. */
    private static final String CODEX_PROVIDER = "openai-codex";

    /**
     * How a destructive choice is put to the reader. A field so a test can
     * answer it without a modal dialog — the confirmation is worth testing, and
     * a test that could only pass by removing it would be the wrong pressure.
     */
    interface Confirmer {
        boolean ask(String message, String title);
    }

    private Confirmer confirmer = (message, title) -> JOptionPane.showConfirmDialog(
            settingsDialog != null ? settingsDialog : this, message, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;

    /** Answer confirmations without a dialog. Package-private for tests. */
    void setConfirmer(Confirmer confirmer) {
        this.confirmer = confirmer;
    }

    private void forgetSelectedProvider() {
        String provider = (String) providerCombo.getSelectedItem();
        if (provider == null) {
            return;
        }
        // Asked, like forget-all is: one click here destroys a sign-in or a key
        // with no undo, and the button sits next to the provider combo.
        boolean signInProvider = CODEX_PROVIDER.equals(provider);
        boolean confirmed = confirmer.ask(
                signInProvider
                        ? "Sign out of ChatGPT?\n\nYou will have to sign in again. Sessions are kept."
                        : "Forget the stored API key for " + provider
                                + "?\n\nYou will have to paste it again. Sessions are kept.",
                signInProvider ? "Sign out" : "Forget key");
        if (!confirmed) {
            return;
        }
        actions.forgetCredential(provider);
        refreshCredentialState();
    }

    private void forgetEveryProvider() {
        List<String> stored = actions.storedCredentials();
        if (stored.isEmpty()) {
            return;
        }
        boolean confirmed = confirmer.ask(
                "Remove the stored credentials for " + String.join(", ", stored)
                        + "?\n\nYou will have to sign in or paste a key again. Sessions are kept.",
                "Forget all sign-ins");
        if (confirmed) {
            actions.forgetAllCredentials();
            refreshCredentialState();
        }
    }

    private void hideSettings() {
        if (settingsDialog != null) {
            settingsDialog.setVisible(false);
        }
    }

    private Icon loadHeaderIcon(String name) {
        try {
            java.net.URL u = getClass().getResource(
                    "/com/dogsbay/dogsbayaieditor/icons/sidebar/" + name);
            if (u == null) {
                return null;
            }
            // Tint to match the theme like the sidebar icons do, so the gear isn't a
            // fixed dark-gray that turns near-invisible on the dark theme.
            boolean dark = com.dogsbay.dogsbayaieditor.properties.TextPreferences.isDarkTheme();
            Color tint = dark ? new Color(197, 197, 197) : new Color(66, 66, 66);
            return tintIcon(new ImageIcon(u), tint);
        } catch (Exception e) {
            return null;
        }
    }

    /** Tints a grayscale icon to the given colour, preserving alpha (mirrors DogsBayAIEditor). */
    private static Icon tintIcon(ImageIcon source, Color tint) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                source.getIconWidth(), source.getIconHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.drawImage(source.getImage(), 0, 0, null);
        g.dispose();
        int tr = tint.getRed(), tg = tint.getGreen(), tb = tint.getBlue();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a > 0) {
                    img.setRGB(x, y, (a << 24) | (tr << 16) | (tg << 8) | tb);
                }
            }
        }
        return new ImageIcon(img);
    }

    /** Send text from outside the box; if the agent is busy it waits in the box, and the transcript says so. */
    public void submitText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!send.isEnabled()) {
            input.setText(text.strip());
            input.requestFocusInWindow();
            note("The agent is busy; the selection is in the box to send when it is done.");
            return;
        }
        input.setText(text.strip());
        submit();
    }

    private void submit() {
        completion.setVisible(false);
        String text = input.getText().strip();
        if (text.isEmpty() || !send.isEnabled()) {
            return;
        }
        input.setText("");
        clearEmptyState();
        append("\nYou: ", bold(foreground()));
        append(text + "\n", plain(foreground()));
        if (com.dogsbay.agent.SlashCommand.isCommand(text)) {
            actions.command(text);   // local command — no LLM round-trip, stay responsive
            return;
        }
        setBusy(true);
        actions.submit(text);
    }

    /** Enable/disable the send affordances while the agent is working. */
    public void setBusy(boolean busy) {
        boolean hadFocus = input.isFocusOwner();
        input.setEnabled(!busy);
        send.setEnabled(!busy);
        stop.setEnabled(busy);
        status.setText(busy ? "working…" : lastUsage);
        focusBack(busy, hadFocus);
    }

    /** Disable input while tools load at startup (Stop stays off). */
    public void setLoading(boolean loading) {
        boolean hadFocus = input.isFocusOwner();
        input.setEnabled(!loading);
        send.setEnabled(!loading);
        status.setText(loading ? "loading tools…" : lastUsage);
        focusBack(loading, hadFocus);
    }

    /**
     * Put the caret back in the box when the agent is done with it.
     *
     * <p>Disabling a component takes the focus off it, and nothing gave it
     * back: every question left the caret nowhere, and the next one needed a
     * click first. Remembered across the turn, because by the time the reply
     * ends the box has not been the focus owner for a while.
     */
    private void focusBack(boolean disabling, boolean hadFocus) {
        if (disabling) {
            restoreFocusWhenIdle = hadFocus;
            return;
        }
        if (restoreFocusWhenIdle && shouldTakeFocus(KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getFocusOwner())) {
            input.requestFocusInWindow();
        }
        restoreFocusWhenIdle = false;
    }

    /**
     * Whether taking the focus back would be welcome rather than rude.
     *
     * <p>Only when it is going spare — nothing holds it, or something in this
     * panel does. If the reader has moved to the document while the agent
     * worked, that is where they meant to be.
     */
    boolean shouldTakeFocus(java.awt.Component focusOwner) {
        return isShowing() && input.isEnabled()
                && (focusOwner == null || SwingUtilities.isDescendingFrom(focusOwner, this));
    }

    /** Whether the box had the focus when the agent took it away. */
    private boolean restoreFocusWhenIdle;

    /** Enable/disable the ChatGPT sign-in button (disabled while a login is in flight). */
    public void setLoginEnabled(boolean enabled) {
        signIn.setEnabled(enabled);
    }

    /** Reflect the active provider in the combo (e.g. after a successful login). */
    public void setProviderSelection(String provider) {
        providerCombo.setSelectedItem(provider);
    }

    /** Banner-style header: "provider / model · session id" (sessionId may be null). */
    public void setModelLabel(String provider, String model, String sessionId) {
        if (provider == null && model == null) {
            modelLabel.setText(" ");
            return;
        }
        String text = String.valueOf(provider) + " / " + String.valueOf(model);
        if (sessionId != null && !sessionId.isBlank()) {
            String shortId = sessionId.length() > 8 ? sessionId.substring(0, 8) : sessionId;
            text += "   ·   session " + shortId;
        }
        modelLabel.setText(text);
    }

    /** Clear the transcript (e.g. for /clear). */
    public void clearTranscript() {
        transcript.setText("");
        messageStart = -1;
        messageEnd = -1;
        messageRaw.setLength(0);
        // The readiness the card last showed, not a fresh assumption of "ready":
        // /clear on a machine with no provider used to replace "No provider
        // configured" — and its button — with an invitation to ask a question.
        showEmptyState(lastReady);
    }

    /**
     * What the panel says before anyone has said anything.
     *
     * <p>An empty transcript under a provider banner reads as a conversation
     * already under way with nothing in it. This says plainly that nothing has
     * started, and how to start one.
     */
    /** Show the empty state, assuming there is a provider to talk to. */
    public void showEmptyState() {
        showEmptyState(true);
    }

    /**
     * Show what the panel says before anyone has said anything.
     *
     * @param ready whether the agent has somewhere to send a question. On a
     *              fresh install it has not, and the way out of that is a
     *              button rather than a command someone has to be told about.
     * @return whether the card is now showing. False means a conversation is
     *         already in the panel and was left alone.
     */
    public boolean showEmptyState(boolean ready) {
        lastReady = ready;
        if (transcript.getStyledDocument().getLength() > 0) {
            return false;   // a conversation is not written over
        }
        emptyTitle.setText(ready ? "Nothing asked yet" : "No provider configured");
        emptyBody.setText(ready
                ? "<html><body style='width:220px'>Ask about the project you have "
                        + "open. The agent knows its files, grammar and keys.</body></html>"
                : "<html><body style='width:220px'>The agent needs somewhere to send "
                        + "your questions: a ChatGPT sign-in, an API key, or a local "
                        + "model.</body></html>");
        configureProvider.setVisible(!ready);
        cards.show(centre, CARD_EMPTY);
        emptyStateShowing = true;
        return true;
    }

    /** Show the conversation instead, the moment there is one. */
    private void clearEmptyState() {
        cards.show(centre, CARD_TRANSCRIPT);
        emptyStateShowing = false;
    }

    private boolean emptyStateShowing = true;

    /** Whether anything could answer, last time the card was asked to show. */
    private boolean lastReady = true;

    /** Whether the empty-state card is the one on show. Package-private for tests. */
    boolean emptyStateShowing() {
        return emptyStateShowing;
    }

    /** The empty state: a line, a sentence, and a way out of it. */
    private javax.swing.JComponent buildEmptyState() {
        JPanel column = new JPanel();
        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));
        column.setBorder(BorderFactory.createEmptyBorder(28, 16, 16, 16));

        emptyTitle.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        emptyTitle.setFont(emptyTitle.getFont().deriveFont(Font.BOLD));
        emptyBody.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        emptyBody.setForeground(mutedFg());
        configureProvider.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        configureProvider.addActionListener(e -> openSettings());

        column.add(emptyTitle);
        column.add(javax.swing.Box.createVerticalStrut(8));
        column.add(emptyBody);
        column.add(javax.swing.Box.createVerticalStrut(14));
        column.add(configureProvider);

        JPanel outer = new JPanel(new java.awt.GridBagLayout());
        outer.add(column);
        return outer;
    }

    private final JPanel centre = new JPanel(new java.awt.CardLayout());
    private final java.awt.CardLayout cards = (java.awt.CardLayout) centre.getLayout();
    private final JLabel emptyTitle = new JLabel("Nothing asked yet", javax.swing.SwingConstants.CENTER);
    private final JLabel emptyBody = new JLabel("", javax.swing.SwingConstants.CENTER);
    private final JButton configureProvider = new JButton("Configure provider…");
    private static final String CARD_EMPTY = "empty";
    private static final String CARD_TRANSCRIPT = "transcript";

    /** Append a stored conversation turn (used when resuming a session). */
    public void renderTurn(String speaker, String text) {
        clearEmptyState();
        append("\n" + speaker + ": ", bold("You".equals(speaker) ? foreground() : accent()));
        int start = transcript.getStyledDocument().getLength();
        append(text, plain(foreground()));
        if (!"You".equals(speaker)) {
            MarkdownStyler.restyle(transcript.getStyledDocument(), start, transcript.getStyledDocument().getLength(),
                    text, palette(), transcript.getFont().getSize());
        }
        append("\n", plain(foreground()));
    }

    /** Show a one-line guidance hint (e.g. from {@code ChatAgentFactory.authHint}); null clears it. */
    public void setHint(String text) {
        hint.setText(text == null || text.isBlank() ? " " : "<html>" + text + "</html>");
    }

    /**
     * Append a system note to the transcript (e.g. "Signed in to ChatGPT.").
     *
     * <p>
     * The note goes into the transcript, so the transcript is what to show. Left
     * behind the empty-state card it was invisible, and — because the card only
     * redraws over an empty transcript — the card then froze on "No provider
     * configured" until the next question was asked.
     */
    public void note(String text) {
        clearEmptyState();
        if (messageStart >= 0 && messageEnd > messageStart) {
            restyleMessage();   // the reply so far is rendered; more chunks start a new "Agent:" line
        } else if (messageStart >= 0) {
            // nothing streamed yet: drop the empty header rather than leave it dangling above the note
            StyledDocument doc = transcript.getStyledDocument();
            try {
                if (headerStart >= 0 && messageStart <= doc.getLength()) {
                    doc.remove(headerStart, messageStart - headerStart);
                }
            } catch (BadLocationException ignored) {
                // the header is ours; cannot occur
            }
            messageStart = -1;
            messageEnd = -1;
            messageRaw.setLength(0);
        }
        append("\n• " + text + "\n", muted());
    }

    /** Render one agent event. Must be called on the EDT. */
    public void handle(AgentEvent event) {
        switch (event) {
            case AgentEvent.MessageStart e -> {
                clearEmptyState();
                headerStart = transcript.getStyledDocument().getLength();
                append("\nAgent: ", bold(accent()));
                streamedText = false;
                messageStart = transcript.getStyledDocument().getLength();
                messageEnd = -1;
                messageRaw.setLength(0);
                lastStreamRestyle = 0;   // the first completed line of a reply styles at once
            }
            case AgentEvent.MessageUpdate e -> {
                if (e.partialText() != null && !e.partialText().isEmpty()) {
                    if (messageStart < 0) {
                        append("\nAgent: ", bold(accent()));   // a note split the reply
                        messageStart = transcript.getStyledDocument().getLength();
                    }
                    messageRaw.append(e.partialText());
                    append(e.partialText(), plain(foreground()));
                    messageEnd = transcript.getStyledDocument().getLength();
                    streamedText = true;
                    restyleStreamed();
                }
            }
            case AgentEvent.MessageEnd e -> {
                // Some providers (notably the Codex/ChatGPT backend) can deliver a turn
                // without streaming deltas, so the text arrives only in the final message.
                // Render it here rather than dropping it — otherwise the turn looks blank.
                if (!streamedText) {
                    AssistantMessage m = e.message();
                    String content = (m != null) ? m.content() : null;
                    if (content != null && !content.isBlank()) {
                        if (messageStart < 0) {
                            messageStart = transcript.getStyledDocument().getLength();
                        }
                        messageRaw.append(content);
                        append(content, plain(foreground()));
                        messageEnd = transcript.getStyledDocument().getLength();
                    } else if (m == null || !m.hasToolCalls()) {
                        append("(no response — the model returned nothing; try again)", muted());
                    }
                }
                restyleMessage();
                append("\n", plain(foreground()));
            }
            case AgentEvent.ToolExecutionStart e -> {
                // Close the reply first: what follows is appended past its end,
                // and a later restyle of [messageStart, messageEnd) would
                // otherwise delete the tool line sitting inside that span.
                closeOpenMessage();
                append("  → " + e.toolName() + "(" + truncate(e.arguments()) + ")\n", muted());
            }
            case AgentEvent.ToolExecutionEnd e -> {
                AgentToolResult r = e.result();
                append("  ← " + (r != null && r.isError() ? "error: " : "")
                        + truncate(r == null ? "" : r.content()) + "\n",
                        r != null && r.isError() ? error() : muted());
            }
            case AgentEvent.ErrorOccurred e -> {
                // Style what did arrive before the error: a turn that fails
                // never reaches MessageEnd, so this is the reply's last chance.
                closeOpenMessage();
                append("\n[" + shortError(e.error()) + "]\n", error());
                String remedy = remedyFor(e.error());
                if (remedy != null) {
                    append("  " + remedy + "\n", muted());
                }
                setBusy(false);   // stay responsive — let the user fix the provider and retry
                turnListeners.forEach(Runnable::run);
            }
            case AgentEvent.TurnStart e -> status.setText("working…");
            case AgentEvent.UsageUpdate e -> {
                lastUsage = String.format("↑ %d  ↓ %d tokens   ·   $%.4f",
                        e.inputTokens(), e.outputTokens(), e.estimatedCostUsd());
                status.setText(lastUsage);
            }
            case AgentEvent.RetryAttempt e -> {
                status.setText("retrying (attempt " + e.attempt() + ")…");
                closeOpenMessage();
                append("  ↻ retrying: " + truncate(e.reason()) + "\n", muted());
            }
            case AgentEvent.TurnEnd e -> {
                setBusy(false);
                turnListeners.forEach(Runnable::run);
            }
            default -> { /* MessageEnd handled above; others ignored */ }
        }
    }

    /** Files dropped on the box or the transcript become mentions; text paste and copy still work. */
    private final class FileDropHandler extends javax.swing.TransferHandler {
        private final javax.swing.TransferHandler text;

        FileDropHandler(javax.swing.TransferHandler text) {
            this.text = text;
        }

        private boolean files(TransferSupport s) {
            return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        }

        @Override public boolean canImport(TransferSupport s) {
            return files(s) || (text != null && text.canImport(s));
        }

        @Override public boolean importData(TransferSupport s) {
            if (!files(s)) {
                return text != null && text.importData(s);
            }
            try {
                @SuppressWarnings("unchecked")
                java.util.List<java.io.File> dropped = (java.util.List<java.io.File>) s.getTransferable()
                        .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                return dropFiles(dropped);
            } catch (Exception ex) {
                return false;
            }
        }

        @Override public int getSourceActions(javax.swing.JComponent c) {
            return text == null ? NONE : text.getSourceActions(c);
        }

        @Override public void exportToClipboard(javax.swing.JComponent c, java.awt.datatransfer.Clipboard clip, int action) {
            if (text != null) {
                text.exportToClipboard(c, clip, action);
            }
        }

        @Override public void exportAsDrag(javax.swing.JComponent c, java.awt.event.InputEvent e, int action) {
            if (text != null) {
                text.exportAsDrag(c, e, action);
            }
        }
    }

    /** Mention every project file among {@code files} in the input; says so for the rest. */
    boolean dropFiles(java.util.List<java.io.File> files) {
        java.util.List<String> mentions = new java.util.ArrayList<>();
        java.util.List<String> outside = new java.util.ArrayList<>();
        for (java.io.File f : files) {
            String m = actions.mention(f);
            if (m != null) {
                mentions.add(m);
            } else {
                outside.add(f.getName());
            }
        }
        if (!mentions.isEmpty()) {
            String current = input.getText();
            String sep = current.isEmpty() || current.endsWith(" ") ? "" : " ";
            input.replaceSelection(sep + String.join(" ", mentions) + " ");
            input.requestFocusInWindow();
        }
        if (!outside.isEmpty()) {
            note("Not attached (outside the project, not a file, or a name the @mention cannot carry): "
                    + String.join(", ", outside));
        }
        return !mentions.isEmpty();
    }

    /** The input's text, for tests. */
    String inputText() {
        return input.getText();
    }

    /** Put text in the box and send it, for tests. */
    void typeAndSend(String text) {
        input.setText(text);
        submit();
    }

    /** Style and close the reply in progress, if there is one. */
    private void closeOpenMessage() {
        if (messageStart >= 0 && messageEnd > messageStart) {
            int trailing = trailingNewlines(messageRaw);
            restyleMessage();
            if (trailing > 0) {
                append("\n".repeat(trailing), plain(foreground()));
            }
        }
    }

    /** How many newlines a message ends with — the styler drops them. */
    private static int trailingNewlines(CharSequence text) {
        int n = 0;
        while (n < text.length() && text.charAt(text.length() - 1 - n) == '\n') {
            n++;
        }
        return n;
    }

    /** Replace the raw text of the message that just ended with its Markdown rendering. */
    private void restyleMessage() {
        applyMarkdown();
        messageStart = -1;
        messageEnd = -1;
        messageRaw.setLength(0);
        transcript.setCaretPosition(transcript.getStyledDocument().getLength());
    }

    /**
     * Style the reply so far, while it is still arriving.
     *
     * <p>Two conditions, because styling every token would be both slow — each
     * pass re-parses the whole message — and ugly: mid-line the markup is
     * half-written, so {@code **bold} would flicker between styles as it
     * completes. So only on a completed line, and only when the last pass was
     * long enough ago; an unclosed code fence waits for its closing one, since
     * everything after it would be styled as prose and then re-styled as code.
     */
    private void restyleStreamed() {
        // The clock first: everything below is proportional to the message, and
        // a reply arrives in thousands of chunks. Checking the cheap thing first
        // keeps the per-chunk cost constant.
        //
        // Each pass rewrites the whole message, so the interval grows with it:
        // a 20,000-character reply restyles about twice a second rather than
        // seven times, which is the difference between smooth and stuttering.
        long interval = Math.max(STREAM_RESTYLE_MS, messageRaw.length() / 40);
        long now = System.currentTimeMillis();
        if (now - lastStreamRestyle < interval || messageRaw.isEmpty()
                || messageRaw.charAt(messageRaw.length() - 1) != '\n') {
            return;
        }
        String raw = messageRaw.toString();
        if (openFence(raw)) {
            return;
        }
        lastStreamRestyle = now;

        // The styler trims trailing newlines, so put back the ones the raw text
        // ends with — without them the next chunk lands on the end of this line.
        int trailing = trailingNewlines(raw);
        applyMarkdown();
        if (trailing > 0) {
            append("\n".repeat(trailing), plain(foreground()));
            messageEnd = transcript.getStyledDocument().getLength();
        }
    }

    /**
     * Whether a fenced code block is still open.
     *
     * <p>Counted at line starts only: a reply about markdown mentions {@code
     * ```} inside an inline code span, and counting those made the parity odd
     * for the rest of the reply — which silently stopped styling it.
     */
    static boolean openFence(String raw) {
        int fences = 0;
        for (String line : raw.split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                fences++;
            }
        }
        return fences % 2 != 0;
    }

    /**
     * Render the message's raw markdown in place.
     *
     * <p>The styled text is shorter than the raw — the markers are gone — so
     * where the message ends moves, and the next chunk has to be appended after
     * the new end rather than the old one.
     */
    private void applyMarkdown() {
        javax.swing.text.StyledDocument doc = transcript.getStyledDocument();
        int lengthBefore = doc.getLength();
        MarkdownStyler.restyle(doc, messageStart, messageEnd, messageRaw.toString(),
                palette(), transcript.getFont().getSize());
        messageEnd += doc.getLength() - lengthBefore;
    }

    /** How often the streaming reply may be re-styled, in milliseconds. */
    private static final long STREAM_RESTYLE_MS = 150;
    private long lastStreamRestyle;

    private MarkdownStyler.Palette palette() {
        Color code = javax.swing.UIManager.getColor("TextField.inactiveBackground");
        Color link = javax.swing.UIManager.getColor("Component.linkColor");
        return new MarkdownStyler.Palette(foreground(), mutedFg(),
                code != null ? code : new Color(0xEEEEEE), link != null ? link : accent());
    }

    private void append(String text, SimpleAttributeSet style) {
        StyledDocument doc = transcript.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
            transcript.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // append-only at end; cannot occur
        }
    }

    // ── styles ───────────────────────────────────────────────────────────────

    private Color foreground() {
        return transcript.getForeground() != null ? transcript.getForeground() : Color.BLACK;
    }

    private Color accent() {
        return new Color(0x2675BF);
    }

    private SimpleAttributeSet plain(Color c) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        return a;
    }

    private SimpleAttributeSet bold(Color c) {
        SimpleAttributeSet a = plain(c);
        StyleConstants.setBold(a, true);
        return a;
    }

    private SimpleAttributeSet muted() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        Color fg = foreground();
        StyleConstants.setForeground(a, new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 160));
        StyleConstants.setFontFamily(a, Font.MONOSPACED);
        return a;
    }

    private SimpleAttributeSet error() {
        return plain(new Color(0xC0392B));
    }

    /**
     * What to do about an error, when the error itself does not say. A backend
     * that rejects the model names the model but not the way to change it, and
     * the model is not something the panel offers a control for — it is a slash
     * command, which is discoverable only if something says so.
     */
    static String remedyFor(Throwable t) {
        // The cause chain, not just the top: a provider error usually reaches
        // here wrapped, and the wrapper's own message says nothing useful.
        StringBuilder all = new StringBuilder();
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c.getMessage() != null) {
                all.append(c.getMessage()).append('\n');
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        String lower = all.toString().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("model") && (lower.contains("not supported")
                || lower.contains("does not exist") || lower.contains("unknown model")
                || lower.contains("invalid model"))) {
            return "The provider rejected this model. Run /model to see it, "
                    + "/model <name> to change it, or /provider to switch provider.";
        }
        if (lower.contains("http 401") || lower.contains("unauthorized")
                || lower.contains("invalid api key")) {
            return "The provider rejected the credentials. Run /login to sign in again, "
                    + "or /provider to switch to one you have a key for.";
        }
        return null;
    }

    private static String shortError(Throwable t) {
        String msg = t == null ? "error" : (t.getMessage() != null ? t.getMessage() : t.toString());
        return truncate(msg);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 160 ? oneLine.substring(0, 157) + "…" : oneLine;
    }

    // test/diagnostic hooks
    public String transcriptText() {
        return transcript.getText();
    }

    String hintText() {
        return hint.getText();
    }

    String modelLabelText() {
        return modelLabel.getText();
    }

    String statusText() {
        return status.getText();
    }

    /** Convenience for callers off the EDT. */
    public void handleLater(AgentEvent event) {
        SwingUtilities.invokeLater(() -> handle(event));
    }
}
