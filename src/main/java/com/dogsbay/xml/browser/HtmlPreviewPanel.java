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

package com.dogsbay.xml.browser;

import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import netscape.javascript.JSObject;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A ViewPanel that hosts a JavaFX WebView for rendering HTML content
 * inside the application. Uses JFXPanel as a Swing-JavaFX bridge.
 * Includes a toolbar for selecting the preview CSS theme.
 *
 * @author DogsBay Ltd
 */
public class HtmlPreviewPanel extends ViewPanel {
    private static final boolean DEBUG = false;
    private static final AtomicBoolean FX_INITIALIZED = new AtomicBoolean(false);

    private JFXPanel jfxPanel;
    private WebView webView;
    private WebEngine webEngine;
    private Runnable onDispose;

    private PreviewTheme currentTheme = PreviewTheme.LIGHT;
    private ConfigurationProperties config;
    private String lastRawHtml;
    private Runnable onThemeChanged;
    private boolean scrollSyncEnabled = true;
    private Runnable onPreviewScrolled;

    // DITA rendering context (per-tab, session-only)
    private java.io.File ditaContextMap;
    private java.io.File ditaval;
    private Runnable onContextChanged;
    private JCheckBox showChangesCheck;
    private JPanel contextBar;
    private JLabel mapValueLabel;
    private JLabel ditavalValueLabel;
    // Branch-filter (DITA 1.3 ditavalref) selector: previewing a chosen variant
    // applies that branch's DITAVAL. "Whole map" restores the deliverable default.
    private java.io.File defaultDitaval;
    private JLabel branchLabel;
    private JComboBox<BranchChoice> branchCombo;
    private boolean updatingBranches;

    /** One entry in the branch selector: a label and the DITAVAL it applies. */
    record BranchChoice(String label, java.io.File ditaval) {
        @Override public String toString() {
            return label;
        }
    }

    /**
     * The branch-selector entries for a context map: "Whole map" (restoring
     * {@code defaultDitaval}) followed by one entry per {@code <ditavalref>} branch
     * that has a resolvable DITAVAL (others can't be previewed in-process). A null /
     * non-file / branchless map yields just the "Whole map" entry. GUI-free for test.
     */
    static java.util.List<BranchChoice> buildBranchChoices(
            java.io.File contextMap, java.io.File defaultDitaval) {
        java.util.List<BranchChoice> choices = new java.util.ArrayList<>();
        choices.add(new BranchChoice("Whole map", defaultDitaval));
        if (contextMap != null && contextMap.isFile()) {
            try {
                for (com.dogsbay.dogsbayaieditor.links.Branch b
                        : com.dogsbay.dogsbayaieditor.links.BranchModel.enumerate(contextMap)) {
                    // Only branches with an existing DITAVAL can be previewed (a missing
                    // file would just render blank); skip the rest.
                    if (b.ditaval() != null && b.ditaval().isFile()) {
                        choices.add(new BranchChoice(b.label(), b.ditaval()));
                    }
                }
            } catch (java.io.IOException ignored) {
                // unreadable/malformed map → just the whole-map entry
            }
        }
        return choices;
    }

    /** Tracks which side last initiated a scroll to prevent feedback loops. */
    private volatile long editorScrolledAt = 0;
    private volatile long previewScrolledAt = 0;
    private static final long SCROLL_COOLDOWN_MS = 100;

    public HtmlPreviewPanel(ConfigurationProperties config) {
        super(new BorderLayout());
        this.config = config;
        if (config != null) {
            currentTheme = PreviewTheme.fromName(config.getPreviewTheme());
            scrollSyncEnabled = config.isPreviewScrollSync();
        }
        initializeFX();
        createToolbar();
        createContent();
    }

    public HtmlPreviewPanel() {
        this(null);
    }

