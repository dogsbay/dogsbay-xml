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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dom4j.Element;

import com.dogsbay.xml.author.adapter.BlockAdapter;
import com.dogsbay.xml.author.adapter.DitaBlockAdapter;
import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.AuthorDocumentEvent;
import com.dogsbay.xml.author.model.AuthorDocumentListener;
import com.dogsbay.xml.author.model.BlockType;

/**
 * The WYSIWYG author editing surface: a vertical stack of styled block
 * components rendered from an {@link AuthorDocument}, with a breadcrumb bar
 * and block selection.
 *
 * <p>Self-contained by design (the standalone rule): hosts hand it content via
 * {@link #setContent(Element)} or {@link #setAuthorDocument(AuthorDocument)}
 * and read it back through {@link #getAuthorDocument()} / the adapter — no
 * application types appear in this API.
 */
public class AuthorEditorPanel extends JPanel {

    private BlockAdapter adapter;
    private BlockStylesheet stylesheet;
    private final JPanel blocksPanel;
    private final BreadcrumbBar breadcrumb;
    private final FormatToolbar formatToolbar;
    private final List<Consumer<AuthorBlock>> selectionListeners = new ArrayList<>();
    private final Map<String, BlockView> viewsById = new HashMap<>();
    private final Map<String, TextBlockComponent> textComponentsById = new HashMap<>();
    private final javax.swing.undo.UndoableEditSupport undoSupport =
            new javax.swing.undo.UndoableEditSupport(this);

    private AuthorDocument authorDocument;
    private AuthorBlock selectedBlock;
    private TextBlockComponent focusedText;
    private String suppressRefreshBlockId;
    private ValidationSummaryPanel validationSummary;
    private AuthorOutlinePanel outline;
    private JScrollPane contentScroll;
    private final com.dogsbay.xml.author.validation.AuthorValidator validator =
            new com.dogsbay.xml.author.validation.AuthorValidator();
    private Map<String, List<com.dogsbay.xml.author.validation.ValidationIssue>> issuesByBlock = Map.of();
    private com.dogsbay.xml.author.spi.ReferenceResolver references =
            com.dogsbay.xml.author.spi.ReferenceResolver.NONE;
    private final Map<java.net.URI, javax.swing.ImageIcon> imageCache = new HashMap<>();

