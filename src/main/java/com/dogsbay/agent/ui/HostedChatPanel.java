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
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.dogsbay.agent.acp.AcpWire.PlanEntry;
import com.dogsbay.agent.acp.AcpWire.Update;

/**
 * The chat for one hosted ACP agent: a transcript that renders the agent's
 * streamed updates, an input row, and a header with the agent's name, its
 * tier badge, a Follow toggle and a sign-in button that appears only when
 * the agent asks for authentication. Swing-only; knows nothing about the
 * editor or the protocol beyond {@link Update}.
 */
public final class HostedChatPanel extends JPanel {

    /** What the panel asks its controller to do. */
    public interface Actions {
        void submit(String text);

        void abort();

        void login();

        void followChanged(boolean follow);

        /** The {@code @path} mention for a dropped file, or null when it cannot be attached. */
        default String mention(java.io.File file) {
            return null;
        }
    }

    private final JTextPane transcript = new JTextPane();
    private final javax.swing.JTextArea input = new javax.swing.JTextArea(1, 20);
    /** One of the agent's slash commands. */
    record Command(String name, String description) {}

    private final List<Command> commands = new java.util.ArrayList<>();
    private final JPopupMenu completion = new JPopupMenu();
    private List<Command> shown = List.of();
    private int selected = -1;
    private static final int MAX_ROWS = 6;
    private final JButton send = new JButton("Send");
    private final JButton stop = new JButton("Stop");
    private final JButton login = new JButton("Sign in");
    private final JCheckBox follow = new JCheckBox("Follow");
    private final JLabel title = new JLabel(" ");
    private final JLabel badge = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final Actions actions;
    private boolean inMessage;
    private int messageStart = -1;
    private int messageEnd = -1;
    private final StringBuilder messageRaw = new StringBuilder();

