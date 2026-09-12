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

package com.dogsbay.dogsbayaieditor.plugin.whereused;

import java.io.File;
import java.net.URL;
import java.util.function.Consumer;

import javax.swing.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.links.AttributeReferenceLocator;
import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.dogsbayaieditor.services.events.DocumentModifiedEvent;
import com.dogsbay.dogsbayaieditor.whereused.KeyNavigationItems;
import com.dogsbay.dogsbayaieditor.whereused.WhereUsedPanel;

/**
 * Built-in plugin that adds the Where Used panel to the left sidebar:
 * a reverse-link index answering "where is this file or key referenced?"
 * (href, conref, conkeyref, keyref, image src, xi:include).
 */
public class WhereUsedPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(WhereUsedPlugin.class);
    private static final String SIDEBAR_ID = "whereUsed";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/list-tree.png";
    private static final int DEBOUNCE_MS = 400;

    private PluginContext context;
    private WhereUsedPanel panel;
    private Consumer<DocumentModifiedEvent> modifiedHandler;
    private Timer debounceTimer;
    private File pendingChangedFile;
    private com.dogsbay.xml.editor.EditorPopupContributors.Contributor popupContributor;

    /**
     * Context map chosen via "Go to Key Definition…" when no project
     * Default Root Map is configured. Session-only.
     */
    private File sessionContextMap;

    @Override
    public String getId() {
        return "com.dogsbay.where-used";
    }

    @Override
    public String getName() {
        return "Where Used";
    }

    @Override
    public String getDescription() {
        return "Find every reference to a file or key across the project";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;
        LOG.info("Activating Where Used plugin");
        try {
            DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
            panel = new WhereUsedPanel(editor);

            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "Where Used",
                    panel, UIService.SidebarPosition.LEFT);

            // Keep the index current as documents change. DocumentModifiedEvent
            // fires frequently — debounce per the standard 400ms pattern.
            debounceTimer = new Timer(DEBOUNCE_MS, e -> {
                File changed = pendingChangedFile;
                pendingChangedFile = null;
                if (changed != null && panel != null) {
                    panel.fileChanged(changed);
                }
            });
            debounceTimer.setRepeats(false);

            modifiedHandler = event -> {
                File file = fileOf(event);
                if (file != null) {
                    pendingChangedFile = file;
                    debounceTimer.restart();
                }
            };
            ctx.getEventBus().subscribe(DocumentModifiedEvent.class, modifiedHandler);

            // Editor right-click navigation: Go to Key Definition / Open
            // Target / Find Usages, when the click lands on a keyref,
            // conkeyref, href, conref, or src value.
            popupContributor = (editorPane, offset) ->
                    contributePopupItems(editor, ctx, editorPane, offset);
            ctx.getUIService().addEditorPopupContributor(popupContributor);

            LOG.info("Where Used plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate Where Used plugin", e);
        }
    }

    /**
     * Builds the navigation items for a right-click in the editor. Returns
     * an empty list (popup unchanged) unless the click lands on a keyref,
     * conkeyref, href, conref, or src value.
     */
    private java.util.List<javax.swing.JMenuItem> contributePopupItems(
            DogsBayAIEditor editor, PluginContext ctx,
            javax.swing.text.JTextComponent editorPane, int offset) {
        try {
            javax.swing.text.Document doc = editorPane.getDocument();
            String text = doc.getText(0, doc.getLength());
            AttributeReferenceLocator.Located located =
                    AttributeReferenceLocator.locate(text, offset);
            if (located == null) {
                return java.util.List.of();
            }

            File activeFile = activeFile(editor);
            File rootMap = editor.getDefaultRootMapFile();
            if (rootMap == null && sessionContextMap != null && sessionContextMap.isFile()) {
                rootMap = sessionContextMap;
            }
            KeySpace keySpace = rootMap != null
                    ? KeySpace.fromRootMap(rootMap) : KeySpace.empty();

            java.util.List<javax.swing.JMenuItem> menuItems = new java.util.ArrayList<>();
            for (KeyNavigationItems.Item item
                    : KeyNavigationItems.itemsFor(located, keySpace, activeFile, rootMap)) {
                menuItems.add(toMenuItem(item, ctx, activeFile, editor, rootMap));
            }
            return menuItems;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    private javax.swing.JMenuItem toMenuItem(KeyNavigationItems.Item item, PluginContext ctx,
                                             File activeFile, DogsBayAIEditor editor,
                                             File rootMap) {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(item.label());
        menuItem.setEnabled(item.enabled());
        if (item.tooltip() != null) {
            menuItem.setToolTipText(item.tooltip());
        }
        menuItem.addActionListener(e -> {
            switch (item.kind()) {
                case GOTO_DEFINITION:
                case OPEN_TARGET:
                    // Key-based navigation also refreshes the Keys tab with
                    // this key's definition + usages (without forcing the
                    // sidebar forward — navigation stays the primary action).
                    if (item.key() != null) {
                        panel.findUsagesOfKey(item.key());
                    }
                    if (item.file() != null) {
                        panel.navigateToFile(item.file(), item.line());
                    }
                    break;
                case GOTO_DEFINITION_PICK_MAP:
                    pickMapAndGoto(item.key(), activeFile);
                    break;
                case FIND_KEY_USAGES:
                    panel.findUsagesOfKey(item.key());
                    ctx.getUIService().showSidebarPanel(SIDEBAR_ID,
                            UIService.SidebarPosition.LEFT);
                    break;
                case FIND_FILE_USAGES:
                    panel.findUsagesOf(item.file());
                    ctx.getUIService().showSidebarPanel(SIDEBAR_ID,
                            UIService.SidebarPosition.LEFT);
                    break;
                case RENAME_KEY:
                    new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor)
                            .renameKey(item.key(), null);
                    break;
                case INLINE_KEY:
                    new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor)
                            .inlineKey(item.key(), rootMap, null);
                    break;
                case KEYIFY:
                    new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor)
                            .keyifyFile(item.file(), rootMap, null);
                    break;
                case INLINE_CONREF:
                    new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor)
                            .inlineConref(item.file(), item.key(), null);
                    break;
            }
        });
        return menuItem;
    }

    /**
     * "Go to Key Definition…" with no configured context map: prompt for a
     * .ditamap, remember it for the session, and navigate to the key's
     * definition in it (or explain that the key isn't defined there).
     */
    private void pickMapAndGoto(String key, File activeFile) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Choose context map for key resolution");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "DITA maps (*.ditamap)", "ditamap"));
        if (sessionContextMap != null) {
            chooser.setCurrentDirectory(sessionContextMap.getParentFile());
        } else if (activeFile != null) {
            chooser.setCurrentDirectory(activeFile.getParentFile());
        }
        if (chooser.showOpenDialog(panel) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        sessionContextMap = chosen;

        com.dogsbay.dogsbayaieditor.links.KeyDefinition def =
                KeySpace.fromRootMap(chosen).resolve(key);
        if (def != null) {
            panel.findUsagesOfKey(key);
            panel.navigateToFile(def.source(), def.line());
        } else {
            javax.swing.JOptionPane.showMessageDialog(panel,
                    "Key ‘" + key + "’ is not defined in " + chosen.getName()
                            + " (or its submaps).",
                    "Go to Key Definition", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static File activeFile(DogsBayAIEditor editor) {
        try {
            URL url = editor.getDocument().getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception ignored) {
            // unsaved / non-file document
        }
        return null;
    }

    private static File fileOf(DocumentModifiedEvent event) {
        try {
            URL url = event.document().getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception ignored) {
            // non-file documents don't affect the index
        }
        return null;
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating Where Used plugin");
        try {
            if (debounceTimer != null) {
                debounceTimer.stop();
            }
            if (context != null) {
                if (modifiedHandler != null) {
                    context.getEventBus().unsubscribe(DocumentModifiedEvent.class, modifiedHandler);
                }
                if (popupContributor != null) {
                    context.getUIService().removeEditorPopupContributor(popupContributor);
                }
                context.getUIService().removeSidebarPanel(SIDEBAR_ID,
                        UIService.SidebarPosition.LEFT);
            }
        } catch (Exception e) {
            LOG.error("Error during Where Used plugin deactivation", e);
        }
    }
}
