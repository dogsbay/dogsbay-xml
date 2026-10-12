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

package com.dogsbay.dogsbayaieditor.plugin.inspector;

import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentModifiedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentOpenedEvent;

/**
 * Document Inspector plugin that shows document statistics and validation
 * problems in a bottom panel. This is a proof-of-concept plugin that
 * exercises the new plugin API.
 */
public class DocumentInspectorPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentInspectorPlugin.class);
    private static final int DEBOUNCE_DELAY_MS = 400;

    private PluginContext context;
    private DocumentInspectorPanel panel;
    private Timer debounceTimer;

    private Consumer<ActiveDocumentChangedEvent> activeDocHandler;
    private Consumer<DocumentModifiedEvent> modifiedHandler;
    private Consumer<DocumentOpenedEvent> openedHandler;

    @Override
    public String getId() {
        return "com.dogsbay.document-inspector";
    }

    @Override
    public String getName() {
        return "Document Inspector";
    }

    @Override
    public String getDescription() {
        return "Link checker and dirty diff viewer";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating Document Inspector plugin");

        try {
            panel = new DocumentInspectorPanel(ctx);

            // Add bottom panel
            ctx.getUIService().addBottomPanel("Inspector", panel);

            // Commented out rather than deleted: the command belongs in View, beside the preview
            // commands, and the three lines put it there whole. It asked for a Tools menu until
            // recently, which this editor does not have, so the item was dropped at every launch
            // and the command has never actually been in front of anyone. It stays that way for
            // now: the Links tab reads five attribute names and asks whether a relative path
            // exists, so a keyref, a conkeyref, a ditavalref branch and anything resolved
            // through the catalog are invisible to it — a weaker answer than Check Project
            // already gives, for one file. Whether that tab should exist decides whether this
            // command should. The Inspector tab in the bottom panel is unaffected, which is how
            // the panel has always been reached.
            //
            // JMenuItem menuItem = new JMenuItem("Document Inspector");
            // menuItem.addActionListener(e -> panel.refresh());
            // ctx.getUIService().addMenuItem(MenuBuilder.VIEW_MENU, menuItem);

            // Set up debounce timer for modification events
            debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> panel.onDocumentModified());
            debounceTimer.setRepeats(false);

            // Subscribe to events
            activeDocHandler = event -> {
                try {
                    panel.setDocument(event.document());
                } catch (Exception ex) {
                    LOG.error("Error handling active document change", ex);
                }
            };
            ctx.getEventBus().subscribe(ActiveDocumentChangedEvent.class, activeDocHandler);

            modifiedHandler = event -> {
                try {
                    debounceTimer.restart();
                } catch (Exception ex) {
                    LOG.error("Error handling document modification", ex);
                }
            };
            ctx.getEventBus().subscribe(DocumentModifiedEvent.class, modifiedHandler);

            openedHandler = event -> {
                try {
                    panel.setDocument(event.document());
                } catch (Exception ex) {
                    LOG.error("Error handling document opened", ex);
                }
            };
            ctx.getEventBus().subscribe(DocumentOpenedEvent.class, openedHandler);

            // Refresh with current document if one is already open
            try {
                com.dogsbay.xml.DogsBayDocument activeDoc = ctx.getDocumentManager().getActiveDocument();
                if (activeDoc != null) {
                    panel.setDocument(activeDoc);
                }
            } catch (Exception ex) {
                LOG.debug("No active document at plugin activation time");
            }

            LOG.info("Document Inspector plugin activated successfully");

        } catch (Exception e) {
            LOG.error("Failed to activate Document Inspector plugin", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating Document Inspector plugin");

        try {
            if (debounceTimer != null) {
                debounceTimer.stop();
            }

            if (context != null && context.getEventBus() != null) {
                if (activeDocHandler != null) {
                    context.getEventBus().unsubscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
                }
                if (modifiedHandler != null) {
                    context.getEventBus().unsubscribe(DocumentModifiedEvent.class, modifiedHandler);
                }
                if (openedHandler != null) {
                    context.getEventBus().unsubscribe(DocumentOpenedEvent.class, openedHandler);
                }
            }
        } catch (Exception e) {
            LOG.error("Error during Document Inspector deactivation", e);
        }
    }
}