    private final AuthorDocumentListener documentListener = e -> {
        // suppression must be decided synchronously, while the committing
        // transaction is still on the stack
        if (e.kind() == AuthorDocumentEvent.Kind.TEXT && e.block() != null
                && e.block().getId().equals(suppressRefreshBlockId)) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            onDocumentChanged(e);
        } else {
            SwingUtilities.invokeLater(() -> onDocumentChanged(e));
        }
    };

    public AuthorEditorPanel() {
        this(new DitaBlockAdapter());
    }

    public AuthorEditorPanel(BlockAdapter adapter) {
        super(new BorderLayout());
        this.adapter = adapter;
        this.stylesheet = new BlockStylesheet();

        breadcrumb = new BreadcrumbBar(stylesheet, this::setSelectedBlock);
        formatToolbar = new FormatToolbar(this);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(breadcrumb, BorderLayout.CENTER);
        north.add(formatToolbar, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        blocksPanel = new JPanel();
        blocksPanel.setLayout(new VerticalStackLayout());
        blocksPanel.setBorder(new EmptyBorder(12, 16, 24, 16));
        blocksPanel.setBackground(stylesheet.background());

        ScrollableWidthPanel viewport = new ScrollableWidthPanel();
        viewport.setBackground(stylesheet.background());
        viewport.add(blocksPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(viewport);
        contentScroll = scroll;
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        // no blit scrolling: the stack's heights settle a revalidate after
        // edits, and blitted pixel copies of in-flux geometry smear fragments
        // of other blocks into the text (full repaint per scroll is cheap here)
        scroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);

        outline = new AuthorOutlinePanel(this);
        outline.setPreferredSize(new Dimension(230, 100));
        outline.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                stylesheet.gutterForeground()));
        addSelectionListener(outline::showSelection);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        center.add(outline, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        validationSummary = new ValidationSummaryPanel(this);
        validationSummary.setAlignmentX(LEFT_ALIGNMENT);
        south.add(validationSummary);
        AttributePanel attributes = new AttributePanel(this);
        attributes.setAlignmentX(LEFT_ALIGNMENT);
        addSelectionListener(attributes::showBlock);
        south.add(attributes);
        add(south, BorderLayout.SOUTH);
    }

    /** Sets or removes ({@code value == null}) one attribute as an undoable edit. */
    public void setBlockAttribute(AuthorBlock block, String name, String value) {
        if (authorDocument == null || java.util.Objects.equals(block.getAttribute(name), value)) {
            return;
        }
        com.dogsbay.xml.author.model.Transaction tx = authorDocument.begin(
                value == null ? "Remove attribute" : "Set attribute");
        tx.setAttribute(block, name, value);
        undoSupport.postEdit(tx.commit());
    }

    public BlockAdapter getAdapter() {
        return adapter;
    }

    /** Switches the format adapter (DITA / generic XML); clears current content. */
    public void setAdapter(BlockAdapter adapter) {
        if (adapter == null || adapter == this.adapter) {
            return;
        }
        if (this.adapter != null && adapter.getClass() == this.adapter.getClass()) {
            this.adapter = adapter;   // same format, fresh instance: the content stays
            return;
        }
        setAuthorDocument(null);
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    /** Imports a parsed XML root and renders it. */
    public void setContent(Element root) {
        setAuthorDocument(root == null ? null : adapter.importDocument(root));
    }

    public void setAuthorDocument(AuthorDocument document) {
        // A replacement (the split view's mirror refresh, an external reload) keeps
        // the reader's place and selection; only a first open starts at the top.
        boolean replacing = authorDocument != null && document != null;
        java.awt.Point view = replacing && contentScroll != null
                ? contentScroll.getViewport().getViewPosition() : new java.awt.Point(0, 0);
        if (authorDocument != null) {
            authorDocument.removeListener(documentListener);
        }
        authorDocument = document;
        if (!replacing) {
            selectedBlock = null;   // else rebuild() re-resolves the selection by id
        }
        if (document != null) {
            document.addListener(documentListener);
        }
        rebuild();
        for (Consumer<AuthorBlock> listener : selectionListeners) {
            listener.accept(selectedBlock);   // the outline and attribute panels show the new document
        }
        SwingUtilities.invokeLater(() -> {
            if (contentScroll == null) {
                return;
            }
            contentScroll.getViewport().setViewPosition(view);
            // Keeping the place must not hide the selection: a programmatic edit
            // focuses a block and the re-import that follows would scroll away
            // from it. The split mirror's selection is on screen anyway.
            BlockView selectedView = selectedBlock == null ? null : viewsById.get(selectedBlock.getId());
            if (selectedView != null && !isInViewport(selectedView)) {
                scrollTo(selectedView);
            }
        });
        validator.attach(document, issues -> {
            if (SwingUtilities.isEventDispatchThread()) {
                showIssues(issues);
            } else {
                SwingUtilities.invokeLater(() -> showIssues(issues));
            }
        });
    }

    /** Sets the reference resolver (keys, hrefs, conrefs) and re-renders. */
    public void setReferenceResolver(com.dogsbay.xml.author.spi.ReferenceResolver resolver) {
        this.references = resolver == null
                ? com.dogsbay.xml.author.spi.ReferenceResolver.NONE : resolver;
        imageCache.clear();
        if (authorDocument != null) {
            rebuild();
        }
    }

    public com.dogsbay.xml.author.spi.ReferenceResolver getReferenceResolver() {
        return references;
    }

    /** Resolved display text for a key, or null (used by placeholders). */
    String resolveKeyText(String key) {
        return references.keyText(key);
    }

    /** Loads (and caches) a scaled image for display, or null. */
    javax.swing.ImageIcon imageFor(java.net.URI uri) {
        if (uri == null) {
            return null;
        }
        return imageCache.computeIfAbsent(uri, u -> {
            try {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(u));
                if (image == null) {
                    return null;
                }
                int maxWidth = 480;
                if (image.getWidth() > maxWidth) {
                    int h = image.getHeight() * maxWidth / image.getWidth();
                    return new javax.swing.ImageIcon(
                            image.getScaledInstance(maxWidth, h, java.awt.Image.SCALE_SMOOTH));
                }
                return new javax.swing.ImageIcon(image);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Current validation issues, in document order (visible for tests/commands). */
    public List<com.dogsbay.xml.author.validation.ValidationIssue> getValidationIssues() {
        return validator.validate(authorDocument);
    }

    private void showIssues(List<com.dogsbay.xml.author.validation.ValidationIssue> issues) {
        Map<String, List<com.dogsbay.xml.author.validation.ValidationIssue>> byBlock = new HashMap<>();
        for (var issue : issues) {
            byBlock.computeIfAbsent(issue.block().getId(), k -> new ArrayList<>()).add(issue);
        }
        issuesByBlock = byBlock;
        for (Map.Entry<String, BlockView> entry : viewsById.entrySet()) {
            entry.getValue().setIssues(byBlock.getOrDefault(entry.getKey(), List.of()));
        }
        if (validationSummary != null) {
            validationSummary.showIssues(issues);
        }
        if (outline != null) {
            outline.setIssues(byBlock);
        }
    }

    public AuthorDocument getAuthorDocument() {
        return authorDocument;
    }

    /** Exports the current block tree back to a detached element tree. */
    public Element exportContent() {
        commitPendingEdits();
        return authorDocument == null ? null : adapter.exportDocument(authorDocument);
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    /** Receives one UndoableEdit per committed editing transaction. */
    public void addUndoableEditListener(javax.swing.event.UndoableEditListener listener) {
        undoSupport.addUndoableEditListener(listener);
    }

    public void removeUndoableEditListener(javax.swing.event.UndoableEditListener listener) {
        undoSupport.removeUndoableEditListener(listener);
    }

    /** Flushes uncommitted text edits from all text components into the model. */
    public void commitPendingEdits() {
        for (TextBlockComponent text : List.copyOf(textComponentsById.values())) {
            text.commitPending();
        }
    }

    /** Called by a text component to commit its current runs as one transaction. */
    void commitText(TextBlockComponent source, List<com.dogsbay.xml.author.model.InlineRun> runs) {
        if (authorDocument == null) {
            return;
        }
        com.dogsbay.xml.author.model.Transaction tx = authorDocument.begin("Edit text");
        suppressRefreshBlockId = source.getBlock().getId();
        try {
            tx.setText(source.getBlock(), runs);
            undoSupport.postEdit(tx.commit());
        } finally {
            suppressRefreshBlockId = null;
        }
    }

    void textComponentFocused(TextBlockComponent component) {
        focusedText = component;
        setSelectedBlock(component.getBlock());
    }

    // ------------------------------------------------------------------
    // Structural operations
    // ------------------------------------------------------------------

    /** Up or Down at the block's edge: focus the neighbouring text block; false when there is none. */
    boolean focusNeighbour(TextBlockComponent component, int direction) {
        AuthorBlock target = component.getBlock();
        TextBlockComponent text = null;
        while (text == null) {   // a text block may have no component (a collapsed or void view): keep going
            target = direction < 0
                    ? com.dogsbay.xml.author.model.BlockOperations.previousTextBlock(authorDocument, target)
                    : com.dogsbay.xml.author.model.BlockOperations.nextTextBlock(authorDocument, target);
            if (target == null) {
                return false;
            }
            text = textComponentsById.get(target.getId());
        }
        component.commitPending();
        focusBlock(target, direction < 0 ? text.getDocument().getLength() : 0);
        return true;
    }

    /** Ctrl+Home / Ctrl+End: the first or last text block of the document. */
    void focusDocumentEdge(TextBlockComponent component, boolean end) {
        if (authorDocument == null || authorDocument.getRoot() == null) {
            return;
        }
        component.commitPending();
        AuthorBlock target = end
                ? com.dogsbay.xml.author.model.BlockOperations.lastTextBlock(authorDocument)
                : com.dogsbay.xml.author.model.BlockOperations.firstTextBlock(authorDocument.getRoot());
        if (target != null) {
            TextBlockComponent text = textComponentsById.get(target.getId());
            focusBlock(target, end && text != null ? text.getDocument().getLength() : 0);
        }
    }

    /** Enter: split the block at the caret, or move focus to the next block. */
    void enterPressed(TextBlockComponent component) {
        AuthorBlock block = component.getBlock();
        if (isListItem(block) && component.getDocument().getLength() == 0) {
            component.commitPending();
            var left = com.dogsbay.xml.author.model.BlockOperations.exitList(authorDocument, block);
            if (left != null) {
                apply(left, 0);   // an empty last item leaves the list
                return;
            }
        }
        if (block.getType().isSplitOnEnter() && block.getParent() != null) {
            int caret = component.getCaretPosition();
            int length = component.getDocument().getLength();
            var before = component.extractRunsRange(0, caret);
            var after = component.extractRunsRange(caret, length);
            guarded(() -> apply(com.dogsbay.xml.author.model.BlockOperations.split(
                    authorDocument, block, before, after), 0));
        } else {
            component.commitPending();
            focusBlock(com.dogsbay.xml.author.model.BlockOperations.nextTextBlock(
                    authorDocument, block), 0);
        }
    }

    /** Backspace at the start of an empty block: delete it, focus the previous one. */
    void backspaceOnEmptyBlock(TextBlockComponent component) {
        AuthorBlock block = component.getBlock();
        if (!block.getChildren().isEmpty()) {
            return;
        }
        apply(com.dogsbay.xml.author.model.BlockOperations.deleteEmptyBlock(authorDocument, block),
                Integer.MAX_VALUE);
    }

    private static boolean isListItem(AuthorBlock block) {
        return com.dogsbay.xml.author.model.BlockOperations.isListItem(block);
    }

    /** Insert Link: xref href or keyref over the focused block's selection. */
    public void insertLink() {
        if (focusedText == null || !adapter.supportsInlineStyles()) {
            return;
        }
        javax.swing.JTextField href = new javax.swing.JTextField(28);
        javax.swing.JTextField keyref = new javax.swing.JTextField(16);
        javax.swing.JComboBox<String> scope = new javax.swing.JComboBox<>(new String[] {"", "local", "peer", "external"});
        String selected = focusedText.getSelectedText();
        if (selected != null && (selected.startsWith("http://") || selected.startsWith("https://"))) {
            href.setText(selected.strip());
            scope.setSelectedItem("external");
        }
        javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(0, 2, 6, 4));
        form.add(new JLabel("Target (href):"));
        form.add(href);
        form.add(new JLabel("or key (keyref):"));
        form.add(keyref);
        form.add(new JLabel("Scope:"));
        form.add(scope);
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, form, "Insert Link",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        if (href.getText().isBlank() && keyref.getText().isBlank()) {
            return;
        }
        focusedText.applyLink(href.getText(), keyref.getText(), (String) scope.getSelectedItem());
        focusedText.requestFocusInWindow();
    }

    /** Set an image block's source from a file chooser, stored relative to the document when possible. */
    public void chooseImageSource(AuthorBlock image) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Image file");
        java.net.URI base = references.resolveHref(".");
        if (base != null && "file".equals(base.getScheme())) {
            chooser.setCurrentDirectory(new java.io.File(base));
        }
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images", "png", "jpg", "jpeg", "gif", "svg", "webp"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File chosen = chooser.getSelectedFile();
        String href = chosen.getAbsolutePath().replace(java.io.File.separatorChar, '/');
        if (base != null && "file".equals(base.getScheme())) {
            try {
                java.nio.file.Path from = java.nio.file.Path.of(base).toAbsolutePath().normalize();
                href = from.relativize(chosen.toPath().toAbsolutePath().normalize()).toString()
                        .replace(java.io.File.separatorChar, '/');
            } catch (IllegalArgumentException differentRoot) {
                // different drive: keep the absolute path
            }
        }
        setBlockAttribute(image, "href", href);
    }

    /** Tab: indent/outdent list items; navigate between blocks elsewhere. */
    void tabPressed(TextBlockComponent component, boolean shift) {
        AuthorBlock block = component.getBlock();
        if ("li".equals(block.getType().getName())) {
            component.commitPending();
            guarded(() -> apply(shift
                    ? com.dogsbay.xml.author.model.BlockOperations.outdentListItem(authorDocument, block)
                    : com.dogsbay.xml.author.model.BlockOperations.indentListItem(authorDocument, block), 0));
        } else {
            focusNeighbour(component, shift ? -1 : 1);
        }
    }

    /** Alt+Up/Down: move the block among its siblings. */
    void moveBlock(TextBlockComponent component, int delta) {
        component.commitPending();
        guarded(() -> apply(com.dogsbay.xml.author.model.BlockOperations.move(
                authorDocument, component.getBlock(), delta), 0));
    }

    /**
     * Runs a structural edit; a content-model refusal is shown in the status
     * line instead of escaping to the event thread.
     */
    private boolean guarded(Runnable edit) {
        try {
            edit.run();
            return true;
        } catch (com.dogsbay.xml.author.model.AuthorStructureException | IllegalArgumentException e) {
            showStructureError(e.getMessage());
            return false;
        }
    }

    private void showStructureError(String message) {
        String text = message == null || message.isBlank() ? "That edit is not allowed here." : message;
        breadcrumb.showMessage(text);
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    /** Performs a structural insert (from the insert/context menu). */
    void insertBlock(String typeName, AuthorBlock parent, int index) {
        commitPendingEdits();
        var result = new com.dogsbay.xml.author.model.BlockOperations.Result[1];
        if (!guarded(() -> result[0] = com.dogsbay.xml.author.model.BlockOperations.insertBlock(
                authorDocument, typeName, parent, index))) {
            return;
        }
        apply(result[0], 0);
        if ("image".equals(typeName) && result[0] != null && result[0].focus() != null
                && "image".equals(result[0].focus().getType().getName())) {
            chooseImageSource(result[0].focus());   // a fresh image wants its file straight away
        }
    }

    // ------------------------------------------------------------------
    // Block clipboard, duplication and list conversion
    // ------------------------------------------------------------------

    /** Puts a block's XML on the system clipboard, so it also pastes into the XML editor. */
    public boolean copyBlock(AuthorBlock block) {
        if (block == null || authorDocument == null) {
            return false;
        }
        commitPendingEdits();
        org.dom4j.Element element = adapter.exportFragment(block);
        if (element == null) {
            return false;
        }
        com.dogsbay.xml.author.adapter.BlockXmlFormatter.indent(element, "  ");
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(element.asXML()), null);
        } catch (RuntimeException clipboardUnavailable) {
            // another application owns the clipboard, or there is no display
            showStructureError("The clipboard is not available.");
            return false;
        }
        return true;
    }

    /** Copies the block, then deletes it. */
    public boolean cutBlock(AuthorBlock block) {
        if (block == null || !block.getType().isDeletable() || block.getParent() == null) {
            showStructureError(block == null ? "Nothing to cut."
                    : block.getType().getLabel() + " cannot be cut.");
            return false;   // check first: a refused cut must not clobber the clipboard
        }
        if (!copyBlock(block)) {
            return false;
        }
        deleteBlock(block);
        return true;
    }

    /**
     * Parses XML from the clipboard and inserts it after the given block (or
     * inside it when only that fits). False when the clipboard holds no
     * element, or the markup fits neither place.
     */
    public boolean pasteBlock(AuthorBlock anchor) {
        if (anchor == null || authorDocument == null) {
            return false;
        }
        String xml;
        try {
            Object data = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            xml = data == null ? null : data.toString().strip();
        } catch (Exception unavailable) {
            xml = null;   // empty clipboard, no display, or content that is not text
        }
        if (xml == null || !xml.startsWith("<")) {
            showStructureError("The clipboard does not hold a block of XML.");
            return false;
        }
        org.dom4j.Element element;
        try {
            element = org.dom4j.DocumentHelper.parseText(xml).getRootElement();
        } catch (org.dom4j.DocumentException notXml) {
            showStructureError("The clipboard XML is not well formed.");
            return false;
        }
        commitPendingEdits();
        AuthorBlock parent = anchor.getParent() != null ? anchor.getParent() : anchor;
        AuthorBlock pasted = adapter.importFragment(authorDocument, element, parent);
        var result = new com.dogsbay.xml.author.model.BlockOperations.Result[1];
        if (!guarded(() -> result[0] =
                com.dogsbay.xml.author.model.BlockOperations.pasteBlock(authorDocument, anchor, pasted))) {
            return false;
        }
        if (result[0] == null) {
            showStructureError(pasted.getType().getLabel() + " cannot go here.");
            return false;
        }
        apply(result[0], 0);
        return true;
    }

    /** Inserts a copy of the block right after it. */
    public boolean duplicateBlock(AuthorBlock block) {
        if (block == null || authorDocument == null || block.getParent() == null) {
            return false;
        }
        commitPendingEdits();
        org.dom4j.Element element = adapter.exportFragment(block);
        AuthorBlock copy = element == null ? null
                : adapter.importFragment(authorDocument, element, block.getParent());
        if (copy == null) {
            return false;
        }
        var result = new com.dogsbay.xml.author.model.BlockOperations.Result[1];
        if (!guarded(() -> result[0] =
                com.dogsbay.xml.author.model.BlockOperations.pasteBlock(authorDocument, block, copy))
                || result[0] == null) {
            return false;
        }
        apply(result[0], 0);
        return true;
    }

    /** Turns the list the block is in into the other list type. */
    public boolean convertList(AuthorBlock block) {
        if (block == null || authorDocument == null) {
            return false;
        }
        commitPendingEdits();
        var result = new com.dogsbay.xml.author.model.BlockOperations.Result[1];
        if (!guarded(() -> result[0] =
                com.dogsbay.xml.author.model.BlockOperations.convertList(authorDocument, block))
                || result[0] == null) {
            return false;
        }
        apply(result[0], 0);
        return true;
    }

    // ------------------------------------------------------------------
    // Font zoom
    // ------------------------------------------------------------------

    private final List<Runnable> zoomListeners = new ArrayList<>();

    /** Called after the font scale changed, for hosts that persist it. */
    public void addZoomListener(Runnable listener) {
        zoomListeners.add(listener);
    }

    /** Steps the shared Author font scale: +1 larger, -1 smaller, 0 back to normal. */
    public void zoom(int direction) {
        double current = BlockStylesheet.getFontScale();
        double next = direction == 0 ? 1.0 : (direction > 0 ? current * 1.1 : current / 1.1);
        if (BlockStylesheet.setFontScale(next) == current) {
            return;
        }
        commitPendingEdits();
        // rebuild() drops every text component, so remember where the caret was
        AuthorBlock caretBlock = focusedText != null ? focusedText.getBlock() : selectedBlock;
        TextBlockComponent caretText = caretBlock == null ? null : textComponentsById.get(caretBlock.getId());
        int caret = caretText == null ? 0 : caretText.getCaretPosition();
        rebuild();
        if (caretBlock != null && authorDocument != null
                && authorDocument.getBlock(caretBlock.getId()) != null) {
            focusBlock(caretBlock, caret);
        }
        for (Runnable listener : zoomListeners) {
            listener.run();
        }
    }

    /** Deletes a block from the context menu. */
    void deleteBlock(AuthorBlock block) {
        commitPendingEdits();
        apply(com.dogsbay.xml.author.model.BlockOperations.deleteBlock(authorDocument, block),
                Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------
    // Programmatic command API (CLI / MCP / agent driving)
    // ------------------------------------------------------------------

    /**
     * Inserts a block (with auto-created required descendants) under the
     * given parent block id; index -1 appends. Returns the id of the block
     * that received focus (the deepest created text block), or null when the
     * parent is unknown or the insertion is invalid.
     */
    public String insertBlockById(String typeName, String parentBlockId, int index) {
        if (authorDocument == null) {
            return null;
        }
        AuthorBlock parent = authorDocument.getBlock(parentBlockId);
        if (parent == null) {
            return null;
        }
        int at = index < 0 || index > parent.getChildren().size()
                ? parent.getChildren().size() : index;
        try {
            var result = com.dogsbay.xml.author.model.BlockOperations.insertBlock(
                    authorDocument, typeName, parent, at);
            apply(result, 0);
            return result.focus().getId();
        } catch (com.dogsbay.xml.author.model.AuthorStructureException
                | IllegalArgumentException e) {
            return null;
        }
    }

    /** Replaces a text block's content with one plain run; false when invalid. */
    public boolean setBlockTextById(String blockId, String text) {
        if (authorDocument == null) {
            return false;
        }
        AuthorBlock block = authorDocument.getBlock(blockId);
        if (block == null || !block.getType().hasText()) {
            return false;
        }
        com.dogsbay.xml.author.model.Transaction tx = authorDocument.begin("Set text");
        tx.setText(block, text.isEmpty()
                ? List.of() : List.of(com.dogsbay.xml.author.model.InlineRun.of(text)));
        undoSupport.postEdit(tx.commit());
        focusBlock(block, text.length());
        return true;
    }

    /**
     * Moves a block under a new parent at the given index (outline drag &
     * drop). Validated against the content model; undoable.
     */
    public boolean moveBlockTo(AuthorBlock block, AuthorBlock newParent, int index) {
        if (authorDocument == null || block == null || newParent == null
                || block.getParent() == null || block.isAncestorOf(newParent)
                || !authorDocument.getRegistry().isValidChild(newParent.getType(), block.getType())) {
            return false;
        }
        commitPendingEdits();
        try {
            com.dogsbay.xml.author.model.Transaction tx =
                    authorDocument.begin("Move " + block.getType().getLabel());
            tx.moveBlock(block, newParent, Math.min(index, newParent.getChildren().size()));
            undoSupport.postEdit(tx.commit());
            focusBlock(block, 0);
            return true;
        } catch (com.dogsbay.xml.author.model.AuthorStructureException e) {
            return false;
        }
    }

    /** Posts a committed structural edit and focuses its target block. */
    void apply(com.dogsbay.xml.author.model.BlockOperations.Result result, int caret) {
        if (result == null) {
            return;
        }
        undoSupport.postEdit(result.edit());
        flushPendingRebuilds();   // the new block's component must exist before it can take focus
        focusBlock(result.focus(), caret);
    }

    /** Focuses the text component of a block after a rebuild. */
    public void focusBlock(AuthorBlock block, int caret) {
        if (block == null || authorDocument == null) {
            return;
        }
        flushPendingRebuilds();   // the block's component must exist before it can take focus
        setSelectedBlock(authorDocument.getBlock(block.getId()));
        TextBlockComponent text = textComponentsById.get(block.getId());
        if (text != null) {
            text.requestFocusInWindow();
            text.setCaretPosition(Math.min(Math.max(caret, 0), text.getDocument().getLength()));
        }
    }

    /** Shows the schema-filtered insert menu for the given text block. */
    void showInsertMenu(TextBlockComponent component) {
        javax.swing.JPopupMenu menu = InsertMenu.forBlock(this, component.getBlock());
        try {
            java.awt.Rectangle caret = component.modelToView2D(component.getCaretPosition()).getBounds();
            menu.show(component, caret.x, caret.y + caret.height);
        } catch (javax.swing.text.BadLocationException e) {
            menu.show(component, 0, component.getHeight());
        }
    }

    /** Toggles an inline style over the selection of the focused text block. */
    public void toggleInlineStyle(String key, String value) {
        if (!adapter.supportsInlineStyles()) {
            return; // generic XML: styling would be dropped on export
        }
        if (focusedText != null) {
            focusedText.toggleStyle(key, value);
            focusedText.requestFocusInWindow();
        }
    }

    private void onDocumentChanged(AuthorDocumentEvent event) {
        switch (event.kind()) {
            case TEXT -> {
                AuthorBlock block = event.block();
                if (block != null && !block.getId().equals(suppressRefreshBlockId)) {
                    TextBlockComponent text = textComponentsById.get(block.getId());
                    if (text != null) {
                        text.refresh();
                        text.revalidate();
                    }
                }
            }
            case ATTRIBUTES -> {
                // Only blocks that draw from their attributes: images, links and other
                // void blocks, raw chips, and anything whose conref presence decides
                // whether its own content or the referenced text is shown.
                AuthorBlock b = event.block();
                if (b != null && (b.getType().getCategory() == BlockType.Category.VOID
                        || b.getType().getCategory() == BlockType.Category.RAW
                        || (b.getAttribute("conref") != null) != (viewsById.get(b.getId()) != null
                                && viewsById.get(b.getId()).showsConref))) {
                    scheduleRebuild(b, false);
                }
            }
            case STRUCTURE -> {
                if (event.block() != null) {
                    scheduleRebuild(event.block(), true);
                } else {
                    rebuild();
                }
            }
            case ROOT -> rebuild();
        }
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    public AuthorBlock getSelectedBlock() {
        return selectedBlock;
    }

    public void setSelectedBlock(AuthorBlock block) {
        if (selectedBlock == block) {
            return;
        }
        BlockView old = selectedBlock == null ? null : viewsById.get(selectedBlock.getId());
        if (old != null) {
            old.setSelected(false);
        }
        selectedBlock = block;
        BlockView view = block == null ? null : viewsById.get(block.getId());
        if (view != null) {
            view.setSelected(true);
            scrollTo(view);
        }
        breadcrumb.showPath(block);
        for (Consumer<AuthorBlock> listener : selectionListeners) {
            listener.accept(block);
        }
    }

    /**
     * Scrolls a block's view into sight. {@code scrollRectToVisible} takes the
     * rectangle in the component's own coordinates, and right after a rebuild
     * the bounds are not laid out yet, so this runs after layout.
     */
    /** Whether the view's top edge is inside the scrolled area right now. */
    private boolean isInViewport(BlockView view) {
        if (contentScroll == null || !view.isShowing() || view.getHeight() <= 0) {
            return true;   // nothing to scroll, or nothing laid out yet
        }
        java.awt.Rectangle visible = contentScroll.getViewport().getViewRect();
        java.awt.Point at = SwingUtilities.convertPoint(view, 0, 0, contentScroll.getViewport().getView());
        return visible.contains(at.x, at.y);
    }

    private void scrollTo(BlockView view) {
        SwingUtilities.invokeLater(() -> {
            if (view.isShowing() && view.getHeight() > 0) {
                view.scrollRectToVisible(new java.awt.Rectangle(0, 0, view.getWidth(),
                        Math.min(view.getHeight(), 240)));
            }
        });
    }

    public void addSelectionListener(Consumer<AuthorBlock> listener) {
        selectionListeners.add(listener);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Rebuilds the whole block stack (LaF changes, document replaced, structure edits). */
    public void rebuild() {
        stylesheet = new BlockStylesheet();
        String selectedId = selectedBlock == null ? null : selectedBlock.getId();
        viewsById.clear();
        textComponentsById.clear();
        focusedText = null;
        blocksPanel.removeAll();
        blocksPanel.setBackground(stylesheet.background());
        if (authorDocument != null && authorDocument.getRoot() != null) {
            blocksPanel.add(buildBlockView(authorDocument.getRoot(), 0));
        }
        selectedBlock = selectedId == null || authorDocument == null ? null : authorDocument.getBlock(selectedId);
        pendingRebuilds.clear();
        pendingOutlineRefresh = false;
        if (authorDocument != null) {
            afterViewsChanged(null);
        } else {
            breadcrumb.showPath(null);
        }
        if (outline != null) {
            outline.refresh();
        }
        revalidate();
        repaint();
    }

    private BlockView buildBlockView(AuthorBlock block, int depth) {
        BlockView view = new BlockView(block, depth);
        viewsById.put(block.getId(), view);
        return view;
    }

    // Blocks whose views are stale, rebuilt together: a transaction fires one event
    // per operation (a new table row fires one per cell), and the model is in an
    // intermediate state until the transaction ends. apply() flushes before it
    // focuses; anything else (undo, programmatic edits) flushes on the next EDT turn.
    /** A block whose view is out of date: structural when only its child list changed. */
    private record Pending(AuthorBlock block, boolean structural) {}

    private final java.util.LinkedHashMap<String, Pending> pendingRebuilds = new java.util.LinkedHashMap<>();
    private boolean pendingOutlineRefresh;
    private boolean rebuildScheduled;

    private void scheduleRebuild(AuthorBlock block, boolean structural) {
        Pending before = pendingRebuilds.get(block.getId());
        // an attribute change on top of a child-list change needs the whole view redrawn
        pendingRebuilds.put(block.getId(), new Pending(block, structural && (before == null || before.structural())));
        pendingOutlineRefresh |= structural;
        if (!rebuildScheduled) {
            rebuildScheduled = true;
            SwingUtilities.invokeLater(this::flushPendingRebuilds);
        }
    }

    /** Rebuilds every pending block once, outermost first; nested pending blocks come with their ancestor. */
    void flushPendingRebuilds() {
        rebuildScheduled = false;
        if (pendingRebuilds.isEmpty()) {
            return;
        }
        List<Pending> dirty = new ArrayList<>(pendingRebuilds.values());
        java.util.Set<String> dirtyIds = new java.util.HashSet<>(pendingRebuilds.keySet());
        boolean refreshOutline = pendingOutlineRefresh;
        pendingRebuilds.clear();
        pendingOutlineRefresh = false;
        if (authorDocument == null) {
            return;
        }
        dirty.sort(java.util.Comparator.comparingInt(p -> p.block().path().size()));   // outermost first
        java.util.Set<String> rebuilt = new java.util.HashSet<>();
        for (Pending pending : dirty) {
            AuthorBlock block = pending.block();
            if (authorDocument.getBlock(block.getId()) != block) {
                continue;   // removed since the event: its parent's sync dropped its view
            }
            boolean covered = false;
            for (String id : rebuilt) {
                AuthorBlock other = authorDocument.getBlock(id);
                if (other != null && other.isAncestorOf(block)) {
                    covered = true;   // a full rebuild of an ancestor drew this block afresh
                    break;
                }
            }
            if (covered) {
                continue;
            }
            BlockView view = viewsById.get(block.getId());
            if (pending.structural() && view != null && view.block == block && view.syncChildren(dirtyIds)) {
                afterViewsChanged(block);
                continue;
            }
            rebuilt.add(block.getId());
            if (!rebuildBlock(block)) {
                return;   // fell back to a full rebuild, which covers everything
            }
        }
        if (refreshOutline && outline != null) {
            outline.refresh();
        }
    }

    /**
     * Rebuilds the view of one block and its subtree in place, leaving the
     * rest of the document's components alone, so a structural edit on a long
     * topic costs the size of the touched block, not of the document. Returns
     * false when it had to fall back to a full rebuild (the root, or a block
     * with no view yet).
     */
    boolean rebuildBlock(AuthorBlock block) {
        if (authorDocument == null || block == null) {
            return true;
        }
        if (authorDocument.getBlock(block.getId()) != block) {
            return true;   // removed since the event: its ancestor's rebuild covered it
        }
        BlockView old = viewsById.get(block.getId());
        java.awt.Container container = old == null ? null : old.getParent();
        int at = container == null ? -1 : container.getComponentZOrder(old);
        if (block.getParent() == null || at < 0) {
            rebuild();
            return false;
        }
        if (focusedText != null && SwingUtilities.isDescendingFrom(focusedText, old)) {
            focusedText = null;
        }
        forgetSubtree(block);
        BlockView fresh = new BlockView(block, old.depth);
        viewsById.put(block.getId(), fresh);
        container.remove(at);
        container.add(fresh, at);
        afterViewsChanged(block);
        container.revalidate();
        container.repaint();
        return true;
    }

    /** Selection mark and issue badges for the views under {@code root} (the whole document when null). */
    private void afterViewsChanged(AuthorBlock root) {
        if (selectedBlock != null) {
            // the selected block may have been replaced (same id, new instance) or removed by the edit
            AuthorBlock live = authorDocument == null ? null : authorDocument.getBlock(selectedBlock.getId());
            if (live != selectedBlock) {
                selectedBlock = live;
                for (Consumer<AuthorBlock> listener : selectionListeners) {
                    listener.accept(live);
                }
            }
            BlockView selectedView = selectedBlock == null ? null : viewsById.get(selectedBlock.getId());
            if (selectedView != null) {
                selectedView.setSelected(true);
            }
        }
        breadcrumb.showPath(selectedBlock);
        applyIssues(root == null ? authorDocument.getRoot() : root);
    }

    private void applyIssues(AuthorBlock block) {
        if (block == null) {
            return;
        }
        BlockView view = viewsById.get(block.getId());
        if (view != null) {
            view.setIssues(issuesByBlock.getOrDefault(block.getId(), List.of()));
        }
        for (AuthorBlock child : block.getChildren()) {
            applyIssues(child);
        }
    }

    /** Drops the map entries of a block's view and everything under it, before it is replaced. */
    private void forgetSubtree(AuthorBlock block) {
        BlockView view = viewsById.remove(block.getId());
        TextBlockComponent text = textComponentsById.remove(block.getId());
        if (text != null) {
            text.dispose();
        }
        if (view != null) {
            for (AuthorBlock child : block.getChildren()) {
                forgetSubtree(child);
            }
            // children that were removed from the model still hold map entries: sweep by view
            viewsById.values().removeIf(v -> SwingUtilities.isDescendingFrom(v, view));
            for (var it = textComponentsById.values().iterator(); it.hasNext();) {
                TextBlockComponent t = it.next();
                if (SwingUtilities.isDescendingFrom(t, view)) {
                    t.dispose();
                    it.remove();
                }
            }
        }
    }

    /** Visible component for one block: gutter label, content, indented children. */
    /**
     * How far a table cell spans, for its header label: "· cols 1-3" from
     * {@code namest}/{@code nameend}, "· +2 rows" from {@code morerows}.
     * Empty for a cell that spans nothing, and for everything else.
     */
    static String spanSuffix(AuthorBlock block) {
        String start = block.getAttribute("namest");
        String end = block.getAttribute("nameend");
        String more = block.getAttribute("morerows");
        StringBuilder sb = new StringBuilder();
        if (start != null && end != null) {
            sb.append(" · cols ").append(start).append('-').append(end);
        }
        if (more != null && !more.isBlank() && !"0".equals(more.trim())) {
            sb.append(" · +").append(more.trim()).append(" rows");
        }
        return sb.toString();
    }

    /** Indent for a block's children; topic roots and bodies stay flush. */
    private static int childIndent(AuthorBlock parent) {
        return switch (parent.getType().getName()) {
            case "concept", "task", "reference", "topic", "glossentry",
                 "conbody", "taskbody", "refbody", "body" -> 0;
            default -> 18;
        };
    }

    private final class BlockView extends JPanel {

        private final AuthorBlock block;
        private final int depth;
        private final boolean showsConref;
        private final JLabel badge;
        private final JPanel blockActions;
        private JPanel childrenPanel;
        private boolean selected;

        /**
         * Brings the child views in line with the block's children after a
         * structural edit, keeping the views of children that are still there
         * (and not themselves dirty) and building only the new ones. False when
         * the shape changed in a way that needs the whole view rebuilt: children
         * appeared or vanished entirely, or a conref took over.
         */
        boolean syncChildren(java.util.Set<String> dirtyIds) {
            boolean wantsChildren = !block.getChildren().isEmpty() && block.getAttribute("conref") == null;
            if (childrenPanel == null || !wantsChildren) {
                return false;
            }
            Map<String, BlockView> existing = new java.util.HashMap<>();
            for (java.awt.Component c : childrenPanel.getComponents()) {
                if (c instanceof BlockView v) {
                    existing.put(v.block.getId(), v);
                }
            }
            childrenPanel.removeAll();
            for (AuthorBlock child : block.getChildren()) {
                BlockView view = existing.remove(child.getId());
                if (view != null && view.block == child && !dirtyIds.contains(child.getId())) {
                    childrenPanel.add(view);
                    continue;
                }
                if (view != null) {
                    dropView(view);
                }
                childrenPanel.add(buildBlockView(child, depth + 1));
            }
            for (BlockView gone : existing.values()) {
                dropView(gone);
            }
            childrenPanel.revalidate();
            childrenPanel.repaint();
            return true;
        }

        private void dropView(BlockView view) {
            if (focusedText != null && SwingUtilities.isDescendingFrom(focusedText, view)) {
                focusedText = null;
            }
            forgetSubtree(view.block);
        }

        BlockView(AuthorBlock block, int depth) {
            this.block = block;
            this.depth = depth;
            this.showsConref = block.getAttribute("conref") != null;
            setLayout(new VerticalStackLayout());
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);

            // web-editor structure: a small fixed-height label line above the
            // content, full-width body below, children modestly indented.
            // Selection only changes colors/visibility inside the fixed-height
            // header, so it can never reflow the document.
            badge = new JLabel(" ");
            badge.setFont(stylesheet.gutterFont());

            String span = spanSuffix(block);
            JLabel typeLabel = new JLabel(block.getType().getLabel() + span);
            typeLabel.setFont(stylesheet.gutterFont());
            typeLabel.setForeground(stylesheet.gutterForeground());
            if (!span.isEmpty()) {
                typeLabel.setToolTipText("This cell spans" + span.replace(" ·", "") + "; spans are preserved on save");
            }

            blockActions = buildBlockActions();

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
            header.setOpaque(false);
            header.setAlignmentX(LEFT_ALIGNMENT);
            header.add(badge);
            header.add(typeLabel);
            header.add(Box.createHorizontalStrut(6));
            header.add(blockActions);
            header.add(Box.createHorizontalGlue());
            int headerHeight = Math.max(typeLabel.getPreferredSize().height,
                    blockActions.getPreferredSize().height) + 2;
            header.setPreferredSize(new Dimension(10, headerHeight));
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerHeight));
            add(header);

            JComponent body = buildBody(depth);
            if (body != null) {
                body.setAlignmentX(LEFT_ALIGNMENT);
                add(body);
            }
            // conref'd blocks show the referenced content; their local children
            // (placeholder cmd etc.) stay hidden
            if (!block.getChildren().isEmpty() && block.getAttribute("conref") == null) {
                childrenPanel = new JPanel();
                childrenPanel.setLayout(new VerticalStackLayout());
                childrenPanel.setOpaque(false);
                childrenPanel.setAlignmentX(LEFT_ALIGNMENT);
                childrenPanel.setBorder(new EmptyBorder(0, childIndent(block), 0, 0));
                for (AuthorBlock child : block.getChildren()) {
                    childrenPanel.add(buildBlockView(child, depth + 1));
                }
                add(childrenPanel);
            }

            // category accent as the block's left rule (note keeps its orange)
            java.awt.Color accent = "note".equals(block.getType().getName())
                    ? stylesheet.noteAccent()
                    : stylesheet.categoryAccent(block.getType().getName());
            if (accent != null) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 2, 0, 0, accent),
                        new EmptyBorder(2, 6, 2, 0)));
            } else {
                setBorder(new EmptyBorder(2, 8, 2, 0));
            }

            MouseAdapter selector = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    setSelectedBlock(BlockView.this.block);
                    maybeShowContextMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowContextMenu(e);
                }

                private void maybeShowContextMenu(MouseEvent e) {
                    // text components own their popup (clipboard + formatting,
                    // block actions appended) — don't double up over them
                    if (e.isPopupTrigger() && !(e.getComponent() instanceof TextBlockComponent)) {
                        InsertMenu.contextMenu(AuthorEditorPanel.this, BlockView.this.block)
                                .show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            };
            addMouseListener(selector);
            header.addMouseListener(selector);
            typeLabel.addMouseListener(selector);
            if (body != null) {
                body.addMouseListener(selector);
            }
        }

        private JComponent buildBody(int depth) {
            if (block.getAttribute("conref") != null) {
                return conrefBody();
            }
            return switch (block.getType().getCategory()) {
                case TEXT -> {
                    TextBlockComponent text =
                            new TextBlockComponent(block, stylesheet, depth, AuthorEditorPanel.this);
                    textComponentsById.put(block.getId(), text);
                    yield text;
                }
                case RAW -> rawBody();
                case VOID -> voidBody();
                case CONTAINER -> null;
            };
        }

        /** Conref'd blocks render the referenced content read-only ("locked"). */
        private JComponent conrefBody() {
            String conref = block.getAttribute("conref");
            org.dom4j.Element resolved = references.resolveConref(conref);
            String text;
            if (resolved != null) {
                text = resolved.getStringValue().replaceAll("\\s+", " ").strip();
                if (text.length() > 300) {
                    text = text.substring(0, 300) + "…";
                }
            } else {
                text = conref;
            }
            JLabel label = new JLabel("<html>⧉ <i>" + escape(text) + "</i></html>");
            label.setFont(stylesheet.fontFor(block.getType(), 0)
                    .deriveFont(java.awt.Font.ITALIC));
            label.setForeground(stylesheet.mutedForeground());
            label.setToolTipText("Referenced content (conref): " + conref
                    + " — edit it at its source");
            return label;
        }

        private String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private JComponent rawBody() {
            JTextArea area = new JTextArea(block.getRawXml());
            area.setEditable(false);
            area.setFont(stylesheet.monoFont());
            area.setForeground(stylesheet.mutedForeground());
            area.setBackground(stylesheet.rawBackground());
            area.setBorder(new EmptyBorder(4, 6, 4, 6));
            return area;
        }

        private JComponent voidBody() {
            return switch (block.getType().getName()) {
                case "image" -> imageBody();
                case "link" -> linkBody();
                default -> attributeSummaryBody();
            };
        }

        private JComponent imageBody() {
            String href = block.getAttribute("href");
            if (href == null && block.getAttribute("keyref") != null) {
                href = references.keyHref(block.getAttribute("keyref"));
            }
            javax.swing.ImageIcon icon = href == null ? null
                    : imageFor(references.resolveHref(href));
            JLabel label;
            if (icon != null) {
                label = new JLabel(icon);
                String alt = block.getAttribute(
                        com.dogsbay.xml.author.adapter.DitaBlockAdapter.IMAGE_ALT_ATTR);
                label.setToolTipText(alt != null ? alt : href);
            } else {
                label = new JLabel("🖼 " + (href != null ? href : block.getType().getLabel()));
                label.setFont(stylesheet.gutterFont());
                label.setForeground(stylesheet.mutedForeground());
            }
            return label;
        }

        /** Related-links entries: "🔗 link text — target". */
        private JComponent linkBody() {
            String target = block.getAttribute("href");
            if (target == null && block.getAttribute("keyref") != null) {
                String key = block.getAttribute("keyref");
                String resolved = references.keyText(key);
                target = resolved != null ? resolved : "⟨" + key + "⟩";
            }
            String text = block.getAttribute(
                    com.dogsbay.xml.author.adapter.DitaBlockAdapter.LINK_TEXT_ATTR);
            String display = text != null && !text.isBlank()
                    ? text + "  —  " + (target != null ? target : "")
                    : (target != null ? target : "link");
            JLabel label = new JLabel("🔗 " + display);
            label.setForeground(stylesheet.mutedForeground());
            label.setToolTipText(block.getAttribute("href"));
            return label;
        }

        /** Generic void block (colspec, ...): muted attribute summary. */
        private JComponent attributeSummaryBody() {
            StringBuilder sb = new StringBuilder(block.getType().getLabel());
            for (Map.Entry<String, String> e : block.getAttributes().entrySet()) {
                if (!e.getKey().startsWith("#")) {
                    sb.append("  ").append(e.getKey()).append("=").append(e.getValue());
                }
            }
            JLabel label = new JLabel(sb.toString());
            label.setFont(stylesheet.gutterFont());
            label.setForeground(stylesheet.mutedForeground());
            return label;
        }

        /** The + / × mini-toolbar shown on the selected block's gutter. */
        private JPanel buildBlockActions() {
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
            actions.setOpaque(false);
            javax.swing.JButton insert = miniButton("+", "Insert a block here");
            insert.addActionListener(e -> InsertMenu.forBlock(AuthorEditorPanel.this, block)
                    .show(insert, 0, insert.getHeight()));
            actions.add(insert);
            if (block.getType().isDeletable() && block.getParent() != null) {
                javax.swing.JButton delete = miniButton("×", "Delete this block");
                delete.addActionListener(e -> deleteBlock(block));
                actions.add(delete);
            }
            actions.setVisible(false);
            return actions;
        }

        private javax.swing.JButton miniButton(String label, String tooltip) {
            javax.swing.JButton button = new javax.swing.JButton(label);
            button.setToolTipText(tooltip);
            button.setFont(stylesheet.gutterFont());
            button.setMargin(new java.awt.Insets(0, 4, 0, 4));
            button.setFocusable(false);
            return button;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            putClientProperty("author.selected", selected);
            blockActions.setVisible(selected);
            revalidate();
            repaint();
        }

        /**
         * The selection tint is translucent, so it must be painted by a
         * NON-opaque component: declaring opaque=true while filling with an
         * alpha color violates Swing's opacity contract — the buffer is never
         * cleared underneath, and every caret/selection repaint leaves its
         * old pixels ghosted under the wash (the click-residue bug).
         */
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (selected) {
                g.setColor(stylesheet.selectionBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }

        /** Shows a validation badge (✕ error / ⚠ warning) with the messages as tooltip. */
        void setIssues(List<com.dogsbay.xml.author.validation.ValidationIssue> issues) {
            if (issues.isEmpty()) {
                badge.setText(" ");
                badge.setToolTipText(null);
            } else {
                boolean error = issues.stream().anyMatch(
                        com.dogsbay.xml.author.validation.ValidationIssue::isError);
                badge.setText(error ? "✕" : "⚠");
                badge.setForeground(error ? new java.awt.Color(0xD64540) : stylesheet.noteAccent());
                StringBuilder tip = new StringBuilder("<html>");
                for (var issue : issues) {
                    tip.append(issue.message()).append("<br>");
                }
                badge.setToolTipText(tip.toString());
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Tracks the viewport width so text blocks wrap instead of scrolling sideways. */
    private static final class ScrollableWidthPanel extends JPanel implements javax.swing.Scrollable {

        ScrollableWidthPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Test support
    // ------------------------------------------------------------------

    /** The rendered component for a block id, or null (visible for tests). */
    public JComponent getBlockComponent(String blockId) {
        return viewsById.get(blockId);
    }

    /** Number of rendered block views (visible for tests). */
    public int getRenderedBlockCount() {
        return viewsById.size();
    }
}
