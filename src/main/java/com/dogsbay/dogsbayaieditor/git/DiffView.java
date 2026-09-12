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

package com.dogsbay.dogsbayaieditor.git;

import com.dogsbay.dogsbayaieditor.ViewPanel;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 * A custom ViewPanel for displaying Git diffs with syntax highlighting.
 * Extends ViewPanel to integrate properly with the DogsBayAIEditor tab system.
 *
 * This view displays diffs with color coding:
 * - Added lines: green text on light green background
 * - Removed lines: red text on light red background
 * - Context lines: normal black text
 * - Header lines: bold gray text
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DiffView extends ViewPanel {
    private static final boolean DEBUG = false;

    private JTextPane diffTextPane;
    private StyledDocument diffDocument;

    // Style attributes for diff highlighting
    private SimpleAttributeSet addedStyle;
    private SimpleAttributeSet removedStyle;
    private SimpleAttributeSet contextStyle;
    private SimpleAttributeSet headerStyle;

    /**
     * Creates a new DiffView with empty content.
     */
    public DiffView() {
        super(new BorderLayout());
        initializeUI();
        initializeStyles();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        // Create text pane for diff display
        diffTextPane = new JTextPane();
        diffTextPane.setEditable(false);
        diffTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        diffDocument = diffTextPane.getStyledDocument();

        // Add to scroll pane
        JScrollPane scrollPane = new JScrollPane(diffTextPane);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Initializes the text styles for diff highlighting.
     */
    private void initializeStyles() {
        // Added lines (green text on light green background)
        addedStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(addedStyle, new Color(0, 128, 0));
        StyleConstants.setBackground(addedStyle, new Color(230, 255, 230));

        // Removed lines (red text on light red background)
        removedStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(removedStyle, new Color(178, 34, 34));
        StyleConstants.setBackground(removedStyle, new Color(255, 230, 230));

        // Context lines (normal)
        contextStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(contextStyle, Color.BLACK);

        // Header lines (bold, gray)
        headerStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(headerStyle, new Color(100, 100, 100));
        StyleConstants.setBold(headerStyle, true);
    }

    /**
     * Sets the content of the diff view.
     * Applies syntax highlighting based on diff format.
     *
     * @param diffText the unified diff text to display
     */
    public void setContent(String diffText) {
        try {
            // Clear existing content
            diffDocument.remove(0, diffDocument.getLength());

            if (diffText == null || diffText.trim().isEmpty()) {
                diffDocument.insertString(0, "No diff content available", contextStyle);
                return;
            }

            // Parse and insert diff line by line with appropriate styling
            String[] lines = diffText.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                SimpleAttributeSet style = getStyleForLine(line);

                // Insert line with style
                diffDocument.insertString(diffDocument.getLength(), line, style);

                // Add newline (except for last line)
                if (i < lines.length - 1) {
                    diffDocument.insertString(diffDocument.getLength(), "\n", style);
                }
            }

            // Scroll to top
            SwingUtilities.invokeLater(() -> {
                diffTextPane.setCaretPosition(0);
            });

        } catch (BadLocationException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Determines the appropriate style for a diff line based on its prefix.
     *
     * @param line the line to analyze
     * @return the appropriate AttributeSet for styling
     */
    private SimpleAttributeSet getStyleForLine(String line) {
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return addedStyle;
        } else if (line.startsWith("-") && !line.startsWith("---")) {
            return removedStyle;
        } else if (line.startsWith("@@") || line.startsWith("diff ") ||
                   line.startsWith("index ") || line.startsWith("---") ||
                   line.startsWith("+++")) {
            return headerStyle;
        } else {
            return contextStyle;
        }
    }

    /**
     * Sets focus on the diff text pane.
     * Required by ViewPanel.
     */
    @Override
    public void setFocus() {
        diffTextPane.requestFocus();
    }

    /**
     * Updates preferences for the diff view.
     * Currently no preferences to update.
     * Required by ViewPanel.
     */
    @Override
    public void updatePreferences() {
        // No preferences to update currently
        // Future: could update font, colors, etc. from preferences
    }

    /**
     * Sets properties for the diff view.
     * Currently no properties to set.
     * Required by ViewPanel.
     */
    @Override
    public void setProperties() {
        // No properties to set currently
        // Future: could set view-specific properties
    }

    /**
     * Gets the text pane component (for testing or advanced usage).
     *
     * @return the JTextPane displaying the diff
     */
    public JTextPane getTextPane() {
        return diffTextPane;
    }
}
