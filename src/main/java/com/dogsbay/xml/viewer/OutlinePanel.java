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

package com.dogsbay.xml.viewer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.DocumentFormat;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;

/**
 * Composite outline panel that switches between format-specific outlines
 * depending on the active document type. Format outlines are created
 * on demand via {@link DocumentFormat#createOutlinePanel(Object)}.
 * Falls back to the XML Viewer for formats that don't provide an outline.
 */
public class OutlinePanel extends ViewTreePanel {

    private static final String CARD_XML = "xml";
    private static final String CARD_EMPTY = "empty";

    private final DogsBayAIEditor parent;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    private Viewer xmlViewer;
    private JPanel emptyPanel;

    /** Format-specific outline panels, keyed by format name. */
    private final Map<String, FormatOutlinePanel> formatPanels = new HashMap<>();
    private String activeCard = CARD_EMPTY;
    private FormatOutlinePanel activeFormatPanel = null;

    public OutlinePanel(DogsBayAIEditor parent, Viewer xmlViewer) {
        super(new BorderLayout());

        this.parent = parent;
        this.xmlViewer = xmlViewer;
        this.emptyPanel = new JPanel();

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(xmlViewer, CARD_XML);
        cardPanel.add(emptyPanel, CARD_EMPTY);

        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, CARD_EMPTY);
    }

    /**
     * Sets the document and switches to the appropriate outline view
     * based on the document's format.
     */
    public void setDocument(DogsBayDocument document) {
        // Clear the previously active format panel
        if (activeFormatPanel != null) {
            activeFormatPanel.setDocument(null);
            activeFormatPanel = null;
        }
        xmlViewer.setDocument(null);

        if (document != null) {
            DocumentFormat format = document.getDocumentFormat();
            String formatName = (format != null) ? format.getName() : "";

            // Try to get or create a format-specific outline panel
            FormatOutlinePanel formatPanel = getOrCreateFormatPanel(format, formatName);

            if (formatPanel != null) {
                formatPanel.setDocument(document);
                activeFormatPanel = formatPanel;
                activeCard = formatName;
            } else {
                // Fall back to XML tree viewer
                xmlViewer.setDocument(document);
                activeCard = CARD_XML;
            }
        } else {
            activeCard = CARD_EMPTY;
        }
        cardLayout.show(cardPanel, activeCard);
    }

    /**
     * Gets an existing format panel or creates one via DocumentFormat.createOutlinePanel().
     */
    private FormatOutlinePanel getOrCreateFormatPanel(DocumentFormat format, String formatName) {
        if (format == null || formatName.isEmpty()) return null;

        // Check if we already have a panel for this format
        FormatOutlinePanel existing = formatPanels.get(formatName);
        if (existing != null) return existing;

        // Ask the format to create one
        JComponent component = format.createOutlinePanel(parent);
        if (component instanceof FormatOutlinePanel) {
            FormatOutlinePanel panel = (FormatOutlinePanel) component;
            formatPanels.put(formatName, panel);
            cardPanel.add(component, formatName);
            return panel;
        }

        return null;
    }

    public Viewer getXmlViewer() {
        return xmlViewer;
    }

    public void expandAll() {
        if (activeFormatPanel != null) {
            activeFormatPanel.expandAll();
        } else if (CARD_XML.equals(activeCard)) {
            xmlViewer.expandAll();
        }
    }

    public void collapseAll() {
        if (activeFormatPanel != null) {
            activeFormatPanel.collapseAll();
        } else if (CARD_XML.equals(activeCard)) {
            xmlViewer.collapseAll();
        }
    }

    public void setSelectedElement(XElement element, boolean end, int y) {
        if (CARD_XML.equals(activeCard)) {
            xmlViewer.setSelectedElement(element, end, y);
        }
    }

    public void addSelectedElement(XElement element) {
        if (CARD_XML.equals(activeCard)) {
            xmlViewer.addSelectedElement(element);
        }
    }

    public XElement getSelectedElement() {
        if (CARD_XML.equals(activeCard)) {
            return xmlViewer.getSelectedElement();
        }
        return null;
    }

    public void clearSelection() {
        if (CARD_XML.equals(activeCard)) {
            xmlViewer.clearSelection();
        }
    }

    public boolean hasLatestInformation() {
        if (CARD_XML.equals(activeCard)) {
            return xmlViewer.hasLatestInformation();
        }
        return true;
    }

    public void setFocus() {
        if (activeFormatPanel != null) {
            activeFormatPanel.setFocus();
        } else if (CARD_XML.equals(activeCard)) {
            xmlViewer.setFocus();
        }
    }

    public void setProperties() {
    }

    public void updatePreferences() {
        xmlViewer.updatePreferences();
        for (FormatOutlinePanel panel : formatPanels.values()) {
            panel.updatePreferences();
        }
    }

    public void updateHelper() {
        if (CARD_XML.equals(activeCard)) {
            xmlViewer.updateHelper();
        }
    }

    public void cleanup() {
        xmlViewer.cleanup();
        for (FormatOutlinePanel panel : formatPanels.values()) {
            panel.cleanup();
        }
        formatPanels.clear();
        removeAll();
    }
}
