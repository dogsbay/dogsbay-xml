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

package com.dogsbay.dogsbayaieditor.search;

import com.dogsbay.dogsbayaieditor.IconFactory;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.project.Match;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Custom list cell renderer for search results that displays
 * file groups and matches in a VSCode-style hierarchical format.
 *
 * File groups are rendered as: [Icon] path/to/file.xml [5 matches]
 * Matches are rendered as:       [15,8] before **match** after
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class SearchResultCellRenderer extends JPanel implements ListCellRenderer<Object> {
    private static final Border NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
    private static final Color GRAY_TEXT = new Color(128, 128, 128);

    // Components for file group rendering
    private JLabel groupIcon;
    private JLabel groupPath;
    private JLabel groupCount;
    private JPanel groupPanel;

    // Components for match rendering
    private JLabel matchLine;
    private JLabel matchBefore;
    private JLabel matchHighlight;
    private JLabel matchAfter;
    private JPanel matchPanel;

    private File searchRoot;

    /**
     * Creates a new search result cell renderer.
     */
    public SearchResultCellRenderer() {
        this(null);
    }

    /**
     * Creates a new search result cell renderer with search root for relative paths.
     *
     * @param searchRoot the search root directory
     */
    public SearchResultCellRenderer(File searchRoot) {
        super(new BorderLayout());
        this.searchRoot = searchRoot;

        // Create panels for both rendering modes
        createGroupPanel();
        createMatchPanel();
    }

    /**
     * Creates the panel for rendering file groups.
     */
    private void createGroupPanel() {
        groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        groupPanel.setOpaque(false);

        groupIcon = new JLabel();

        groupPath = new JLabel();
        groupPath.setFont(groupPath.getFont().deriveFont(Font.BOLD));

        groupCount = new JLabel();
        groupCount.setFont(groupCount.getFont().deriveFont(Font.PLAIN));
        groupCount.setForeground(GRAY_TEXT);

        groupPanel.add(groupIcon);
        groupPanel.add(groupPath);
        groupPanel.add(groupCount);
    }

    /**
     * Creates the panel for rendering individual matches.
     */
    private void createMatchPanel() {
        matchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        matchPanel.setOpaque(false);

        // Add indentation
        JLabel indent = new JLabel("    ");
        matchPanel.add(indent);

        matchLine = new JLabel();
        matchLine.setFont(matchLine.getFont().deriveFont(Font.PLAIN));
        matchLine.setForeground(GRAY_TEXT);
        matchLine.setBorder(new EmptyBorder(0, 2, 0, 4));

        matchBefore = new JLabel();
        matchBefore.setFont(matchBefore.getFont().deriveFont(Font.PLAIN));

        matchHighlight = new JLabel();
        matchHighlight.setFont(matchHighlight.getFont().deriveFont(Font.BOLD));

        matchAfter = new JLabel();
        matchAfter.setFont(matchAfter.getFont().deriveFont(Font.PLAIN));

        matchPanel.add(matchLine);
        matchPanel.add(matchBefore);
        matchPanel.add(matchHighlight);
        matchPanel.add(matchAfter);
    }

    /**
     * Sets the search root for calculating relative paths.
     *
     * @param searchRoot the search root directory
     */
    public void setSearchRoot(File searchRoot) {
        this.searchRoot = searchRoot;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Object> list, Object value,
                                                 int index, boolean isSelected, boolean cellHasFocus) {
        // Clear previous content
        removeAll();

        if (value instanceof SearchResultGroup) {
            renderGroup((SearchResultGroup) value, list, isSelected);
        } else if (value instanceof Match) {
            renderMatch((Match) value, list, isSelected);
        }

        // Apply selection colors
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            applySelectionColors(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            applyNormalColors(list.getForeground());
        }

        setEnabled(list.isEnabled());
        setBorder(cellHasFocus ? UIManager.getBorder("List.focusCellHighlightBorder") : NO_FOCUS_BORDER);

        return this;
    }

    /**
     * Renders a file group header.
     */
    private void renderGroup(SearchResultGroup group, JList<?> list, boolean isSelected) {
        add(groupPanel, BorderLayout.CENTER);

        File file = group.getFile();
        String fileName = file.getName();
        String extension = URLUtilities.getExtension(fileName);

        if (extension == null) {
            extension = "";
        }

        // Set icon based on file extension
        groupIcon.setIcon(IconFactory.getIconForExtension(extension));

        // Set path (relative if possible)
        String displayPath = group.getRelativePath(searchRoot);
        groupPath.setText(displayPath);

        // Set match count
        int count = group.getMatchCount();
        groupCount.setText("[" + count + (count == 1 ? " match]" : " matches]"));

        // Set tooltip with full path
        setToolTipText(file.getAbsolutePath());
    }

    /**
     * Renders an individual match line.
     */
    private void renderMatch(Match match, JList<?> list, boolean isSelected) {
        add(matchPanel, BorderLayout.CENTER);

        // Set line number and column
        matchLine.setText("[" + match.getLineNumber() + "," + (match.getStart() + 1) + "]");

        // Set match content with highlighting
        String lineValue = match.getLineValue();
        if (lineValue != null && match.getLineNumber() != -1) {
            int start = match.getStart();
            int end = match.getEnd();

            // Trim long lines for display
            String before = start > 0 ? lineValue.substring(0, start) : "";
            String highlight = (start < lineValue.length() && end <= lineValue.length()) ?
                             lineValue.substring(start, end) : "";
            String after = end < lineValue.length() ? lineValue.substring(end) : "";

            // Limit length of before/after context
            if (before.length() > 50) {
                before = "..." + before.substring(before.length() - 47);
            }
            if (after.length() > 100) {
                after = after.substring(0, 97) + "...";
            }

            matchBefore.setText(before);
            matchHighlight.setText(highlight);
            matchAfter.setText(after);
        } else {
            matchBefore.setText(lineValue != null ? lineValue : "");
            matchHighlight.setText("");
            matchAfter.setText("");
        }

        // Set tooltip
        setToolTipText(match.getURL() + " [" + match.getLineNumber() + "," + (match.getStart() + 1) + "]");
    }

    /**
     * Applies selection foreground color to all labels.
     */
    private void applySelectionColors(Color foreground) {
        // Group components
        groupPath.setForeground(foreground);
        groupCount.setForeground(foreground);

        // Match components
        matchLine.setForeground(foreground);
        matchBefore.setForeground(foreground);
        matchHighlight.setForeground(foreground);
        matchAfter.setForeground(foreground);
    }

    /**
     * Applies normal foreground colors to all labels.
     */
    private void applyNormalColors(Color foreground) {
        // Group components
        groupPath.setForeground(foreground);
        groupCount.setForeground(GRAY_TEXT);

        // Match components
        matchLine.setForeground(GRAY_TEXT);
        matchBefore.setForeground(foreground);
        matchHighlight.setForeground(foreground);
        matchAfter.setForeground(foreground);
    }
}
