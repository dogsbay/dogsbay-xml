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

package com.dogsbay.dogsbayaieditor.author;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.xml.editor.Editor;

/**
 * The Author+Editor split: XML source and the WYSIWYG Author view side by
 * side, where exactly one pane is <em>live</em> — the one holding keyboard
 * focus — and the other is a mirror refreshed a few hundred milliseconds
 * behind. Clicking into the mirror flushes the live side into the document
 * and swaps the roles (the same synchronization that runs on full view
 * switches, triggered by focus instead).
 *
 * <p>The handoff into the Author pane is refused while the XML does not
 * parse: the Author surface must never go live over content it cannot
 * represent.
 */
public class AuthorSplitView extends ViewPanel {

    public enum Side { EDITOR, AUTHOR }

    private static final int MIRROR_DELAY_MS = 500;

    private final DogsBayView view;
    private final JSplitPane splitPane;
    private final JPanel editorHolder = new JPanel(new BorderLayout());
    private final JPanel authorHolder = new JPanel(new BorderLayout());

    private Editor editor;
    private AuthorView author;
    private Side liveSide = Side.EDITOR;
    private boolean active;
    private boolean syncing;

    /** Refreshes the mirror side after the live side went quiet. */
    private final javax.swing.Timer mirrorTimer;

    /** Live-side edits land on the shared ChangeManager; that's our trigger. */
    private final ChangeListener changeListener = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
            if (active && !syncing) {
                mirrorTimer.restart();
            }
        }
    };

    /** Focus decides which pane is live. */
    private final PropertyChangeListener focusListener = e -> {
        if (!active || !(e.getNewValue() instanceof Component owner)) {
            return;
        }
        if (SwingUtilities.isDescendingFrom(owner, editorHolder)) {
            makeLive(Side.EDITOR);
        } else if (SwingUtilities.isDescendingFrom(owner, authorHolder)) {
            makeLive(Side.AUTHOR);
        }
    };

    public AuthorSplitView(DogsBayView view) {
        super(new BorderLayout());
        this.view = view;
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        add(splitPane, BorderLayout.CENTER);

        mirrorTimer = new javax.swing.Timer(MIRROR_DELAY_MS, e -> refreshMirror());
        mirrorTimer.setRepeats(false);
    }

    public Side getLiveSide() {
        return liveSide;
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------
    // Lifecycle: the Editor and AuthorView components are borrowed from the
    // DogsBayView card layout and handed back on deactivate.
    // ------------------------------------------------------------------

    /** Hosts the two components and starts mirroring. */
    public void activate(Editor editor, AuthorView author, boolean xmlLeft, Side live) {
        this.editor = editor;
        this.author = author;

        editorHolder.removeAll();
        editorHolder.add(editor, BorderLayout.CENTER);
        authorHolder.removeAll();
        authorHolder.add(author, BorderLayout.CENTER);
        // the borrowed components come from a CardLayout, which leaves
        // non-top cards with visible=false — they would render blank here
        editor.setVisible(true);
        author.setVisible(true);
        splitPane.setLeftComponent(xmlLeft ? editorHolder : authorHolder);
        splitPane.setRightComponent(xmlLeft ? authorHolder : editorHolder);
        // proportional divider locations are ignored before the pane has a
        // size — defer until after layout
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.5));

        liveSide = live;
        active = true;
        updateLiveBorders();

        // the mirror side starts from the current document state
        syncing = true;
        try {
            if (liveSide == Side.EDITOR) {
                refreshAuthorPane();
            } else {
                refreshEditorPane();
            }
        } finally {
            syncing = false;
        }

        view.getChangeManager().addChangeListener(changeListener);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusListener);
        revalidate();
        repaint();
    }

    /** Stops mirroring and releases the borrowed components (caller re-cards them). */
    public void deactivate() {
        active = false;
        mirrorTimer.stop();
        view.getChangeManager().removeChangeListener(changeListener);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("focusOwner", focusListener);
        editorHolder.removeAll();
        authorHolder.removeAll();
        splitPane.setLeftComponent(null);
        splitPane.setRightComponent(null);
        editor = null;
        author = null;
    }

    // ------------------------------------------------------------------
    // Live / mirror mechanics
    // ------------------------------------------------------------------

    /** Flushes the current live side into the document, then swaps roles. */
    private void makeLive(Side side) {
        if (liveSide == side || syncing) {
            return;
        }
        syncing = true;
        try {
            if (side == Side.AUTHOR) {
                // entering Author: the XML must parse, or Author would go
                // live over content it cannot see
                if (view.getChangeManager().isTextChanged()) {
                    try {
                        editor.parse();
                    } catch (Exception parseError) {
                        view.getMainEditor().setStatus(
                                "Fix the XML errors before editing in the Author pane");
                        SwingUtilities.invokeLater(() -> editor.setFocus());
                        return;
                    }
                }
                refreshAuthorPane();
            } else {
                // entering Editor: flush pending Author edits into the text
                if (author.isModelDirty()) {
                    author.applyToDocument();
                }
                refreshEditorPane();
            }
            liveSide = side;
            updateLiveBorders();
        } finally {
            syncing = false;
        }
    }

    /** Debounced refresh of whichever side is currently the mirror. */
    private void refreshMirror() {
        if (!active || syncing) {
            return;
        }
        syncing = true;
        try {
            if (liveSide == Side.AUTHOR) {
                if (author.isModelDirty()) {
                    author.applyToDocument();
                }
                refreshEditorPane();
            } else {
                try {
                    if (view.getChangeManager().isTextChanged()) {
                        editor.parse();
                    }
                    refreshAuthorPane();
                } catch (Exception parseError) {
                    // mid-edit XML often doesn't parse — keep the last good mirror
                }
            }
        } finally {
            syncing = false;
        }
    }

    private void refreshAuthorPane() {
        if (!author.hasLatestInformation()) {
            author.setDocument(view.getDocument());
        }
    }

    private void refreshEditorPane() {
        if (!editor.hasLatestInformation()) {
            // Editor.setDocument wraps the buffer reset in an insignificant
            // compound, so the mirror refresh never pollutes undo
            editor.setDocument(view.getDocument());
        }
    }

    /** Called by DogsBayView.updateModel() before others read the document. */
    public void flushLiveSide() throws Exception {
        if (!active) {
            return;
        }
        syncing = true;
        try {
            if (liveSide == Side.AUTHOR) {
                if (author.isModelDirty()) {
                    author.applyToDocument();
                }
            } else if (view.getChangeManager().isTextChanged()) {
                editor.parse();
            }
        } finally {
            syncing = false;
        }
    }

    /** Reloads only the XML pane from the document (the Author model changed). */
    public void refreshEditorFromModel() {
        if (!active || editor.hasLatestInformation()) {
            return;
        }
        syncing = true;
        try {
            editor.setDocument(view.getDocument());
        } finally {
            syncing = false;
        }
    }

    /** Reloads only the Author pane from the document (the XML text changed). */
    public void refreshAuthorFromText() {
        if (!active || author.hasLatestInformation()) {
            return;   // re-importing an up-to-date pane would throw away its undo history
        }
        syncing = true;
        try {
            author.setDocument(view.getDocument());
        } finally {
            syncing = false;
        }
    }

    /** Called by DogsBayView.reload() after an external change. */
    public void refreshBothPanes() {
        syncing = true;
        try {
            if (!editor.hasLatestInformation()) {
                editor.setDocument(view.getDocument());
            }
            if (!author.hasLatestInformation()) {
                // re-importing an up-to-date Author pane would replace the block
                // model, and with it the undo history those edits belong to
                author.setDocument(view.getDocument());
            }
        } finally {
            syncing = false;
        }
    }

    private void updateLiveBorders() {
        Color accent = UIManager.getColor("Component.focusColor");
        if (accent == null) {
            accent = new Color(0x2675BF);
        }
        Color idle = UIManager.getColor("Panel.background");
        editorHolder.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0,
                liveSide == Side.EDITOR ? accent : idle));
        authorHolder.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0,
                liveSide == Side.AUTHOR ? accent : idle));
    }

    // ------------------------------------------------------------------
    // ViewPanel
    // ------------------------------------------------------------------

    @Override
    public void setFocus() {
        if (liveSide == Side.AUTHOR && author != null) {
            author.setFocus();
        } else if (editor != null) {
            editor.setFocus();
        }
    }

    @Override
    public void updatePreferences() {
        // the hosted Editor/AuthorView instances are updated by DogsBayView
        updateLiveBorders();
    }

    @Override
    public void setProperties() {
    }
}
