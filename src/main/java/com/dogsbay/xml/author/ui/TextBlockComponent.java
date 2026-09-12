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

package com.dogsbay.xml.author.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;

/**
 * The editing surface for one text block. Inline semantics ride on every
 * character as a {@code Map<String,String>} under {@link #RUN_ATTRS} (the same
 * attribute maps the model's {@link InlineRun}s use); visual styling is
 * derived from that map via the stylesheet. Extraction back to runs groups
 * consecutive characters with equal semantic maps.
 *
 * <p>Empty reference runs ({@code <keyword keyref=.../>}, {@code <xref href=.../>})
 * render as {@code ⟨target⟩} placeholders whose characters additionally carry
 * {@link #PLACEHOLDER}; they collapse back to zero-length runs on extraction,
 * and typed text never inherits their attributes.
 *
 * <p>Edits are committed to the model (one undoable transaction per quiet
 * period) on a 400 ms debounce, on focus loss, and on {@link #commitPending()}.
 */
public class TextBlockComponent extends JTextPane {

    /** Character attribute key holding the semantic run-attribute map. */
    public static final Object RUN_ATTRS = new Object() {
        @Override
        public String toString() {
            return "authorRunAttrs";
        }
    };

    /** Marker inside a semantic map identifying placeholder (zero-length run) text. */
    public static final String PLACEHOLDER = "#placeholder";

    private static final int COMMIT_DELAY_MS = 400;

    private final BlockStylesheet stylesheet;
    private final AuthorBlock block;
    private final AuthorEditorPanel panel;
    private final Timer commitTimer;
    private boolean refreshing;

