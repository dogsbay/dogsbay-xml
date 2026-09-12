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

package com.dogsbay.xml.editor;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;

/**
 * A VSCode-style find/replace bar for the Editor.
 * Displays as a compact horizontal bar at the top of the editor with:
 * - Find text field with match case, whole word, regex toggles
 * - Find next/previous navigation
 * - Optional replace mode with replace text field and buttons
 * - Find in selection toggle
 */
public class FindBar extends JPanel {
    private static final long serialVersionUID = 1L;

    private Editor editor;

    // UI Components - Search
    private JTextField findField;
    private JCheckBox matchCaseCheckBox;
    private JCheckBox matchWholeWordCheckBox;
    private JCheckBox useRegexCheckBox;
    private JCheckBox findInSelectionCheckBox;
    private JButton findPreviousButton;
    private JButton findNextButton;
    private JButton closeButton;
    private JLabel matchCountLabel;

    // UI Components - Replace
    private JPanel replacePanel;
    private JTextField replaceField;
    private JButton replaceButton;
    private JButton replaceAllButton;
    private JButton replaceToggleButton;

    // State
    private boolean replaceMode = false;
    private int currentMatchIndex = -1;
    private int totalMatches = 0;

    // Highlighting
    private List<Object> highlightTags = new ArrayList<>();
    private Highlighter.HighlightPainter matchPainter;
    private Highlighter.HighlightPainter currentMatchPainter;

