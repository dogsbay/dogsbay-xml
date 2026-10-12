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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;

/**
 * Panel that displays a unified diff between the current editor buffer
 * and the saved file on disk.
 */
public class ChangesPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(ChangesPanel.class);

    private final PluginContext context;
    private DogsBayDocument currentDocument;
    private boolean needsRefresh = true;

    private static final int AUTO_REFRESH_MS = 2000;

    private final JTextPane diffPane;
    private final JLabel summaryLabel;
    private final javax.swing.Timer autoRefreshTimer;

    // Styles for diff coloring
    private Style addedStyle;
    private Style removedStyle;
    private Style headerStyle;
    private Style normalStyle;

    public ChangesPanel(PluginContext context) {
        super(new BorderLayout(0, 2));
        this.context = context;

        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Summary label
        summaryLabel = new JLabel("No document open");
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        topBar.add(summaryLabel);
        add(topBar, BorderLayout.NORTH);

        // Diff display
        diffPane = new JTextPane();
        diffPane.setEditable(false);
        diffPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        initStyles();

        add(new JScrollPane(diffPane), BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshDiff());
        buttonBar.add(refreshButton);
        add(buttonBar, BorderLayout.SOUTH);

        // Auto-refresh timer — runs while panel is visible
        autoRefreshTimer = new javax.swing.Timer(AUTO_REFRESH_MS, e -> {
            if (isShowing() && currentDocument != null) {
                refreshDiff();
            }
        });
        autoRefreshTimer.setRepeats(true);

        // Start/stop auto-refresh based on visibility
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    autoRefreshTimer.start();
                } else {
                    autoRefreshTimer.stop();
                }
            }
        });
    }

    private void initStyles() {
        StyledDocument doc = diffPane.getStyledDocument();

        addedStyle = doc.addStyle("added", null);
        StyleConstants.setForeground(addedStyle, new Color(0, 128, 0));

        removedStyle = doc.addStyle("removed", null);
        StyleConstants.setForeground(removedStyle, new Color(200, 0, 0));

        headerStyle = doc.addStyle("header", null);
        StyleConstants.setForeground(headerStyle, new Color(0, 80, 180));
        StyleConstants.setBold(headerStyle, true);

        normalStyle = doc.addStyle("normal", null);
        StyleConstants.setForeground(normalStyle, diffPane.getForeground());
    }

    /**
     * Sets the document and marks the panel as needing refresh.
     */
    public void setDocument(DogsBayDocument document) {
        this.currentDocument = document;
        this.needsRefresh = true;
        clearDiff();
        if (document == null) {
            summaryLabel.setText("No document open");
        } else {
            summaryLabel.setText("Click 'Refresh' to compare with saved version");
        }
    }

    /**
     * Returns true if a refresh is needed (for lazy loading on tab switch).
     */
    public boolean needsRefresh() {
        return needsRefresh;
    }

    /**
     * Marks the panel as needing refresh (called on document modifications).
     */
    public void markDirty() {
        this.needsRefresh = true;
    }

    /**
     * Computes and displays the diff between editor buffer and saved file.
     */
    public void refreshDiff() {
        needsRefresh = false;

        if (currentDocument == null) {
            clearDiff();
            summaryLabel.setText("No document open");
            return;
        }

        // Check if document has a file URL
        URL url = currentDocument.getURL();
        if (url == null || !"file".equals(url.getProtocol())) {
            clearDiff();
            summaryLabel.setText("Document has not been saved yet");
            return;
        }

        try {
            // Get live editor buffer text (not DogsBayDocument.getText() which
            // may not reflect unsaved edits in the Swing editor)
            String currentText = getLiveEditorText();
            if (currentText == null) {
                // Fall back to document model text
                currentText = currentDocument.getText();
            }
            if (currentText == null) {
                clearDiff();
                summaryLabel.setText("No content available");
                return;
            }

            // Read saved file from disk
            File file = new File(url.toURI());
            if (!file.exists()) {
                clearDiff();
                summaryLabel.setText("File not found on disk");
                return;
            }

            // Read with document's encoding if available
            String encoding = currentDocument.getJavaEncoding();
            String savedText;
            if (encoding != null && !encoding.isEmpty()) {
                savedText = Files.readString(file.toPath(), java.nio.charset.Charset.forName(encoding));
            } else {
                savedText = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }

            // Check if identical
            if (currentText.equals(savedText)) {
                clearDiff();
                summaryLabel.setText("No unsaved changes");
                return;
            }

            // Compute diff
            String[] currentLines = currentText.split("\n", -1);
            String[] savedLines = savedText.split("\n", -1);

            List<DiffLine> diff = computeDiff(savedLines, currentLines);
            displayDiff(diff, file.getName());

            long added = diff.stream().filter(d -> d.type == DiffType.ADDED).count();
            long removed = diff.stream().filter(d -> d.type == DiffType.REMOVED).count();
            summaryLabel.setText("Changes: +" + added + " / -" + removed + " lines");

        } catch (Exception e) {
            LOG.error("Error computing diff", e);
            clearDiff();
            summaryLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Gets the live text from the Swing editor buffer, which reflects
     * unsaved edits. Returns null if unavailable.
     */
    private String getLiveEditorText() {
        try {
            DogsBayView view = context.getViewManager().getView();
            if (view != null && view.getEditor() != null) {
                javax.swing.text.Document swingDoc = view.getEditor().getEditor().getDocument();
                if (swingDoc != null) {
                    return swingDoc.getText(0, swingDoc.getLength());
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not get live editor text", e);
        }
        return null;
    }

    private void clearDiff() {
        diffPane.setText("");
    }

    private void displayDiff(List<DiffLine> diff, String filename) {
        StyledDocument doc = diffPane.getStyledDocument();
        diffPane.setText("");

        try {
            // File header
            doc.insertString(doc.getLength(), "--- " + filename + " (saved)\n", headerStyle);
            doc.insertString(doc.getLength(), "+++ " + filename + " (buffer)\n", headerStyle);
            doc.insertString(doc.getLength(), "\n", normalStyle);

            for (DiffLine line : diff) {
                Style style;
                String prefix;
                switch (line.type) {
                    case ADDED:
                        style = addedStyle;
                        prefix = "+ ";
                        break;
                    case REMOVED:
                        style = removedStyle;
                        prefix = "- ";
                        break;
                    case CONTEXT:
                        style = normalStyle;
                        prefix = "  ";
                        break;
                    case HEADER:
                        style = headerStyle;
                        prefix = "";
                        break;
                    default:
                        style = normalStyle;
                        prefix = "  ";
                }
                doc.insertString(doc.getLength(), prefix + line.text + "\n", style);
            }
        } catch (BadLocationException e) {
            LOG.error("Error displaying diff", e);
        }

        // Scroll to top
        diffPane.setCaretPosition(0);
    }

    /**
     * Computes a unified diff between old and new line arrays using a simple
     * LCS-based algorithm. Produces context lines around changes.
     */
    static List<DiffLine> computeDiff(String[] oldLines, String[] newLines) {
        // Compute LCS table
        int oldLen = oldLines.length;
        int newLen = newLines.length;

        // Use short arrays for memory efficiency on large files
        // For very large files, fall back to simple comparison
        if ((long) oldLen * newLen > 10_000_000L) {
            return computeSimpleDiff(oldLines, newLines);
        }

        int[][] lcs = new int[oldLen + 1][newLen + 1];
        for (int i = oldLen - 1; i >= 0; i--) {
            for (int j = newLen - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        // Walk the LCS to produce diff hunks
        List<RawDiffLine> rawDiff = new ArrayList<>();
        int i = 0, j = 0;
        while (i < oldLen || j < newLen) {
            if (i < oldLen && j < newLen && oldLines[i].equals(newLines[j])) {
                rawDiff.add(new RawDiffLine(DiffType.CONTEXT, oldLines[i], i + 1, j + 1));
                i++;
                j++;
            } else if (j < newLen && (i >= oldLen || lcs[i][j + 1] >= lcs[i + 1][j])) {
                rawDiff.add(new RawDiffLine(DiffType.ADDED, newLines[j], -1, j + 1));
                j++;
            } else if (i < oldLen) {
                rawDiff.add(new RawDiffLine(DiffType.REMOVED, oldLines[i], i + 1, -1));
                i++;
            }
        }

        // Convert to unified diff with context (3 lines)
        return formatUnifiedDiff(rawDiff);
    }

    /**
     * Simple line-by-line diff for very large files where LCS would be too expensive.
     */
    private static List<DiffLine> computeSimpleDiff(String[] oldLines, String[] newLines) {
        List<DiffLine> result = new ArrayList<>();
        int maxLen = Math.max(oldLines.length, newLines.length);

        result.add(new DiffLine(DiffType.HEADER,
                "@@ file too large for full diff, showing line-by-line comparison @@"));

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;

            if (oldLine != null && newLine != null && oldLine.equals(newLine)) {
                // Skip context in simple mode for brevity
                continue;
            }

            if (oldLine != null && (newLine == null || !oldLine.equals(newLine))) {
                result.add(new DiffLine(DiffType.REMOVED, oldLine));
            }
            if (newLine != null && (oldLine == null || !oldLine.equals(newLine))) {
                result.add(new DiffLine(DiffType.ADDED, newLine));
            }
        }

        return result;
    }

    /**
     * Formats raw diff lines into unified diff with hunk headers and context.
     */
    private static List<DiffLine> formatUnifiedDiff(List<RawDiffLine> rawDiff) {
        List<DiffLine> result = new ArrayList<>();
        int contextSize = 3;

        // Find change regions
        List<int[]> changeRegions = new ArrayList<>();
        int regionStart = -1;
        for (int i = 0; i < rawDiff.size(); i++) {
            if (rawDiff.get(i).type != DiffType.CONTEXT) {
                if (regionStart == -1) {
                    regionStart = i;
                }
            } else {
                if (regionStart != -1) {
                    changeRegions.add(new int[]{regionStart, i - 1});
                    regionStart = -1;
                }
            }
        }
        if (regionStart != -1) {
            changeRegions.add(new int[]{regionStart, rawDiff.size() - 1});
        }

        if (changeRegions.isEmpty()) {
            result.add(new DiffLine(DiffType.CONTEXT, "(no differences)"));
            return result;
        }

        // Merge overlapping regions (with context)
        List<int[]> mergedRegions = new ArrayList<>();
        for (int[] region : changeRegions) {
            int start = Math.max(0, region[0] - contextSize);
            int end = Math.min(rawDiff.size() - 1, region[1] + contextSize);

            if (!mergedRegions.isEmpty()) {
                int[] last = mergedRegions.get(mergedRegions.size() - 1);
                if (start <= last[1] + 1) {
                    last[1] = end;
                    continue;
                }
            }
            mergedRegions.add(new int[]{start, end});
        }

        // Output hunks
        for (int[] region : mergedRegions) {
            // Calculate hunk header
            int oldStart = -1, oldCount = 0, newStart = -1, newCount = 0;
            for (int i = region[0]; i <= region[1]; i++) {
                RawDiffLine rdl = rawDiff.get(i);
                if (rdl.type == DiffType.CONTEXT || rdl.type == DiffType.REMOVED) {
                    if (oldStart == -1) oldStart = rdl.oldLine;
                    oldCount++;
                }
                if (rdl.type == DiffType.CONTEXT || rdl.type == DiffType.ADDED) {
                    if (newStart == -1) newStart = rdl.newLine;
                    newCount++;
                }
            }
            if (oldStart == -1) oldStart = 1;
            if (newStart == -1) newStart = 1;

            result.add(new DiffLine(DiffType.HEADER,
                    "@@ -" + oldStart + "," + oldCount
                            + " +" + newStart + "," + newCount + " @@"));

            for (int i = region[0]; i <= region[1]; i++) {
                RawDiffLine rdl = rawDiff.get(i);
                result.add(new DiffLine(rdl.type, rdl.text));
            }
        }

        return result;
    }

    // --- Inner classes ---

    enum DiffType {
        CONTEXT, ADDED, REMOVED, HEADER
    }

    static class DiffLine {
        final DiffType type;
        final String text;

        DiffLine(DiffType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static class RawDiffLine {
        final DiffType type;
        final String text;
        final int oldLine;
        final int newLine;

        RawDiffLine(DiffType type, String text, int oldLine, int newLine) {
            this.type = type;
            this.text = text;
            this.oldLine = oldLine;
            this.newLine = newLine;
        }
    }
}
