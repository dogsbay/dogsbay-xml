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

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;

/**
 * Document Inspector panel with Links and Changes tabs.
 * Added as a tab in the bottom output panel.
 */
public class DocumentInspectorPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentInspectorPanel.class);

    static final int TAB_LINKS = 0;
    static final int TAB_CHANGES = 1;

    private final PluginContext context;
    private DogsBayDocument currentDocument;

    private final JTabbedPane tabbedPane;
    private final LinkCheckerPanel linkCheckerPanel;
    private final ChangesPanel changesPanel;

    public DocumentInspectorPanel(PluginContext context) {
        super(new BorderLayout());
        this.context = context;

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        // --- Links tab ---
        linkCheckerPanel = new LinkCheckerPanel(context);
        tabbedPane.addTab("Links", linkCheckerPanel);

        // --- Changes tab ---
        changesPanel = new ChangesPanel(context);
        tabbedPane.addTab("Changes", changesPanel);

        // Lazy refresh on tab switch
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int selectedIndex = tabbedPane.getSelectedIndex();
                if (selectedIndex == TAB_LINKS && linkCheckerPanel.needsRefresh()) {
                    linkCheckerPanel.checkLinks();
                } else if (selectedIndex == TAB_CHANGES && changesPanel.needsRefresh()) {
                    changesPanel.refreshDiff();
                }
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Sets the document to inspect and refreshes the active tab.
     */
    public void setDocument(DogsBayDocument document) {
        this.currentDocument = document;
        linkCheckerPanel.setDocument(document);
        changesPanel.setDocument(document);

        // Refresh the currently visible tab immediately
        int selectedTab = tabbedPane.getSelectedIndex();
        if (selectedTab == TAB_LINKS) {
            linkCheckerPanel.checkLinks();
        } else if (selectedTab == TAB_CHANGES) {
            changesPanel.refreshDiff();
        }
    }

    /**
     * Called when the document is modified. Auto-refreshes the
     * currently visible tab.
     */
    public void onDocumentModified() {
        int selectedTab = tabbedPane.getSelectedIndex();
        if (selectedTab == TAB_CHANGES) {
            changesPanel.refreshDiff();
        } else if (selectedTab == TAB_LINKS) {
            linkCheckerPanel.checkLinks();
        }
    }

    /**
     * Refreshes both tabs.
     */
    public void refresh() {
        linkCheckerPanel.checkLinks();
        changesPanel.refreshDiff();
    }

    /**
     * Returns the tabbed pane for testing purposes.
     */
    JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    /**
     * Returns the link checker panel for testing purposes.
     */
    LinkCheckerPanel getLinkCheckerPanel() {
        return linkCheckerPanel;
    }

    /**
     * Returns the changes panel for testing purposes.
     */
    ChangesPanel getChangesPanel() {
        return changesPanel;
    }
}
