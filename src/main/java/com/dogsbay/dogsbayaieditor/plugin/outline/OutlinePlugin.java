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

package com.dogsbay.dogsbayaieditor.plugin.outline;

import java.util.function.Consumer;

import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.viewer.OutlinePanel;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentClosedEvent;

/**
 * Built-in plugin that provides the document outline panel in the right sidebar.
 * Switches between XML tree, Markdown headings, and AsciiDoc sections
 * depending on the active document's format.
 */
public class OutlinePlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(OutlinePlugin.class);
    private static final String SIDEBAR_ID = "outline";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/open-preview.png";

    private PluginContext context;
    private OutlinePanel outlinePanel;

    private Consumer<ActiveDocumentChangedEvent> activeDocHandler;
    private Consumer<DocumentClosedEvent> docClosedHandler;

    @Override
    public String getId() {
        return "com.dogsbay.outline";
    }

    @Override
    public String getName() {
        return "Outline";
    }

    @Override
    public String getDescription() {
        return "Document outline with format-aware views (XML, Markdown, AsciiDoc)";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating Outline plugin");

        try {
            // Get DogsBayAIEditor for OutlinePanel construction (transition API)
            DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();

            // Create the XML viewer for the outline
            Viewer xmlViewer = new Viewer(editor,
                    editor.getProperties().getViewerProperties(), null);

            // Create the composite outline panel
            outlinePanel = new OutlinePanel(editor, xmlViewer);

            // Register in right sidebar
            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "Outline",
                    outlinePanel, UIService.SidebarPosition.RIGHT);

            // Subscribe to document change events
            activeDocHandler = event -> {
                try {
                    outlinePanel.setDocument(event.document());
                } catch (Exception e) {
                    LOG.error("Error updating outline on document change", e);
                }
            };
            ctx.getEventBus().subscribe(ActiveDocumentChangedEvent.class, activeDocHandler);

            docClosedHandler = event -> {
                try {
                    // If the closed doc was the one being displayed, clear outline
                    DogsBayDocument activeDoc = ctx.getDocumentManager().getActiveDocument();
                    if (activeDoc == null) {
                        outlinePanel.setDocument(null);
                    }
                } catch (Exception e) {
                    LOG.error("Error updating outline on document close", e);
                }
            };
            ctx.getEventBus().subscribe(DocumentClosedEvent.class, docClosedHandler);

            // Initialize with current document if one is already open
            try {
                DogsBayDocument activeDoc = ctx.getDocumentManager().getActiveDocument();
                if (activeDoc != null) {
                    outlinePanel.setDocument(activeDoc);
                }
            } catch (Exception e) {
                LOG.debug("No active document at outline plugin activation time");
            }

            LOG.info("Outline plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate Outline plugin", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating Outline plugin");

        try {
            if (context != null && context.getEventBus() != null) {
                if (activeDocHandler != null) {
                    context.getEventBus().unsubscribe(ActiveDocumentChangedEvent.class, activeDocHandler);
                }
                if (docClosedHandler != null) {
                    context.getEventBus().unsubscribe(DocumentClosedEvent.class, docClosedHandler);
                }
            }

            if (context != null) {
                context.getUIService().removeSidebarPanel(SIDEBAR_ID,
                        UIService.SidebarPosition.RIGHT);
            }

            if (outlinePanel != null) {
                outlinePanel.cleanup();
            }
        } catch (Exception e) {
            LOG.error("Error during Outline plugin deactivation", e);
        }
    }

    /**
     * Returns the outline panel (for use by ViewManager during transition).
     */
    public OutlinePanel getOutlinePanel() {
        return outlinePanel;
    }
}
