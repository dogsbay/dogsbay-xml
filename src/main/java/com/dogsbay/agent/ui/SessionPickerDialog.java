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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import com.xagent.session.SessionManager.SessionInfo;

/**
 * Choose a session to resume, start a new one, or delete ones you are done
 * with.
 *
 * <p>The old picker was a combo box of the fifteen most recent sessions with no
 * way to remove any of them, so transcripts accumulated on disk unreachable and
 * uncountable — 237 of them on the machine that prompted this. This lists every
 * session, says how many there are, and can delete.
 */
public final class SessionPickerDialog extends JDialog {

    /** What the dialog was closed with. */
    public enum Outcome { RESUME, NEW, CANCELLED }

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final DefaultListModel<SessionInfo> model = new DefaultListModel<>();
    private final JList<SessionInfo> list = new JList<>(model);
    private final JLabel countLabel = new JLabel();
    private final Predicate<String> deleter;
    private final String activeSessionId;

    private Outcome outcome = Outcome.CANCELLED;
    private String chosen;

    /**
     * @param parent           dialog parent
     * @param sessions         every session, most recent first
     * @param activeSessionId  the conversation in progress, which cannot be
     *                         deleted from under itself; may be null
     * @param deleter          deletes one session by id and reports whether it
     *                         went; called on the event thread
     */
    public SessionPickerDialog(Component parent, List<SessionInfo> sessions,
            String activeSessionId, Predicate<String> deleter) {
        super(JOptionPane.getFrameForComponent(parent), "Agent sessions", true);
        this.deleter = deleter;
        this.activeSessionId = activeSessionId;
        sessions.forEach(model::addElement);
        // The X button must dispose like Cancel does, or every close leaks a
        // hidden dialog holding the whole session list.
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(560, 320));

        JButton resume = new JButton("Resume");
        resume.addActionListener(e -> finish(Outcome.RESUME));
        JButton newSession = new JButton("New session");
        newSession.addActionListener(e -> finish(Outcome.NEW));
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> { outcome = Outcome.CANCELLED; dispose(); });

        list.addListSelectionListener(e -> updateButtons(resume, delete));
        updateButtons(resume, delete);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(delete);
        buttons.add(Box.createHorizontalStrut(24));
        buttons.add(newSession);
        buttons.add(cancel);
        buttons.add(resume);

        countLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        countLabel.setHorizontalAlignment(SwingConstants.LEFT);
        updateCount();

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        content.add(countLabel, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(resume);
        pack();
        setLocationRelativeTo(parent);
    }

    /** The session to resume, when {@link #outcome()} is RESUME. */
    public String chosenSessionId() {
        return chosen;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Resume needs a selection; delete needs one that is not the live conversation. */
    private void updateButtons(JButton resume, JButton delete) {
        SessionInfo selected = list.getSelectedValue();
        resume.setEnabled(selected != null);
        delete.setEnabled(selected != null && !isActive(selected));
        delete.setToolTipText(selected != null && isActive(selected)
                ? "This is the conversation in progress" : null);
    }

    private boolean isActive(SessionInfo s) {
        return activeSessionId != null && activeSessionId.equals(s.id());
    }

    private void finish(Outcome how) {
        SessionInfo selected = list.getSelectedValue();
        if (how == Outcome.RESUME && selected == null) {
            return;
        }
        chosen = selected == null ? null : selected.id();
        outcome = how;
        dispose();
    }

    private void deleteSelected() {
        SessionInfo selected = list.getSelectedValue();
        if (selected == null || isActive(selected)) {
            // Deleting the live session leaves the agent appending to a file
            // with no header, which no later run can list or resume.
            return;
        }
        // Deleting a transcript is not undoable and the preview is the only
        // thing identifying it, so it goes in the question.
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete this session?\n\n" + label(selected) + "\n\nThis cannot be undone.",
                "Delete session", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        int index = list.getSelectedIndex();
        if (!deleter.test(selected.id())) {
            // The transcript is still there — a read-only directory, a locked
            // file. Leaving the row is the honest answer; removing it would
            // claim a deletion that did not happen.
            JOptionPane.showMessageDialog(this, "That session could not be deleted.",
                    "Delete session", JOptionPane.ERROR_MESSAGE);
            return;
        }
        model.remove(index);
        if (!model.isEmpty()) {
            list.setSelectedIndex(Math.min(index, model.getSize() - 1));
        }
        updateCount();
    }

    private void updateCount() {
        int n = model.getSize();
        countLabel.setText(n == 1 ? "1 session" : n + " sessions");
    }

    /** One line per session: when, then what it was about. */
    static String label(SessionInfo s) {
        String when = s.timestamp() == null ? "" : WHEN.format(s.timestamp());
        String preview = s.preview() == null || s.preview().isBlank()
                ? s.provider() + "/" + s.model()
                : s.preview();
        return when + "   " + preview;
    }

    /** Labels for a list of sessions, for tests and for anything text-mode. */
    static List<String> labels(List<SessionInfo> sessions) {
        List<String> out = new ArrayList<>();
        sessions.forEach(s -> out.add(label(s)));
        return out;
    }

    private static final class Renderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof SessionInfo s) {
                setText(label(s));
                setToolTipText(s.id() + " — " + s.cwd());
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return this;
        }
    }
}