    /**
     * Ensures the JavaFX toolkit is initialized. Safe to call multiple times.
     */
    private static void initializeFX() {
        if (FX_INITIALIZED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException e) {
                // Already initialized — that's fine
            }
            Platform.setImplicitExit(false);
        }
    }

    private void createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));

        JLabel label = new JLabel("Theme:");
        toolbar.add(label);

        JComboBox<PreviewTheme> themeCombo = new JComboBox<>(PreviewTheme.values());
        themeCombo.setSelectedItem(currentTheme);
        themeCombo.setMaximumRowCount(PreviewTheme.values().length);
        themeCombo.addActionListener(e -> {
            PreviewTheme selected = (PreviewTheme) themeCombo.getSelectedItem();
            if (selected != null && selected != currentTheme) {
                currentTheme = selected;
                if (config != null) {
                    config.setPreviewTheme(selected.getDisplayName());
                }
                // Re-render with new theme
                if (lastRawHtml != null) {
                    String themed = currentTheme.applyTo(lastRawHtml);
                    Platform.runLater(() -> webEngine.loadContent(themed));
                }
                if (onThemeChanged != null) {
                    onThemeChanged.run();
                }
            }
        });
        toolbar.add(themeCombo);

        toolbar.add(Box.createHorizontalStrut(12));

        JCheckBox syncCheck = new JCheckBox("Scroll Sync", scrollSyncEnabled);
        syncCheck.addActionListener(e -> {
            scrollSyncEnabled = syncCheck.isSelected();
            if (config != null) {
                config.setPreviewScrollSync(scrollSyncEnabled);
            }
        });
        toolbar.add(syncCheck);

        showChangesCheck = new JCheckBox("Show changes", false);
        showChangesCheck.setToolTipText("Render open review proposals as insertions, deletions and comments "
                + "instead of the accepted view");
        showChangesCheck.addActionListener(e -> {
            if (onContextChanged != null) {
                onContextChanged.run();
            }
        });
        toolbar.add(showChangesCheck);

        add(toolbar, BorderLayout.SOUTH);
    }

    // -------------------------------------------------------------------------
    // DITA rendering context bar (map for key resolution + ditaval filter)
    // -------------------------------------------------------------------------

    /**
     * Shows the DITA context bar with an initial context map (typically the
     * project's Default Root Map; may be null). Called by the manager only
     * for DITA documents.
     */
    public void showDitaContextBar(java.io.File defaultMap) {
        showDitaContextBar(defaultMap, null);
    }

    /**
     * Shows the DITA context bar seeded with a context map and DITAVAL filter from the
     * active deliverable (either may be null). The map and filter are read-only here — to
     * change them, switch or edit the deliverable; only the branch variant is selectable.
     * Call again to re-seed when the active deliverable changes.
     */
    public void showDitaContextBar(java.io.File defaultMap, java.io.File defaultDitaval) {
        this.ditaContextMap = defaultMap;
        this.ditaval = defaultDitaval;
        this.defaultDitaval = defaultDitaval;
        if (contextBar == null) {
            contextBar = buildContextBar();
            add(contextBar, BorderLayout.NORTH);
            revalidate();
        }
        updateContextLabels();
        refreshBranchChoices();
        contextBar.setVisible(true);
    }

    private JPanel buildContextBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));

        // The context map and DITAVAL filter come from the active deliverable (the
        // project's single source of truth) and are shown read-only here — switch or edit
        // the deliverable to change them. Only the structured DITA 1.3 branch variant of
        // the deliverable's map is selectable below.
        bar.add(new JLabel("Map:"));
        mapValueLabel = new JLabel();
        bar.add(mapValueLabel);

        bar.add(Box.createHorizontalStrut(10));

        bar.add(new JLabel("Filter:"));
        ditavalValueLabel = new JLabel();
        bar.add(ditavalValueLabel);

        bar.add(Box.createHorizontalStrut(10));

        branchLabel = new JLabel("Branch:");
        bar.add(branchLabel);
        branchCombo = new JComboBox<>();
        branchCombo.setToolTipText("Preview a DITA 1.3 branch-filter variant "
                + "(applies the branch's DITAVAL)");
        branchCombo.addActionListener(e -> {
            if (updatingBranches) {
                return;
            }
            BranchChoice choice = (BranchChoice) branchCombo.getSelectedItem();
            if (choice != null) {
                ditaval = choice.ditaval();           // whole map → default; branch → its DITAVAL
                contextChanged();
            }
        });
        bar.add(branchCombo);

        return bar;
    }

    /**
     * Repopulate the branch selector from the current context map's
     * {@code <ditavalref>} branches. Hidden when the map has none.
     */
    private void refreshBranchChoices() {
        if (branchCombo == null) {
            return;
        }
        updatingBranches = true;
        branchCombo.removeAllItems();
        for (BranchChoice choice : buildBranchChoices(ditaContextMap, defaultDitaval)) {
            branchCombo.addItem(choice);
        }
        branchCombo.setSelectedIndex(0);
        updatingBranches = false;
        // The combo now reads "Whole map" — keep the applied filter in sync (the guard
        // above suppressed the listener that would otherwise restore it), so changing
        // the map can't leave a previously-selected branch's DITAVAL applied.
        ditaval = defaultDitaval;
        boolean hasBranches = branchCombo.getItemCount() > 1;
        branchLabel.setVisible(hasBranches);
        branchCombo.setVisible(hasBranches);
    }

    private void contextChanged() {
        updateContextLabels();
        if (onContextChanged != null) {
            onContextChanged.run();
        }
    }

    private void updateContextLabels() {
        if (mapValueLabel != null) {
            mapValueLabel.setText(ditaContextMap != null ? ditaContextMap.getName() : "none");
            mapValueLabel.setToolTipText(ditaContextMap != null
                    ? ditaContextMap.getAbsolutePath() : null);
        }
        if (ditavalValueLabel != null) {
            ditavalValueLabel.setText(ditaval != null ? ditaval.getName() : "none");
            ditavalValueLabel.setToolTipText(ditaval != null
                    ? ditaval.getAbsolutePath() : null);
        }
    }

    /** The current DITA rendering context (both fields may be null). */
    public PreviewOptions getPreviewOptions() {
        return new PreviewOptions(ditaContextMap, ditaval,
                showChangesCheck != null && showChangesCheck.isSelected());
    }

    /** Called when the user changes the context map or ditaval. */
    public void setOnContextChanged(Runnable onContextChanged) {
        this.onContextChanged = onContextChanged;
    }

    private void createContent() {
        jfxPanel = new JFXPanel();
        add(jfxPanel, BorderLayout.CENTER);

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            webView = new WebView();
            webEngine = webView.getEngine();
            // Our own right-click menu: the rendered text can be sent to the agent tab in
            // front, so a tutorial's prompts run from the preview with no markup in the way.
            webView.setContextMenuEnabled(false);
            webView.setOnContextMenuRequested(e -> showContextMenu(e.getScreenX(), e.getScreenY()));

            // When page loads, inject scroll listener that calls back to Java
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    installScrollBridge();
                }
            });

            Scene scene = new Scene(webView);
            jfxPanel.setScene(scene);
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Loads an HTML string into the WebView, applying the current theme.
     *
     * @param html    the raw HTML content to render
     * @param baseUrl optional base URL for resolving relative resources (may be null)
     */
    public void loadContent(String html, URL baseUrl) {
        lastRawHtml = html;
        String themed = currentTheme.applyTo(html);
        Platform.runLater(() -> {
            webEngine.loadContent(themed);
        });
    }

    /**
     * Refreshes the WebView with new HTML content, preserving scroll position.
     * Applies the current theme CSS.
     *
     * @param html the raw HTML content to render
     */
    public void refresh(String html) {
        lastRawHtml = html;
        String themed = currentTheme.applyTo(html);
        Platform.runLater(() -> {
            // Capture scroll position before reload
            int scrollPos = 0;
            try {
                Object result = webEngine.executeScript("window.scrollY");
                if (result instanceof Number) {
                    scrollPos = ((Number) result).intValue();
                }
            } catch (Exception e) {
                // Page may not be loaded yet
            }

            webEngine.loadContent(themed);

            // Restore scroll position after content loads
            if (scrollPos > 0) {
                final int pos = scrollPos;
                // Use a one-shot listener that removes itself after restoring
                javafx.beans.value.ChangeListener<Worker.State>[] holder = new javafx.beans.value.ChangeListener[1];
                holder[0] = (obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        webEngine.executeScript("window.scrollTo(0, " + pos + ")");
                        webEngine.getLoadWorker().stateProperty().removeListener(holder[0]);
                    }
                };
                webEngine.getLoadWorker().stateProperty().addListener(holder[0]);
            }
        });
    }

    /**
     * Returns the current theme.
     */
    public PreviewTheme getTheme() {
        return currentTheme;
    }

    /**
     * Returns whether scroll sync is enabled.
     */
    public boolean isScrollSyncEnabled() {
        return scrollSyncEnabled;
    }

    /**
     * Returns true when editor-initiated scroll sync should be suppressed
     * (because the preview was recently scrolled by the user).
     */
    public boolean isSuppressingEditorSync() {
        return (System.currentTimeMillis() - previewScrolledAt) < SCROLL_COOLDOWN_MS;
    }

    /**
     * Scrolls the preview to the given percentage (0.0 to 1.0).
     * Called from editor scroll sync.
     */
    public void scrollToPercentage(double percentage) {
        if (!scrollSyncEnabled) {
            return;
        }
        editorScrolledAt = System.currentTimeMillis();
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(
                    "window.scrollTo(0, (document.body.scrollHeight - window.innerHeight) * " + percentage + ")");
            } catch (Exception e) {
                // Page may not be loaded yet
            }
        });
    }

    /**
     * Sets a callback invoked when the user scrolls the preview.
     * The callback receives the scroll percentage via {@link #getScrollPercentage()}.
     */
    public void setOnPreviewScrolled(Runnable callback) {
        this.onPreviewScrolled = callback;
    }

    /**
     * Returns the current scroll percentage of the preview (0.0 to 1.0).
     * Must be called from the JavaFX thread.
     */
    public double getScrollPercentage() {
        try {
            Object result = webEngine.executeScript(
                "(function() { var max = document.body.scrollHeight - window.innerHeight; return max > 0 ? window.scrollY / max : 0; })()");
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            // Page may not be loaded
        }
        return 0.0;
    }

    /** The rendered page's selected text, or "" when nothing is selected. Must run on the JavaFX thread. */
    String selectedText() {
        try {
            Object sel = webEngine.executeScript("window.getSelection().toString()");
            return sel == null ? "" : sel.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Copy, and Send selection to AI Agent while an agent tab can take it. JavaFX thread. */
    private void showContextMenu(double screenX, double screenY) {
        String selected = selectedText().strip();
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Copy");
        copy.setDisable(selected.isEmpty());
        copy.setOnAction(a -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(selected);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().add(copy);
        if (com.dogsbay.dogsbayaieditor.SelectionToAgent.available()) {
            javafx.scene.control.MenuItem send = new javafx.scene.control.MenuItem(
                    com.dogsbay.dogsbayaieditor.SelectionToAgent.LABEL);
            send.setDisable(selected.isEmpty());
            send.setOnAction(a -> javax.swing.SwingUtilities.invokeLater(
                    () -> com.dogsbay.dogsbayaieditor.SelectionToAgent.send(selected)));
            menu.getItems().add(send);
        }
        javafx.scene.control.MenuItem reload = new javafx.scene.control.MenuItem("Reload");
        reload.setOnAction(a -> webEngine.reload());
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        menu.getItems().add(reload);
        menu.show(webView, screenX, screenY);
    }

    /**
     * Installs a JavaScript scroll listener that bridges back to Java.
     * Must be called on the JavaFX thread after page load.
     */
    private void installScrollBridge() {
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("javaScrollBridge", this);
            webEngine.executeScript(
                "window.addEventListener('scroll', function() { window.javaScrollBridge.onWebViewScrolled(); })");
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Called from JavaScript when the user scrolls the preview.
     * Must be public for the JS bridge to invoke it.
     */
    public void onWebViewScrolled() {
        if (!scrollSyncEnabled || onPreviewScrolled == null) {
            return;
        }
        // Ignore if this scroll was triggered by editor sync
        if ((System.currentTimeMillis() - editorScrolledAt) < SCROLL_COOLDOWN_MS) {
            return;
        }
        previewScrolledAt = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            if (onPreviewScrolled != null) {
                onPreviewScrolled.run();
            }
        });
    }

    /**
     * Sets a callback to run when the user changes the theme.
     * Used by HtmlPreviewManager to trigger a full refresh if needed.
     */
    public void setOnThemeChanged(Runnable onThemeChanged) {
        this.onThemeChanged = onThemeChanged;
    }

    /**
     * Loads a URL into the WebView.
     *
     * @param url the URL to load
     */
    public void loadUrl(URL url) {
        Platform.runLater(() -> {
            webEngine.load(url.toExternalForm());
        });
    }

    /**
     * Loads a URL string into the WebView.
     *
     * @param url the URL string to load
     */
    public void loadUrl(String url) {
        Platform.runLater(() -> {
            webEngine.load(url);
        });
    }

    @Override
    public void setFocus() {
        jfxPanel.requestFocus();
    }

    @Override
    public void updatePreferences() {
        // No preferences to update currently
    }

    @Override
    public void setProperties() {
        // No properties to set currently
    }

    /**
     * Sets a callback to run when this panel is removed from the component hierarchy.
     * Used by HtmlPreviewManager to clean up live refresh resources.
     */
    public void setOnDispose(Runnable onDispose) {
        this.onDispose = onDispose;
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        // Defer disposal check — if the panel is being re-parented (e.g. split pane),
        // addNotify() will be called again shortly and the parent will be non-null.
        // Only dispose if the panel is truly orphaned after the EDT settles.
        if (onDispose != null) {
            final Runnable callback = onDispose;
            SwingUtilities.invokeLater(() -> {
                if (getParent() == null && onDispose != null) {
                    callback.run();
                    onDispose = null;
                }
            });
        }
    }

    /**
     * Returns the WebEngine for testing or advanced usage.
     * Must be called on the JavaFX application thread.
     */
    WebEngine getWebEngine() {
        return webEngine;
    }
}