    public FindBar(Editor editor) {
        this.editor = editor;

        // Initialize highlighters with distinctive colors
        matchPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 0, 100)); // Yellow with transparency
        currentMatchPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 165, 0, 150)); // Orange for current match

        initComponents();
        layoutComponents();
        setVisible(false); // Hidden by default
    }

    private void initComponents() {
        // Find field
        findField = new JTextField(20);
        findField.setToolTipText("Find");
        findField.addActionListener(e -> findNext());
        findField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
        });

        // Replace field
        replaceField = new JTextField(20);
        replaceField.setToolTipText("Replace");
        replaceField.addActionListener(e -> replace());

        // Match case toggle (Aa)
        matchCaseCheckBox = new JCheckBox("Aa");
        matchCaseCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        matchCaseCheckBox.setToolTipText("Match Case");
        matchCaseCheckBox.setFocusable(false);
        matchCaseCheckBox.setMargin(new Insets(2, 4, 2, 4));
        matchCaseCheckBox.addActionListener(e -> updateSearch());

        // Match whole word toggle (A͟B)
        matchWholeWordCheckBox = new JCheckBox("<html>A<u>B</u></html>");
        matchWholeWordCheckBox.setToolTipText("Match Whole Word");
        matchWholeWordCheckBox.setFocusable(false);
        matchWholeWordCheckBox.setMargin(new Insets(2, 4, 2, 4));
        matchWholeWordCheckBox.addActionListener(e -> updateSearch());

        // Use regex toggle (.*)
        useRegexCheckBox = new JCheckBox(".*");
        useRegexCheckBox.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        useRegexCheckBox.setToolTipText("Use Regular Expression");
        useRegexCheckBox.setFocusable(false);
        useRegexCheckBox.setMargin(new Insets(2, 4, 2, 4));
        useRegexCheckBox.addActionListener(e -> updateSearch());

        // Find in selection toggle
        findInSelectionCheckBox = new JCheckBox("<html>A<u>B</u></html>");
        findInSelectionCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        findInSelectionCheckBox.setToolTipText("Find in Selection");
        findInSelectionCheckBox.setFocusable(false);
        findInSelectionCheckBox.setMargin(new Insets(2, 4, 2, 4));
        findInSelectionCheckBox.addActionListener(e -> updateSearch());

        // Find previous button
        findPreviousButton = new JButton("↑");
        findPreviousButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        findPreviousButton.setToolTipText("Previous Match (Shift+F3)");
        findPreviousButton.setFocusable(false);
        findPreviousButton.setMargin(new Insets(2, 8, 2, 8));
        findPreviousButton.addActionListener(e -> findPrevious());

        // Find next button
        findNextButton = new JButton("↓");
        findNextButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        findNextButton.setToolTipText("Next Match (F3)");
        findNextButton.setFocusable(false);
        findNextButton.setMargin(new Insets(2, 8, 2, 8));
        findNextButton.addActionListener(e -> findNext());

        // Match count label
        matchCountLabel = new JLabel("");
        matchCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        matchCountLabel.setBorder(new EmptyBorder(0, 5, 0, 5));

        // Replace toggle button
        replaceToggleButton = new JButton("▶");
        replaceToggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        replaceToggleButton.setToolTipText("Toggle Replace");
        replaceToggleButton.setFocusable(false);
        replaceToggleButton.setMargin(new Insets(2, 6, 2, 6));
        replaceToggleButton.addActionListener(e -> toggleReplaceMode());

        // Replace button
        replaceButton = new JButton("Replace");
        replaceButton.setToolTipText("Replace (Ctrl+Shift+1)");
        replaceButton.setFocusable(false);
        replaceButton.setMargin(new Insets(2, 8, 2, 8));
        replaceButton.addActionListener(e -> replace());

        // Replace all button
        replaceAllButton = new JButton("Replace All");
        replaceAllButton.setToolTipText("Replace All (Ctrl+Shift+Alt+1)");
        replaceAllButton.setFocusable(false);
        replaceAllButton.setMargin(new Insets(2, 8, 2, 8));
        replaceAllButton.addActionListener(e -> replaceAll());

        // Close button
        closeButton = new JButton("✕");
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        closeButton.setToolTipText("Close (Escape)");
        closeButton.setFocusable(false);
        closeButton.setMargin(new Insets(2, 8, 2, 8));
        closeButton.addActionListener(e -> hideFindBar());

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
    }

    private void layoutComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, UIManager.getColor("controlShadow")),
            new EmptyBorder(5, 10, 5, 10)
        ));
        setBackground(UIManager.getColor("Panel.background"));

        // Find panel (always visible when FindBar is shown)
        JPanel findPanel = new JPanel();
        findPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        findPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        findPanel.add(replaceToggleButton);
        findPanel.add(new JLabel("Find:"));
        findPanel.add(findField);
        findPanel.add(findPreviousButton);
        findPanel.add(findNextButton);
        findPanel.add(matchCaseCheckBox);
        findPanel.add(matchWholeWordCheckBox);
        findPanel.add(useRegexCheckBox);
        findPanel.add(findInSelectionCheckBox);
        findPanel.add(matchCountLabel);
        findPanel.add(Box.createHorizontalGlue());
        findPanel.add(closeButton);

        add(findPanel);

        // Replace panel (shown/hidden based on replace mode)
        replacePanel = new JPanel();
        replacePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        replacePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        replacePanel.setVisible(false);

        // Add spacer to align with find field
        replacePanel.add(Box.createHorizontalStrut(replaceToggleButton.getPreferredSize().width + 5));
        replacePanel.add(new JLabel("Replace:"));
        replacePanel.add(replaceField);
        replacePanel.add(replaceButton);
        replacePanel.add(replaceAllButton);

        add(replacePanel);
    }

    private void setupKeyboardShortcuts() {
        // Escape to close
        findField.registerKeyboardAction(
            e -> hideFindBar(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED
        );

        replaceField.registerKeyboardAction(
            e -> hideFindBar(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED
        );
    }

    /**
     * Shows the find bar in find-only mode.
     * @param selectedText optional text to prefill the find field
     */
    public void showFind(String selectedText) {
        if (replaceMode) {
            toggleReplaceMode();
        }
        if (selectedText != null && !selectedText.isEmpty()) {
            findField.setText(selectedText);
            findField.selectAll();
        }
        setVisible(true);
        findField.requestFocusInWindow();
        updateSearch();
    }

    /**
     * Shows the find bar in replace mode.
     * @param selectedText optional text to prefill the find field
     */
    public void showReplace(String selectedText) {
        if (!replaceMode) {
            toggleReplaceMode();
        }
        if (selectedText != null && !selectedText.isEmpty()) {
            findField.setText(selectedText);
            findField.selectAll();
        }
        setVisible(true);
        findField.requestFocusInWindow();
        updateSearch();
    }

    /**
     * Hides the find bar.
     */
    public void hideFindBar() {
        setVisible(false);
        editor.requestFocusInWindow();
        clearHighlights();
    }

    /**
     * Toggles between find-only and replace mode.
     */
    private void toggleReplaceMode() {
        replaceMode = !replaceMode;
        replaceToggleButton.setText(replaceMode ? "▼" : "▶");
        replacePanel.setVisible(replaceMode);
        revalidate();
        repaint();
    }

    /**
     * Updates the search when find text or options change.
     */
    private void updateSearch() {
        String searchText = findField.getText();
        if (searchText.isEmpty()) {
            matchCountLabel.setText("");
            totalMatches = 0;
            currentMatchIndex = -1;
            clearHighlights();
            return;
        }

        // Perform search and update match count
        totalMatches = countMatches(searchText);

        if (totalMatches == 0) {
            matchCountLabel.setText("No results");
            matchCountLabel.setForeground(Color.RED);
            findField.setForeground(Color.RED);
        } else {
            // Find current match based on caret position
            currentMatchIndex = findCurrentMatchIndex();
            matchCountLabel.setText((currentMatchIndex + 1) + " of " + totalMatches);
            matchCountLabel.setForeground(UIManager.getColor("Label.foreground"));
            findField.setForeground(UIManager.getColor("TextField.foreground"));
            highlightAllMatches(searchText);
        }
    }

    /**
     * Finds the next match.
     */
    private void findNext() {
        String searchText = findField.getText();
        if (searchText.isEmpty()) {
            return;
        }

        try {
            String content = editor.getEditor().getText();
            // Start searching from the end of current selection to skip current match
            int startPos = editor.getEditor().getSelectionEnd();

            int matchPos = findMatch(content, searchText, startPos, true);

            if (matchPos != -1) {
                int matchLength = getMatchLength(content, searchText, matchPos);
                // Use setCaretPosition + moveCaretPosition instead of select()
                editor.getEditor().setCaretPosition(matchPos + matchLength);
                editor.getEditor().moveCaretPosition(matchPos);

                // Ensure the selection is visible
                try {
                    editor.getEditor().scrollRectToVisible(editor.getEditor().modelToView(matchPos));
                } catch (Exception ex) {
                    // Ignore scroll errors
                }

                currentMatchIndex = findCurrentMatchIndex();
                updateMatchCountLabel();
                highlightAllMatches(searchText); // Refresh highlights to show current match
            } else {
                // Wrap around to beginning
                matchPos = findMatch(content, searchText, 0, true);
                if (matchPos != -1) {
                    int matchLength = getMatchLength(content, searchText, matchPos);
                    // Use setCaretPosition + moveCaretPosition instead of select()
                    editor.getEditor().setCaretPosition(matchPos + matchLength);
                    editor.getEditor().moveCaretPosition(matchPos);

                    // Ensure the selection is visible
                    try {
                        editor.getEditor().scrollRectToVisible(editor.getEditor().modelToView(matchPos));
                    } catch (Exception ex) {
                        // Ignore scroll errors
                    }

                    currentMatchIndex = findCurrentMatchIndex();
                    updateMatchCountLabel();
                    highlightAllMatches(searchText); // Refresh highlights to show current match
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds the previous match.
     */
    private void findPrevious() {
        String searchText = findField.getText();

        if (searchText.isEmpty()) {
            return;
        }

        try {
            String content = editor.getEditor().getText();

            // Start searching from before the current selection to skip current match
            int selectionStart = editor.getEditor().getSelectionStart();
            int selectionEnd = editor.getEditor().getSelectionEnd();

            // For backward search, we need to start BEFORE the current match
            // If there's a selection, start before it. If just a caret, check if we're at end of a match
            int startPos;
            if (selectionStart == selectionEnd) {
                // No selection, just a caret - might be at end of a match
                // Start searching from well before the caret to ensure we skip current match
                startPos = selectionStart - searchText.length();
            } else {
                // There's a selection - start before it
                startPos = selectionStart - 1;
            }

            // If we're at the very beginning, wrap to end
            if (startPos < 0) {
                startPos = content.length();
            }

            int matchPos = findMatch(content, searchText, startPos, false);

            if (matchPos != -1) {
                int matchLength = getMatchLength(content, searchText, matchPos);

                // Use setCaretPosition + moveCaretPosition instead of select()
                editor.getEditor().setCaretPosition(matchPos + matchLength);
                editor.getEditor().moveCaretPosition(matchPos);

                // Ensure the selection is visible
                try {
                    editor.getEditor().scrollRectToVisible(editor.getEditor().modelToView(matchPos));
                } catch (Exception ex) {
                    // Ignore scroll errors
                }

                currentMatchIndex = findCurrentMatchIndex();
                updateMatchCountLabel();
                highlightAllMatches(searchText); // Refresh highlights to show current match
            } else {
                // Wrap around to end
                matchPos = findMatch(content, searchText, content.length(), false);
                if (matchPos != -1) {
                    int matchLength = getMatchLength(content, searchText, matchPos);
                    // Use setCaretPosition + moveCaretPosition instead of select()
                    editor.getEditor().setCaretPosition(matchPos + matchLength);
                    editor.getEditor().moveCaretPosition(matchPos);

                    // Ensure the selection is visible
                    try {
                        editor.getEditor().scrollRectToVisible(editor.getEditor().modelToView(matchPos));
                    } catch (Exception ex) {
                        // Ignore scroll errors
                    }

                    currentMatchIndex = findCurrentMatchIndex();
                    updateMatchCountLabel();
                    highlightAllMatches(searchText); // Refresh highlights to show current match
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Replaces the current match.
     */
    private void replace() {
        String searchText = findField.getText();
        String replaceText = replaceField.getText();

        if (searchText.isEmpty()) {
            return;
        }

        try {
            int selStart = editor.getEditor().getSelectionStart();
            int selEnd = editor.getEditor().getSelectionEnd();

            if (selStart != selEnd) {
                String selectedText = editor.getSelectedText();
                if (matches(selectedText, searchText)) {
                    editor.replaceSelection(replaceText);
                    editor.getEditor().setCaretPosition(selStart + replaceText.length());
                }
            }

            // Find next match
            findNext();
            updateSearch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Replaces all matches.
     */
    private void replaceAll() {
        String searchText = findField.getText();
        String replaceText = replaceField.getText();

        if (searchText.isEmpty()) {
            return;
        }

        try {
            String content = editor.getEditor().getText();
            String result = replaceAllMatches(content, searchText, replaceText);

            if (!content.equals(result)) {
                int caretPos = editor.getEditor().getCaretPosition();
                editor.getEditor().setText(result);
                editor.getEditor().setCaretPosition(Math.min(caretPos, result.length()));
                updateSearch();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper methods for search/replace logic

    private int countMatches(String searchText) {
        try {
            String content = editor.getEditor().getText();
            int count = 0;
            int pos = 0;

            while (pos < content.length()) {
                int matchPos = findMatch(content, searchText, pos, true);
                if (matchPos == -1) {
                    break;
                }
                count++;
                pos = matchPos + 1;
            }

            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private int findCurrentMatchIndex() {
        try {
            String content = editor.getEditor().getText();
            String searchText = findField.getText();
            int selectionStart = editor.getEditor().getSelectionStart();
            int index = 0;
            int pos = 0;

            while (pos < content.length()) {
                int matchPos = findMatch(content, searchText, pos, true);
                if (matchPos == -1) {
                    break;
                }

                // If this match is past the selection, we're done
                if (matchPos > selectionStart) {
                    break;
                }

                // If this match is exactly at the selection start, we found it
                if (matchPos == selectionStart) {
                    return index;
                }

                index++;
                pos = matchPos + 1;
            }

            // If no exact match, return the last index before selection
            return index > 0 ? index - 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int findMatch(String content, String searchText, int startPos, boolean forward) {
        boolean matchCase = matchCaseCheckBox.isSelected();
        boolean matchWholeWord = matchWholeWordCheckBox.isSelected();
        boolean useRegex = useRegexCheckBox.isSelected();

        if (useRegex) {
            return findRegexMatch(content, searchText, startPos, forward, matchCase);
        } else {
            return findLiteralMatch(content, searchText, startPos, forward, matchCase, matchWholeWord);
        }
    }

    private int findLiteralMatch(String content, String searchText, int startPos, boolean forward, boolean matchCase, boolean matchWholeWord) {
        String searchContent = matchCase ? content : content.toLowerCase();
        String searchPattern = matchCase ? searchText : searchText.toLowerCase();

        int matchPos;
        if (forward) {
            matchPos = searchContent.indexOf(searchPattern, startPos);
        } else {
            // For backward search, ensure startPos is valid
            if (startPos < 0) {
                return -1; // Can't search from negative position
            }
            // lastIndexOf searches from startPos backward to beginning
            matchPos = searchContent.lastIndexOf(searchPattern, startPos);
        }

        if (matchPos != -1 && matchWholeWord) {
            if (!isWholeWordMatch(content, matchPos, searchText.length())) {
                // Continue searching
                int nextPos = forward ? matchPos + 1 : matchPos - 1;
                if (nextPos >= 0 && nextPos < content.length()) {
                    return findLiteralMatch(content, searchText, nextPos, forward, matchCase, matchWholeWord);
                }
                return -1;
            }
        }

        return matchPos;
    }

    private int findRegexMatch(String content, String searchText, int startPos, boolean forward, boolean matchCase) {
        try {
            int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
            Pattern pattern = Pattern.compile(searchText, flags);
            java.util.regex.Matcher matcher = pattern.matcher(content);

            if (forward) {
                if (matcher.find(startPos)) {
                    return matcher.start();
                }
            } else {
                int lastMatch = -1;
                while (matcher.find() && matcher.start() < startPos) {
                    lastMatch = matcher.start();
                }
                return lastMatch;
            }
        } catch (PatternSyntaxException e) {
            // Invalid regex
        }

        return -1;
    }

    private int getMatchLength(String content, String searchText, int matchPos) {
        boolean useRegex = useRegexCheckBox.isSelected();
        boolean matchCase = matchCaseCheckBox.isSelected();

        if (useRegex) {
            try {
                int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(searchText, flags);
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find(matchPos) && matcher.start() == matchPos) {
                    return matcher.end() - matcher.start();
                }
            } catch (PatternSyntaxException e) {
                // Invalid regex
            }
        }

        return searchText.length();
    }

    private boolean matches(String text, String searchText) {
        boolean matchCase = matchCaseCheckBox.isSelected();
        boolean matchWholeWord = matchWholeWordCheckBox.isSelected();
        boolean useRegex = useRegexCheckBox.isSelected();

        if (useRegex) {
            try {
                int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(searchText, flags);
                return pattern.matcher(text).matches();
            } catch (PatternSyntaxException e) {
                return false;
            }
        } else {
            String compareText = matchCase ? text : text.toLowerCase();
            String compareSearch = matchCase ? searchText : searchText.toLowerCase();
            return compareText.equals(compareSearch);
        }
    }

    private boolean isWholeWordMatch(String content, int matchPos, int matchLength) {
        boolean startOk = matchPos == 0 || !Character.isLetterOrDigit(content.charAt(matchPos - 1));
        boolean endOk = matchPos + matchLength >= content.length() || !Character.isLetterOrDigit(content.charAt(matchPos + matchLength));
        return startOk && endOk;
    }

    private String replaceAllMatches(String content, String searchText, String replaceText) {
        boolean matchCase = matchCaseCheckBox.isSelected();
        boolean matchWholeWord = matchWholeWordCheckBox.isSelected();
        boolean useRegex = useRegexCheckBox.isSelected();

        if (useRegex) {
            try {
                int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
                Pattern pattern = Pattern.compile(searchText, flags);
                return pattern.matcher(content).replaceAll(replaceText);
            } catch (PatternSyntaxException e) {
                return content;
            }
        } else {
            StringBuilder result = new StringBuilder();
            int pos = 0;

            while (pos < content.length()) {
                int matchPos = findLiteralMatch(content, searchText, pos, true, matchCase, matchWholeWord);
                if (matchPos == -1) {
                    result.append(content.substring(pos));
                    break;
                }

                result.append(content.substring(pos, matchPos));
                result.append(replaceText);
                pos = matchPos + searchText.length();
            }

            return result.toString();
        }
    }

    private void highlightAllMatches(String searchText) {
        clearHighlights();

        try {
            String content = editor.getEditor().getText();
            Highlighter highlighter = editor.getEditor().getHighlighter();

            int pos = 0;
            int matchIndex = 0;
            int currentSelection = editor.getEditor().getSelectionStart();

            while (pos < content.length()) {
                int matchPos = findMatch(content, searchText, pos, true);
                if (matchPos == -1) {
                    break;
                }

                int matchLength = getMatchLength(content, searchText, matchPos);

                // Use different highlight for current match vs others
                Highlighter.HighlightPainter painter = (matchPos == currentSelection) ? currentMatchPainter : matchPainter;
                Object tag = highlighter.addHighlight(matchPos, matchPos + matchLength, painter);
                highlightTags.add(tag);

                pos = matchPos + 1;
                matchIndex++;
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void clearHighlights() {
        Highlighter highlighter = editor.getEditor().getHighlighter();
        for (Object tag : highlightTags) {
            highlighter.removeHighlight(tag);
        }
        highlightTags.clear();
    }

    private void updateMatchCountLabel() {
        if (totalMatches > 0) {
            matchCountLabel.setText((currentMatchIndex + 1) + " of " + totalMatches);
        }
    }
}