    public TextBlockComponent(AuthorBlock block, BlockStylesheet stylesheet, int depth,
            AuthorEditorPanel panel) {
        this.block = block;
        this.stylesheet = stylesheet;
        this.panel = panel;
        setEditable(panel != null);
        setOpaque(false);
        setFont(stylesheet.fontFor(block.getType(), depth));
        setForeground(stylesheet.foreground());
        refresh();

        commitTimer = new Timer(COMMIT_DELAY_MS, e -> commitPending());
        commitTimer.setRepeats(false);
        setTransferHandler(new RunsTransferHandler());

        if (panel != null) {
            installEditingBehavior();
        }
        // hover names the DITA tag of the span under the mouse
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Tooltip: the DITA tag(s) of the styled span under the mouse. */
    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        int pos = viewToModel2D(event.getPoint());
        if (pos < 0 || pos >= getDocument().getLength()) {
            return null;
        }
        Map<String, String> attrs = semanticAttrs(
                getStyledDocument().getCharacterElement(pos).getAttributes());
        if (attrs.isEmpty()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        if (attrs.containsKey(InlineStyle.DITA_INLINE)) {
            String outer = attrs.get(InlineStyle.DITA_INLINE_OUTER);
            if (outer != null) {
                for (String o : outer.split("/")) {
                    tags.add("<" + InlineStyle.chainName(o) + ">");
                }
            }
            tags.add("<" + attrs.get(InlineStyle.DITA_INLINE) + ">");
        }
        if (InlineStyle.isBold(attrs)) {
            tags.add("<b>");
        }
        if (InlineStyle.isItalic(attrs)) {
            tags.add("<i>");
        }
        if (InlineStyle.isUnderline(attrs)) {
            tags.add("<u>");
        }
        if (attrs.containsKey(InlineStyle.HREF)) {
            tags.add("<xref href=\"" + attrs.get(InlineStyle.HREF) + "\">");
        } else if (attrs.containsKey(InlineStyle.KEYREF)) {
            tags.add("keyref=\"" + attrs.get(InlineStyle.KEYREF) + "\"");
        }
        return tags.isEmpty() ? null : String.join(" ", tags);
    }

    public AuthorBlock getBlock() {
        return block;
    }

    private void installEditingBehavior() {
        ((AbstractDocument) getDocument()).setDocumentFilter(new PlaceholderSanitizer());

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                edited();
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                panel.textComponentFocused(TextBlockComponent.this);
            }

            @Override
            public void focusLost(FocusEvent e) {
                commitPending();
            }
        });

        // clicking inside a styled span selects it, so its formatting can be
        // changed or removed directly (keyboard caret movement is unaffected);
        // right-click opens the text context menu (clipboard + formatting),
        // with the block actions appended below
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1 && !e.isPopupTrigger()
                        && getSelectionStart() == getSelectionEnd()) {
                    selectSpanAt(getCaretPosition());
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowTextMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowTextMenu(e);
            }

            private void maybeShowTextMenu(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    e.consume();
                    buildTextContextMenu().show(TextBlockComponent.this, e.getX(), e.getY());
                }
            }
        });

        // Enter is a structural operation (split or move focus) — never a
        // newline, except in whitespace-preserving blocks
        if (!block.preservesSpace()) {
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "author-enter");
            getActionMap().put("author-enter", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    panel.enterPressed(TextBlockComponent.this);
                }
            });
        }

        // Backspace at the start of an empty block deletes the block
        javax.swing.Action defaultBackspace =
                getActionMap().get(javax.swing.text.DefaultEditorKit.deletePrevCharAction);
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "author-backspace");
        getActionMap().put("author-backspace", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (getCaretPosition() == 0 && getSelectionStart() == getSelectionEnd()
                        && getDocument().getLength() == 0) {
                    panel.backspaceOnEmptyBlock(TextBlockComponent.this);
                } else if (defaultBackspace != null) {
                    defaultBackspace.actionPerformed(e);
                }
            }
        });

        // Tab indents list items; elsewhere it walks between text blocks
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "author-tab");
        getActionMap().put("author-tab", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.tabPressed(TextBlockComponent.this, false);
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK), "author-shift-tab");
        getActionMap().put("author-shift-tab", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.tabPressed(TextBlockComponent.this, true);
            }
        });

        // Up on the first line and Down on the last line move between blocks;
        // Ctrl+Home/End go to the first and last text block of the document.
        javax.swing.Action defaultUp = getActionMap().get(javax.swing.text.DefaultEditorKit.upAction);
        javax.swing.Action defaultDown = getActionMap().get(javax.swing.text.DefaultEditorKit.downAction);
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "author-up");
        getActionMap().put("author-up", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (caretOnFirstLine() && panel.focusNeighbour(TextBlockComponent.this, -1)) {
                    return;
                }
                if (defaultUp != null) {
                    defaultUp.actionPerformed(e);
                }
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "author-down");
        getActionMap().put("author-down", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (caretOnLastLine() && panel.focusNeighbour(TextBlockComponent.this, 1)) {
                    return;
                }
                if (defaultDown != null) {
                    defaultDown.actionPerformed(e);
                }
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "author-document-start");
        getActionMap().put("author-document-start", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.focusDocumentEdge(TextBlockComponent.this, false);
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "author-document-end");
        getActionMap().put("author-document-end", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.focusDocumentEdge(TextBlockComponent.this, true);
            }
        });

        // Alt+Up/Down moves the block among its siblings
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,
                java.awt.event.InputEvent.ALT_DOWN_MASK), "author-move-up");
        getActionMap().put("author-move-up", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.moveBlock(TextBlockComponent.this, -1);
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
                java.awt.event.InputEvent.ALT_DOWN_MASK), "author-move-down");
        getActionMap().put("author-move-down", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.moveBlock(TextBlockComponent.this, 1);
            }
        });

        int menuMask;
        try {
            menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            menuMask = java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
        bindToggle(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask), InlineStyle.BOLD, InlineStyle.TRUE);
        bindToggle(KeyStroke.getKeyStroke(KeyEvent.VK_I, menuMask), InlineStyle.ITALIC, InlineStyle.TRUE);
        bindToggle(KeyStroke.getKeyStroke(KeyEvent.VK_U, menuMask), InlineStyle.UNDERLINE, InlineStyle.TRUE);
        bindToggle(KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask), InlineStyle.DITA_INLINE, "codeph");

        int shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask | shift), "author-copy-block",
                () -> panel.copyBlock(block));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask | shift), "author-cut-block",
                () -> panel.cutBlock(block));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask | shift), "author-paste-block",
                () -> panel.pasteBlock(block));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask), "author-duplicate-block",
                () -> panel.duplicateBlock(block));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, menuMask), "author-zoom-in",
                () -> panel.zoom(+1));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask), "author-zoom-in2",
                () -> panel.zoom(+1));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask), "author-zoom-out",
                () -> panel.zoom(-1));
        bindPanelAction(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask), "author-zoom-reset",
                () -> panel.zoom(0));

        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                menuMask | shift), "author-insert-menu");
        getActionMap().put("author-insert-menu", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                panel.showInsertMenu(TextBlockComponent.this);
            }
        });
    }

    /** Binds a key to a panel-level action on this block. */
    private void bindPanelAction(KeyStroke stroke, String name, Runnable action) {
        getInputMap().put(stroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                commitPending();
                action.run();
            }
        });
    }

    private void bindToggle(KeyStroke stroke, String key, String value) {
        String name = "author-toggle-" + key + "-" + value;
        getInputMap().put(stroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleStyle(key, value);
            }
        });
    }

    private void edited() {
        if (!refreshing && commitTimer != null) {
            commitTimer.restart();
        }
    }

    // ------------------------------------------------------------------
    // Model -> view
    // ------------------------------------------------------------------

    /** Rebuilds the styled document from the block's current runs. */
    public final void refresh() {
        refreshing = true;
        try {
            StyledDocument doc = getStyledDocument();
            doc.remove(0, doc.getLength());
            for (InlineRun run : block.getText()) {
                SimpleAttributeSet attrs = stylesheet.attributesFor(run.attrs());
                String text = run.text();
                Map<String, String> semantic = run.attrs();
                if (text.isEmpty()) {
                    String key = run.attrs().get(InlineStyle.KEYREF);
                    String resolved = key != null && panel != null ? panel.resolveKeyText(key) : null;
                    String target = key != null ? key : run.attrs().get(InlineStyle.HREF);
                    if (target == null) {
                        continue;
                    }
                    // resolved key text renders directly; unresolved targets in ⟨brackets⟩
                    text = resolved != null ? resolved : "⟨" + target + "⟩";
                    LinkedHashMap<String, String> marked = new LinkedHashMap<>(run.attrs());
                    marked.put(PLACEHOLDER, InlineStyle.TRUE);
                    semantic = marked;
                }
                attrs.addAttribute(RUN_ATTRS, Map.copyOf(semantic));
                doc.insertString(doc.getLength(), text, attrs);
            }
            if (block.preservesSpace()) {
                highlightCode();
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        } finally {
            refreshing = false;
        }
        if (commitTimer != null) {
            commitTimer.stop();
        }
    }

    /**
     * Lightweight, language-agnostic code coloring (strings, then comments on
     * top). Visual only: attributes merge without touching {@link #RUN_ATTRS},
     * so extraction is unaffected.
     */
    private void highlightCode() throws BadLocationException {
        StyledDocument doc = getStyledDocument();
        String code = doc.getText(0, doc.getLength());

        SimpleAttributeSet stringAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(stringAttrs, stylesheet.codeStringColor());
        applyPattern(doc, code, "\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'", stringAttrs);

        SimpleAttributeSet commentAttrs = new SimpleAttributeSet();
        StyleConstants.setForeground(commentAttrs, stylesheet.codeCommentColor());
        StyleConstants.setItalic(commentAttrs, true);
        applyPattern(doc, code,
                "(?m)(?://|#).*$|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->", commentAttrs);
    }

    private void applyPattern(StyledDocument doc, String code, String regex, SimpleAttributeSet attrs) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(code);
        while (m.find()) {
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), attrs, false);
        }
    }

    // ------------------------------------------------------------------
    // View -> model
    // ------------------------------------------------------------------

    /** Commits pending text edits to the model as one undoable transaction. */
    public void commitPending() {
        if (commitTimer != null) {
            commitTimer.stop();
        }
        if (panel == null || refreshing) {
            return;
        }
        List<InlineRun> runs = extractRuns();
        if (!runs.equals(block.getText())) {
            panel.commitText(this, runs);
        }
    }

    public boolean hasPendingEdits() {
        return panel != null && !extractRuns().equals(block.getText());
    }

    /**
     * The text context menu: clipboard, inline formatting for the selection,
     * clear formatting, and the block actions below a separator.
     */
    private javax.swing.JPopupMenu buildTextContextMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setLightWeightPopupEnabled(false);
        boolean hasSelection = getSelectionStart() != getSelectionEnd();

        menu.add(editAction(javax.swing.text.DefaultEditorKit.cutAction, "Cut", hasSelection));
        menu.add(editAction(javax.swing.text.DefaultEditorKit.copyAction, "Copy", hasSelection));
        menu.add(editAction(javax.swing.text.DefaultEditorKit.pasteAction, "Paste", true));
        menu.addSeparator();

        addFormatItem(menu, "Bold", InlineStyle.BOLD, InlineStyle.TRUE, hasSelection);
        addFormatItem(menu, "Italic", InlineStyle.ITALIC, InlineStyle.TRUE, hasSelection);
        addFormatItem(menu, "Underline", InlineStyle.UNDERLINE, InlineStyle.TRUE, hasSelection);
        javax.swing.JMenu semantic = new javax.swing.JMenu("DITA Inline");
        String[][] groups = {
            {"codeph", "uicontrol", "filepath", "varname", "keyword", "term"},
            {"wintitle", "menucascade", "shortcut", "cmdname", "option", "parmname", "apiname", "userinput",
                "systemoutput", "msgph", "synph"},
            {"q", "cite", "sup", "sub", "tt", "line-through", "overline", "tm", "fn", "indexterm"}};
        for (int g = 0; g < groups.length; g++) {
            if (g > 0) {
                semantic.addSeparator();
            }
            for (String name : groups[g]) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem("<" + name + ">");
                item.setEnabled(hasSelection);
                item.addActionListener(e -> toggleStyle(InlineStyle.DITA_INLINE, name));
                semantic.add(item);
            }
        }
        menu.add(semantic);
        javax.swing.JMenuItem clear = new javax.swing.JMenuItem("Clear Formatting");
        clear.setEnabled(hasSelection);
        clear.addActionListener(e -> clearFormatting());
        menu.add(clear);

        if (panel != null && block.getParent() != null) {
            menu.addSeparator();
            InsertMenu.fillBlockActions(panel, menu, block);
        }
        return menu;
    }

    private javax.swing.JMenuItem editAction(String actionName, String label, boolean enabled) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        javax.swing.Action action = getActionMap().get(actionName);
        item.setEnabled(enabled && action != null);
        if (action != null) {
            item.addActionListener(e -> action.actionPerformed(
                    new java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, actionName)));
        }
        return item;
    }

    private void addFormatItem(javax.swing.JPopupMenu menu, String label,
            String key, String value, boolean enabled) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        item.setEnabled(enabled);
        item.addActionListener(e -> toggleStyle(key, value));
        menu.add(item);
    }

    /**
     * Strips styling (bold/italic/underline/semantic inline) from the
     * selection; reference semantics (href/keyref) are kept intact.
     */
    public void clearFormatting() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (panel == null || start == end) {
            return;
        }
        StyledDocument doc = getStyledDocument();
        for (CharSpan span : spans()) {
            int from = Math.max(span.start(), start);
            int to = Math.min(span.end(), end);
            if (from >= to || span.isPlaceholder()) {
                continue;
            }
            LinkedHashMap<String, String> attrs = new LinkedHashMap<>(span.attrs());
            attrs.remove(InlineStyle.BOLD);
            attrs.remove(InlineStyle.ITALIC);
            attrs.remove(InlineStyle.UNDERLINE);
            attrs.remove(InlineStyle.DITA_INLINE);
            attrs.remove(InlineStyle.DITA_INLINE_OUTER);
            // an element's own attributes go with it, except a key reference, which is a link
            String innerKeyref = null;
            for (var e : attrs.entrySet()) {
                if (e.getKey().endsWith(InlineStyle.INLINE_ATTR_SEPARATOR + "keyref") && innerKeyref == null) {
                    innerKeyref = e.getValue();
                }
            }
            attrs.keySet().removeIf(k -> k.contains(InlineStyle.INLINE_ATTR_SEPARATOR) && !k.startsWith("xref:"));
            if (innerKeyref != null && !attrs.containsKey(InlineStyle.KEYREF)) {
                attrs.put(InlineStyle.KEYREF, innerKeyref);
            }
            SimpleAttributeSet visual = stylesheet.attributesFor(attrs);
            visual.addAttribute(RUN_ATTRS, Map.copyOf(attrs));
            refreshing = true;
            try {
                doc.setCharacterAttributes(from, to - from, visual, true);
            } finally {
                refreshing = false;
            }
        }
        commitPending();
        select(start, end);
    }

    /** Selects the styled span containing {@code pos}; no-op on plain text. */
    void selectSpanAt(int pos) {
        for (CharSpan span : spans()) {
            if (pos >= span.start() && pos < span.end()) {
                if (!span.attrs().isEmpty()) {
                    select(span.start(), span.end());
                }
                return;
            }
        }
    }

    /** One contiguous range of characters with a uniform semantic map. */
    record CharSpan(int start, int end, Map<String, String> attrs) {

        boolean isPlaceholder() {
            return InlineStyle.TRUE.equals(attrs.get(PLACEHOLDER));
        }
    }

    List<CharSpan> spans() {
        StyledDocument doc = getStyledDocument();
        List<CharSpan> spans = new ArrayList<>();
        int length = doc.getLength();
        int i = 0;
        while (i < length) {
            Element el = doc.getCharacterElement(i);
            int end = Math.min(el.getEndOffset(), length);
            Map<String, String> attrs = semanticAttrs(el.getAttributes());
            if (!spans.isEmpty() && spans.get(spans.size() - 1).attrs().equals(attrs)) {
                CharSpan prev = spans.remove(spans.size() - 1);
                spans.add(new CharSpan(prev.start(), end, attrs));
            } else {
                spans.add(new CharSpan(i, end, attrs));
            }
            i = end;
        }
        return spans;
    }

    /** Line breaks with their surrounding indentation, folded to one space on paste into prose. */
    private static final java.util.regex.Pattern LINE_BREAKS = java.util.regex.Pattern.compile("\\s*\\R\\s*");

    private static String foldLineBreaks(String text) {
        return text.indexOf('\n') < 0 && text.indexOf('\r') < 0 ? text : LINE_BREAKS.matcher(text).replaceAll(" ");
    }

    /** Clipboard flavor carrying {@code List<InlineRun>} within this JVM, beside plain text. */
    static final java.awt.datatransfer.DataFlavor RUNS_FLAVOR;
    static {
        try {
            RUNS_FLAVOR = new java.awt.datatransfer.DataFlavor(
                    java.awt.datatransfer.DataFlavor.javaJVMLocalObjectMimeType + ";class=java.util.List");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cut, copy and paste keep inline markup: the selection travels as runs,
     * with plain text for other applications. A plain-text paste into a
     * block that does not preserve space folds its line breaks into spaces.
     */
    private final class RunsTransferHandler extends javax.swing.TransferHandler {
        @Override
        public int getSourceActions(javax.swing.JComponent c) {
            return isEditable() ? COPY_OR_MOVE : COPY;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            if (start == end) {
                return null;
            }
            return new RunsTransferable(extractRunsRange(start, end), getSelectedText());
        }

        @Override
        protected void exportDone(javax.swing.JComponent c, java.awt.datatransfer.Transferable data, int action) {
            if (action == MOVE && isEditable()) {
                replaceSelection("");
            }
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return isEditable() && (support.isDataFlavorSupported(RUNS_FLAVOR)
                    || support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor));
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                if (support.isDataFlavorSupported(RUNS_FLAVOR)
                        && (panel == null || panel.getAdapter() == null || panel.getAdapter().supportsInlineStyles())) {
                    List<InlineRun> runs = (List<InlineRun>) support.getTransferable().getTransferData(RUNS_FLAVOR);
                    insertRuns(runs);
                    return true;
                }
                String text = (String) support.getTransferable().getTransferData(
                        java.awt.datatransfer.DataFlavor.stringFlavor);
                if (!block.preservesSpace()) {
                    text = foldLineBreaks(text);
                }
                replaceSelection(text);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** The selection as runs plus plain text. */
    static final class RunsTransferable implements java.awt.datatransfer.Transferable {
        private final List<InlineRun> runs;
        private final String text;

        RunsTransferable(List<InlineRun> runs, String text) {
            this.runs = List.copyOf(runs);
            this.text = text == null ? "" : text;
        }

        @Override
        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[] {RUNS_FLAVOR, java.awt.datatransfer.DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
            return RUNS_FLAVOR.equals(flavor) || java.awt.datatransfer.DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                throws java.awt.datatransfer.UnsupportedFlavorException {
            if (RUNS_FLAVOR.equals(flavor)) {
                return runs;
            }
            if (java.awt.datatransfer.DataFlavor.stringFlavor.equals(flavor)) {
                return text;
            }
            throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
        }
    }

    /**
     * Inserts runs at the selection, replacing it, with their styling. A
     * reference run (keyref or xref without text) is pasted as its display
     * text, since the placeholder mechanics belong to the model refresh.
     */
    public void insertRuns(List<InlineRun> runs) {
        StyledDocument doc = getStyledDocument();
        int start = getSelectionStart();
        int end = getSelectionEnd();
        refreshing = true;
        try {
            if (end > start) {
                doc.remove(start, end - start);
            }
            int at = start;
            for (InlineRun run : runs) {
                String text = run.text();
                Map<String, String> attrs = run.attrs();
                if (text.isEmpty()) {
                    String key = attrs.get(InlineStyle.KEYREF);
                    String resolved = key != null && panel != null ? panel.resolveKeyText(key) : null;
                    String target = key != null ? key : attrs.get(InlineStyle.HREF);
                    if (target == null) {
                        continue;
                    }
                    text = resolved != null ? resolved : target;
                    LinkedHashMap<String, String> plain = new LinkedHashMap<>(attrs);
                    plain.remove(InlineStyle.KEYREF);
                    plain.remove(InlineStyle.HREF);
                    plain.remove(InlineStyle.SCOPE);
                    attrs = plain;
                }
                if (!block.preservesSpace()) {
                    text = foldLineBreaks(text);
                }
                SimpleAttributeSet visual = stylesheet.attributesFor(attrs);
                visual.addAttribute(RUN_ATTRS, Map.copyOf(attrs));
                doc.insertString(at, text, visual);
                at += text.length();
            }
            setCaretPosition(at);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        } finally {
            refreshing = false;
        }
        commitPending();
    }

    /** Sets link attributes over the selection (a link inserted from the dialog). */
    public void applyLink(String href, String keyref, String scope) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start == end) {
            String shown = href != null && !href.isBlank() ? href : keyref;
            if (shown == null || shown.isBlank()) {
                return;
            }
            replaceSelection(shown);
            end = getCaretPosition();
            start = end - shown.length();
        }
        StyledDocument doc = getStyledDocument();
        for (CharSpan span : spans()) {
            int from = Math.max(span.start(), start);
            int to = Math.min(span.end(), end);
            if (from >= to || span.isPlaceholder()) {
                continue;
            }
            LinkedHashMap<String, String> attrs = new LinkedHashMap<>(span.attrs());
            attrs.remove(InlineStyle.HREF);
            attrs.remove(InlineStyle.KEYREF);
            attrs.remove(InlineStyle.SCOPE);
            if (href != null && !href.isBlank()) {
                attrs.put(InlineStyle.HREF, href.strip());
            }
            if (keyref != null && !keyref.isBlank()) {
                attrs.put(InlineStyle.KEYREF, keyref.strip());
            }
            if (scope != null && !scope.isBlank()) {
                attrs.put(InlineStyle.SCOPE, scope.strip());
            }
            SimpleAttributeSet visual = stylesheet.attributesFor(attrs);
            visual.addAttribute(RUN_ATTRS, Map.copyOf(attrs));
            refreshing = true;
            try {
                doc.setCharacterAttributes(from, to - from, visual, true);
            } finally {
                refreshing = false;
            }
        }
        commitPending();
        select(start, end);
    }

    /** Whether the caret sits on the first visual line of the block. */
    boolean caretOnFirstLine() {
        try {
            return javax.swing.text.Utilities.getRowStart(this, getCaretPosition()) == 0;
        } catch (BadLocationException e) {
            return true;
        }
    }

    /** Whether the caret sits on the last visual line of the block. */
    boolean caretOnLastLine() {
        try {
            return javax.swing.text.Utilities.getRowEnd(this, getCaretPosition()) >= getDocument().getLength();
        } catch (BadLocationException e) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> semanticAttrs(AttributeSet attrs) {
        Object value = attrs.getAttribute(RUN_ATTRS);
        return value instanceof Map ? (Map<String, String>) value : Map.of();
    }

    /**
     * Extracts the semantic runs of a view-offset range. Placeholders that
     * overlap the range are emitted whole (they are atomic references).
     */
    public List<InlineRun> extractRunsRange(int from, int to) {
        List<InlineRun> runs = new ArrayList<>();
        try {
            for (CharSpan span : spans()) {
                int start = Math.max(span.start(), from);
                int end = Math.min(span.end(), to);
                if (start >= end) {
                    continue;
                }
                if (span.isPlaceholder()) {
                    LinkedHashMap<String, String> attrs = new LinkedHashMap<>(span.attrs());
                    attrs.remove(PLACEHOLDER);
                    InlineRun reference = new InlineRun("", attrs);
                    if (runs.isEmpty() || !runs.get(runs.size() - 1).equals(reference)) {
                        runs.add(reference);
                    }
                } else {
                    runs.add(new InlineRun(
                            getStyledDocument().getText(start, end - start), span.attrs()));
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return runs;
    }

    /** Extracts the semantic runs currently in the view. */
    public List<InlineRun> extractRuns() {
        List<InlineRun> runs = new ArrayList<>();
        try {
            for (CharSpan span : spans()) {
                if (span.isPlaceholder()) {
                    LinkedHashMap<String, String> attrs = new LinkedHashMap<>(span.attrs());
                    attrs.remove(PLACEHOLDER);
                    InlineRun reference = new InlineRun("", attrs);
                    if (runs.isEmpty() || !runs.get(runs.size() - 1).equals(reference)) {
                        runs.add(reference);
                    }
                } else {
                    String text = getStyledDocument().getText(span.start(), span.end() - span.start());
                    if (!runs.isEmpty()) {
                        InlineRun prev = runs.get(runs.size() - 1);
                        if (!prev.text().isEmpty() && prev.attrs().equals(span.attrs())) {
                            runs.set(runs.size() - 1, new InlineRun(prev.text() + text, span.attrs()));
                            continue;
                        }
                    }
                    runs.add(new InlineRun(text, span.attrs()));
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return runs;
    }

    /**
     * Toggles an inline style over the current selection and commits
     * immediately. With no selection this is a no-op.
     */
    public void toggleStyle(String key, String value) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (panel == null || start == end) {
            return;
        }
        // present everywhere in the selection -> remove; otherwise apply
        boolean everywhere = true;
        for (CharSpan span : spans()) {
            if (span.start() < end && span.end() > start && !span.isPlaceholder()
                    && !value.equals(span.attrs().get(key))) {
                everywhere = false;
                break;
            }
        }
        StyledDocument doc = getStyledDocument();
        for (CharSpan span : spans()) {
            int from = Math.max(span.start(), start);
            int to = Math.min(span.end(), end);
            if (from >= to || span.isPlaceholder()) {
                continue;
            }
            LinkedHashMap<String, String> attrs = new LinkedHashMap<>(span.attrs());
            if (everywhere) {
                String removed = attrs.remove(key);
                if (InlineStyle.DITA_INLINE.equals(key)) {
                    // the element's own attributes go with it; an outer chain moves in
                    if (removed != null) {
                        attrs.keySet().removeIf(k -> k.startsWith(removed + InlineStyle.INLINE_ATTR_SEPARATOR));
                    }
                    String outer = attrs.remove(InlineStyle.DITA_INLINE_OUTER);
                    if (outer != null) {
                        int slash = outer.lastIndexOf('/');
                        attrs.put(InlineStyle.DITA_INLINE,
                                InlineStyle.chainName(slash < 0 ? outer : outer.substring(slash + 1)));
                        if (slash >= 0) {
                            attrs.put(InlineStyle.DITA_INLINE_OUTER, outer.substring(0, slash));
                        }
                    }
                }
            } else {
                String previous = attrs.put(key, value);
                if (InlineStyle.DITA_INLINE.equals(key) && previous != null && !previous.equals(value)) {
                    // the replaced element's own attributes do not belong to the new one
                    attrs.keySet().removeIf(k -> k.startsWith(previous + InlineStyle.INLINE_ATTR_SEPARATOR));
                }
            }
            if (everywhere && !InlineStyle.DITA_INLINE.equals(key)) {
                attrs.keySet().removeIf(k -> k.startsWith(key + InlineStyle.INLINE_ATTR_SEPARATOR));
            }
            SimpleAttributeSet visual = stylesheet.attributesFor(attrs);
            visual.addAttribute(RUN_ATTRS, Map.copyOf(attrs));
            refreshing = true;
            try {
                doc.setCharacterAttributes(from, to - from, visual, true);
            } finally {
                refreshing = false;
            }
        }
        commitPending();
        select(start, end);
    }

    /**
     * Strips reference/placeholder semantics from attributes of typed text.
     * Programmatic inserts (a model refresh, a paste of runs) run with
     * {@code refreshing} set and pass through untouched.
     */
    private final class PlaceholderSanitizer extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attrs)
                throws BadLocationException {
            fb.insertString(offset, string, sanitize(attrs));
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            fb.replace(offset, length, text, sanitize(attrs));
        }

        private AttributeSet sanitize(AttributeSet attrs) {
            if (attrs == null || refreshing) {
                return attrs;
            }
            Map<String, String> semantic = semanticAttrs(attrs);
            if (!semantic.containsKey(PLACEHOLDER)
                    && !semantic.containsKey(InlineStyle.KEYREF)
                    && !semantic.containsKey(InlineStyle.HREF)) {
                return attrs;
            }
            // typed text must not extend a reference: keep plain styling
            SimpleAttributeSet clean = new SimpleAttributeSet();
            return clean;
        }
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        // called during superclass UI install, before the document exists
        StyledDocument styledDoc = getStyledDocument();
        if (styledDoc == null) {
            return;
        }
        // keep the paragraph's base attributes in sync so runs inherit the block font
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setFontFamily(base, font.getFamily());
        StyleConstants.setFontSize(base, font.getSize());
        StyleConstants.setBold(base, font.isBold());
        StyleConstants.setItalic(base, font.isItalic());
        styledDoc.setParagraphAttributes(0, Math.max(1, styledDoc.getLength()), base, false);
        setParagraphAttributesForFuture(base);
    }

    private void setParagraphAttributesForFuture(SimpleAttributeSet base) {
        if (getStyledDocument() instanceof javax.swing.text.DefaultStyledDocument styled) {
            javax.swing.text.Style defaultStyle = styled.getStyle(javax.swing.text.StyleContext.DEFAULT_STYLE);
            if (defaultStyle != null) {
                defaultStyle.addAttributes(base);
            }
        }
    }

    /**
     * Called when the component is discarded by a rebuild: pending text is
     * committed while the block is still live and the commit timer is stopped
     * so it cannot fire against a stale block.
     */
    public void dispose() {
        if (commitTimer != null && commitTimer.isRunning()) {
            commitTimer.stop();
            commitPending();
        }
    }

    /** Prevents vertical stretching inside the block stack. */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