    public HostedChatPanel(String agentName, String tierBadge, String tierDescription, Actions actions) {
        super(new BorderLayout(0, 0));
        this.actions = actions;

        title.setText(agentName);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(accent());
        badge.setText(" " + tierBadge + " ");
        badge.setToolTipText(tierDescription);
        badge.setOpaque(true);
        badge.setBackground(tierBadge.startsWith("T3") ? new Color(0xC0392B) : new Color(0x2E86AB));
        badge.setForeground(Color.WHITE);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, badge.getFont().getSize2D() - 1f));
        follow.setToolTipText("Open and scroll to the files the agent edits");
        follow.addActionListener(e -> actions.followChanged(follow.isSelected()));
        login.setVisible(false);
        login.addActionListener(e -> actions.login());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(title);
        left.add(badge);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(login);
        right.add(follow);
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        transcript.setEditable(false);
        transcript.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(new JScrollPane(transcript), BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setRows(1);
        input.setToolTipText("Enter sends; Shift+Enter starts a new line; drop a project file to mention it; "
                + "type / for the agent's commands");
        JScrollPane inputScroll = new JScrollPane(input, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        inputRow.add(inputScroll, BorderLayout.CENTER);
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
        input.setTransferHandler(new FileDropHandler(input.getTransferHandler()));
        transcript.setTransferHandler(new FileDropHandler(transcript.getTransferHandler()));
        JPanel buttons = new JPanel();
        buttons.add(stop);
        buttons.add(send);
        inputRow.add(buttons, BorderLayout.EAST);
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
        setReady(false);
    }

    /**
     * Files dropped on the box or the transcript become mentions; everything
     * else, pasting text in and copying text out, still goes to Swing's own
     * text handler, which this one wraps.
     */
    private final class FileDropHandler extends javax.swing.TransferHandler {
        private final javax.swing.TransferHandler text;

        FileDropHandler(javax.swing.TransferHandler text) {
            this.text = text;
        }

        private boolean files(TransferSupport support) {
            return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
        }

        @Override public boolean canImport(TransferSupport support) {
            return files(support) || (text != null && text.canImport(support));
        }

        @Override public boolean importData(TransferSupport support) {
            if (!files(support)) {
                return text != null && text.importData(support);
            }
            try {
                @SuppressWarnings("unchecked")
                List<java.io.File> dropped = (List<java.io.File>) support.getTransferable()
                        .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                return dropFiles(dropped);
            } catch (Exception ex) {
                return false;
            }
        }

        @Override public int getSourceActions(javax.swing.JComponent c) {
            return text == null ? NONE : text.getSourceActions(c);
        }

        @Override public void exportToClipboard(javax.swing.JComponent c, java.awt.datatransfer.Clipboard clip,
                int action) {
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

    /** Up or Down: walk the completions while they show, else the text area's own caret move. */
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

    private void pick(Command c) {
        completion.setVisible(false);
        input.setText("/" + c.name() + " ");
        input.requestFocusInWindow();
    }

    /** The box shows what is typed, one to {@link #MAX_ROWS} lines. */
    private void grow() {
        int rows = Math.max(1, Math.min(MAX_ROWS, input.getLineCount()));
        if (rows != input.getRows()) {
            input.setRows(rows);
            input.revalidate();
            revalidate();
        }
    }

    private static final java.util.regex.Pattern ATTACHMENT_ECHO = java.util.regex.Pattern.compile(
            "(?s)^\\s*(?:<context\\s+ref=\"[^\"]*\">.*"          // Claude Code's embedded resource
            + "|\\[@?[^\\]]*\\]\\((?:file|https?)://[^)]*\\)\\s*"   // a resource link as Markdown
            + "|\\[(?:resource|file)[^\\]]*\\]\\s*"                   // the wire's own marker
            + "|)$");

    /**
     * Whether a replayed user chunk is an attachment the editor sent along with
     * a prompt (the file's text or a link to it), not something the user typed.
     */
    static boolean isAttachmentEcho(String chunk) {
        return chunk == null || ATTACHMENT_ECHO.matcher(chunk).matches();
    }

    /** Mention every project file among {@code files} in the input; says so for the rest. */
    boolean dropFiles(List<java.io.File> files) {
        List<String> mentions = new java.util.ArrayList<>();
        List<String> outside = new java.util.ArrayList<>();
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
            String sep = current.isEmpty() || current.endsWith(" ") || current.endsWith("\n") ? "" : " ";
            input.replaceSelection(sep + String.join(" ", mentions) + " ");
            input.requestFocusInWindow();
        }
        if (!outside.isEmpty()) {
            note("Not attached (outside the project, not a file, or a name the @mention cannot carry): "
                    + String.join(", ", outside));
        }
        return !mentions.isEmpty();
    }

    /** The agent's commands whose name starts with what was typed after the slash. */
    List<Command> completions(String typed) {
        if (typed == null || !typed.startsWith("/") || typed.contains("\n") || typed.contains(" ")) {
            return List.of();
        }
        String prefix = typed.substring(1).toLowerCase();
        return commands.stream().filter(c -> c.name().toLowerCase().startsWith(prefix)).limit(12).toList();
    }

    private void onInputChanged() {
        grow();
        shown = completions(input.getText());
        selected = -1;
        completion.setVisible(false);
        completion.removeAll();
        if (shown.isEmpty() || !input.isShowing()) {
            return;
        }
        for (Command c : shown) {
            String label = "/" + c.name()
                    + (c.description() == null || c.description().isBlank() ? "" : "  —  " + shorten(c.description()));
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
            item.addActionListener(e -> pick(c));
            completion.add(item);
        }
        completion.setFocusable(false);
        completion.show(input, 0, -completion.getPreferredSize().height);
    }

    /** Tab: complete the first (or the walked-to) command; with nothing to complete, leave the box. */
    private void completeCommand() {
        if (shown.isEmpty()) {
            completion.setVisible(false);
            input.transferFocus();
            return;
        }
        pick(shown.get(Math.max(0, selected)));
    }

    private static String shorten(String s) {
        String t = s.replace('\n', ' ').strip();
        return t.length() > 70 ? t.substring(0, 70) + "…" : t;
    }

    /**
     * Send text that came from outside the box, such as a selection from the
     * editor or terminal. If a turn is running or the session is not ready, the
     * text waits in the box instead, and the transcript says so.
     */
    public void submitText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (stop.isEnabled() || !send.isEnabled()) {
            input.setText(text.strip());
            input.requestFocusInWindow();
            note(stop.isEnabled() ? "A turn is running; the selection is in the box to send when it ends."
                    : "The session is not ready yet; the selection is in the box.");
            return;
        }
        input.setText(text.strip());
        submit();
    }

    private void submit() {
        completion.setVisible(false);
        String text = input.getText().trim();
        if (text.isEmpty() || stop.isEnabled() || !send.isEnabled()) {
            return;   // a turn is running, or the session is not ready
        }
        input.setText("");
        endMessage();   // a stray reply chunk must not swallow the user's line on restyle
        append("\nYou: ", bold(foreground()));
        append(text + "\n", plain(foreground()));
        setBusy(true);
        actions.submit(text);
    }

    // ── state from the controller (call on the EDT) ─────────────────────

    /** Input is enabled once the session exists. */
    /** The name shown above the transcript; a session can be renamed. */
    public void setTitle(String text) {
        title.setText(text);
    }

    public void setReady(boolean ready) {
        input.setEnabled(ready);
        send.setEnabled(ready);
        if (!ready) {
            status.setText("connecting…");
        } else if (!stop.isEnabled()) {
            status.setText(" ");
        }
    }

    public void setBusy(boolean busy) {
        stop.setEnabled(busy);
        send.setEnabled(!busy && input.isEnabled());
        status.setText(busy ? "working…" : " ");
        if (!busy) {
            endMessage();   // the usual end of a reply: END_TURN with nothing after it
        }
    }

    public void setLoginVisible(boolean visible) {
        login.setVisible(visible);
        revalidate();
    }

    public void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    public boolean isFollowing() {
        return follow.isSelected();
    }

    public void note(String text) {
        endMessage();   // a note is not part of the reply; the reply so far is rendered first
        append("\n• " + text + "\n", muted());
    }

    public void error(String text) {
        endMessage();
        append("\n[" + text + "]\n", error());
    }

    /** Render one streamed update. */
    public void render(Update update) {
        switch (update) {
            case Update.AgentMessage m -> {
                if (!inMessage) {
                    append("\nAgent: ", bold(accent()));
                    inMessage = true;
                    messageStart = transcript.getStyledDocument().getLength();
                    messageRaw.setLength(0);
                }
                messageRaw.append(m.text());
                append(m.text(), plain(foreground()));
                messageEnd = transcript.getStyledDocument().getLength();
            }
            case Update.AgentThought t -> {
                // thoughts are shown dimly and never interleave with the message
                endMessage();
                append("  ⋯ " + oneLine(t.text()) + "\n", muted());
            }
            case Update.UserMessage u -> {
                if (isAttachmentEcho(u.text())) {
                    break;   // the file that travelled with a prompt; the @mention in the text names it
                }
                // replayed history: the user's turn, then the reply starts its own block
                endMessage();
                append("\nYou: ", bold(foreground()));
                append(u.text().strip() + "\n", plain(foreground()));
            }
            case Update.ToolCall c -> {
                endMessage();
                append("  → " + label(c.kind(), c.title()) + locations(c.locations()) + "\n", muted());
            }
            case Update.ToolCallUpdate c -> {
                if ("completed".equals(c.status()) || "failed".equals(c.status())) {
                    endMessage();
                    append("  ← " + ("failed".equals(c.status()) ? "failed: " : "")
                            + label(c.kind(), c.title()) + "\n", muted());
                }
            }
            case Update.Plan p -> {
                endMessage();
                append("  Plan:\n", muted());
                for (PlanEntry e : p.entries()) {
                    String mark = "completed".equals(e.status()) ? "✓" : "in_progress".equals(e.status()) ? "▸" : "·";
                    append("    " + mark + " " + oneLine(e.content()) + "\n", muted());
                }
            }
            case Update.Other o -> {
                if ("available_commands_update".equals(o.kind())) {
                    commands.clear();
                    o.raw().path("availableCommands").forEach(c ->
                            commands.add(new Command(c.path("name").asText(), c.path("description").asText(null))));
                    List<String> names = commands.stream().map(c -> "/" + c.name()).toList();
                    if (names.size() > 8) {
                        // Claude Code reports every installed skill; dozens of lines
                        // would bury the conversation.
                        note(names.size() + " agent commands available, e.g. "
                                + String.join("  ", names.subList(0, 6)) + "  … Type / to see them.");
                    } else if (!names.isEmpty()) {
                        note("Agent commands: " + String.join("  ", names));
                    }
                }
            }
        }
    }

    private void endMessage() {
        if (inMessage) {
            restyleMessage();
            append("\n", plain(foreground()));
            inMessage = false;
        }
    }

    /** Replace the raw text of the message that just ended with its Markdown rendering. */
    private void restyleMessage() {
        MarkdownStyler.restyle(transcript.getStyledDocument(), messageStart, messageEnd, messageRaw.toString(),
                palette(), transcript.getFont().getSize());
        messageStart = -1;
        messageEnd = -1;
        messageRaw.setLength(0);
        transcript.setCaretPosition(transcript.getStyledDocument().getLength());
    }

    private MarkdownStyler.Palette palette() {
        Color code = UIManager.getColor("TextField.inactiveBackground");
        Color link = UIManager.getColor("Component.linkColor");
        return new MarkdownStyler.Palette(foreground(), mutedFg(),
                code != null ? code : new Color(0xEEEEEE), link != null ? link : accent());
    }

    private static String label(String kind, String title) {
        String t = title == null || title.isBlank() ? "tool" : title;
        return kind == null || kind.isBlank() || "other".equals(kind) ? t : kind + ": " + t;
    }

    private static String locations(List<String> locations) {
        return locations.isEmpty() ? "" : "  (" + String.join(", ", locations) + ")";
    }

    private static String oneLine(String s) {
        String t = s.replace('\n', ' ').strip();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    /** The input's text, for tests. */
    String inputText() {
        return input.getText();
    }

    /** The transcript text, for tests. */
    public String transcriptText() {
        return transcript.getText();
    }

    public void focusInput() {
        SwingUtilities.invokeLater(input::requestFocusInWindow);
    }

    // ── styling ─────────────────────────────────────────────────────────

    private void append(String text, SimpleAttributeSet style) {
        StyledDocument doc = transcript.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
            transcript.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
            // append-only at end; cannot occur
        }
    }

    private static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : new Color(0x2E86AB);
    }

    private Color foreground() {
        Color c = UIManager.getColor("TextPane.foreground");
        return c != null ? c : Color.BLACK;
    }

    private static Color mutedFg() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
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
        return plain(mutedFg());
    }

    private SimpleAttributeSet error() {
        return plain(new Color(0xC0392B));
    }
}
