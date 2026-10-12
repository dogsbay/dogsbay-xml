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

package com.dogsbay.dogsbayaieditor.dita.ui;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.dogsbay.dogsbayaieditor.dita.DitaGroupNode;
import com.dogsbay.dogsbayaieditor.dita.DitaMapNode;
import com.dogsbay.dogsbayaieditor.dita.DitaNode;
import com.dogsbay.dogsbayaieditor.dita.DitaTopicNode;

/**
 * Custom renderer for DITA tree nodes.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DitaTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final long serialVersionUID = 1L;

    private ImageIcon mapIcon;
    private ImageIcon topicIcon;
    private ImageIcon groupIcon;
    private ImageIcon errorIcon;

    public DitaTreeCellRenderer() {
        try {
            // Load icons
            mapIcon = new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/Schema16.gif"));
            topicIcon = new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/DocumentIcon.gif"));
            groupIcon = new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/project/icons/FolderIcon.gif"));
            errorIcon = new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/Error16.gif"));
        } catch (Exception e) {
            System.err.println("Error loading DITA icons: " + e.getMessage());
        }
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DitaNode) {
            DitaNode node = (DitaNode) value;

            // The renderer component is reused across cells, so reset the font each
            // time from tree.getFont() (per CLAUDE.md — avoids size compounding and
            // stops the branch-node italic below from bleeding onto sibling rows).
            java.awt.Font base = tree.getFont();
            if (base != null) {
                setFont(base);
            }

            if (node instanceof DitaMapNode) {
                setIcon(mapIcon);
                // Highlight root map?
                if (((DitaMapNode) node).isRoot()) {
                    // Maybe bold font?
                }
            } else if (node instanceof DitaTopicNode) {
                setIcon(topicIcon);
            } else if (node instanceof DitaGroupNode) {
                setIcon(groupIcon);
            } else if (node instanceof com.dogsbay.dogsbayaieditor.dita.DitaBranchNode) {
                // DITA 1.3 branch-filter variant — italic so it reads as a derived
                // variant, not an authored topicref.
                setIcon(groupIcon);
                if (base != null) {
                    setFont(base.deriveFont(java.awt.Font.ITALIC));
                }
            }

            // Check for error state (hacky way: check title for "Error:")
            if (node.toString().contains("[Error:")) {
                setIcon(errorIcon);
            }

            // Tooltip
            if (node instanceof com.dogsbay.dogsbayaieditor.dita.DitaBranchNode) {
                String href = ((com.dogsbay.dogsbayaieditor.dita.DitaBranchNode) node)
                        .getDitavalHref();
                setToolTipText("DITA 1.3 branch filter (ditavalref)"
                        + (href != null ? " — " + href : ""));
            } else if (node.getResolvedFile() != null) {
                setToolTipText(node.getResolvedFile().getAbsolutePath());
            } else {
                setToolTipText(null);
            }
        }

        return this;
    }
}
