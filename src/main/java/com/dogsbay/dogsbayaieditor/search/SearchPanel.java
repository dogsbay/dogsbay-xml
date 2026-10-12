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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.project.Finder;
import com.dogsbay.dogsbayaieditor.project.Match;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import org.bounce.FormLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Vector;

/**
 * VSCode-style search panel for searching files in the project.
 * Provides comprehensive search and replace functionality with
 * file filtering and grouped results display.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class SearchPanel extends ViewPanel {
    private static final boolean DEBUG = true;

    private DogsBayAIEditor parent;

    // UI Components - Input
    private JComboBox<String> searchField;
    private JComboBox<String> replaceField;

    // UI Components - Options
    private JCheckBox matchCaseCheckBox;
    private JCheckBox matchWholeWordCheckBox;
    private JCheckBox useRegexCheckBox;
    private JCheckBox preserveCaseCheckBox;

    // UI Components - Advanced
    private JPanel advancedPanel;
    private JTextField includePatternField;
    private JTextField excludePatternField;
    private JCheckBox searchOpenEditorsCheckBox;
    private boolean advancedVisible = false;

    // UI Components - Actions
    private JButton searchButton;
    private JButton replaceButton;
    private JButton replaceAllButton;
    private JButton clearButton;
    private JButton replaceToggle;
    private JPanel replaceFieldPanel;

    // UI Components - Results
    private JList<Object> resultsList;
    private SearchResultsListModel resultsModel;
    private SearchResultCellRenderer resultsCellRenderer;
    private JLabel statusLabel;
    private JScrollPane resultsScrollPane;

    // Search state
    private File searchRoot;
    private SearchWorker currentSearchWorker;

    /**
     * Creates a new search panel.
     *
     * @param parent the parent editor
     */
    public SearchPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());

        this.parent = parent;
        this.resultsModel = new SearchResultsListModel();

        initializeUI();
    }

    /**
     * Initializes the user interface.
     */
    private void initializeUI() {
        // Create main content panel
        JPanel contentPanel = new JPanel(new BorderLayout());

        // Top panel with search/replace inputs and options
        JPanel topPanel = createTopPanel();
        contentPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with results
        JPanel centerPanel = createResultsPanel();
        contentPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with status
        JPanel bottomPanel = createBottomPanel();
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * Creates the top panel with search/replace inputs and options.
     * VSCode-style layout with toggle arrow for replace functionality.
     */
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Input panel (search and replace fields) - VSCode style vertical layout
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));

        // Header panel with SEARCH label and icon buttons (VSCode style)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));  // Taller to fit buttons
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));  // More bottom padding

        // Search label on the left
        JLabel searchLabel = new JLabel("SEARCH");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 11f));
        headerPanel.add(searchLabel, BorderLayout.WEST);

        // VSCode-style icon buttons on the right
        JPanel iconButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        iconButtonsPanel.setOpaque(false);

        // Match Case button (Aa)
        matchCaseCheckBox = new JCheckBox("Aa");
        matchCaseCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        matchCaseCheckBox.setMargin(new Insets(2, 4, 2, 4));
        matchCaseCheckBox.setFocusable(false);
        matchCaseCheckBox.setToolTipText("Match Case");

        // Match Whole Word button (AB with underline-like styling)
        matchWholeWordCheckBox = new JCheckBox("<html>A<u>B</u></html>");
        matchWholeWordCheckBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        matchWholeWordCheckBox.setMargin(new Insets(2, 4, 2, 4));
        matchWholeWordCheckBox.setFocusable(false);
        matchWholeWordCheckBox.setToolTipText("Match Whole Word");

        // Use Regular Expression button (.*)
        useRegexCheckBox = new JCheckBox(".*");
        useRegexCheckBox.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        useRegexCheckBox.setMargin(new Insets(2, 4, 2, 4));
        useRegexCheckBox.setFocusable(false);
        useRegexCheckBox.setToolTipText("Use Regular Expression");

        iconButtonsPanel.add(matchCaseCheckBox);
        iconButtonsPanel.add(matchWholeWordCheckBox);
        iconButtonsPanel.add(useRegexCheckBox);

        headerPanel.add(iconButtonsPanel, BorderLayout.EAST);
        inputPanel.add(headerPanel);

        // Search field with toggle arrow
        JPanel searchFieldPanel = new JPanel(new BorderLayout(2, 0));
        searchFieldPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchFieldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        searchField = new JComboBox<>();
        searchField.setEditable(true);
        searchField.setFont(searchField.getFont().deriveFont(Font.PLAIN));

        // Set placeholder text in the editor component
        JTextField searchEditor = (JTextField) searchField.getEditor().getEditorComponent();
        searchEditor.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                }
            }
        });

        // Toggle button for replace functionality (VSCode style)
        replaceToggle = new JButton("▶");
        replaceToggle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        replaceToggle.setMargin(new Insets(0, 3, 0, 3));
        replaceToggle.setFocusable(false);
        replaceToggle.setToolTipText("Toggle Replace");

        searchFieldPanel.add(replaceToggle, BorderLayout.WEST);
        searchFieldPanel.add(searchField, BorderLayout.CENTER);
        inputPanel.add(searchFieldPanel);

        // Replace field (initially hidden)
        replaceFieldPanel = new JPanel(new BorderLayout(2, 0));
        replaceFieldPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        replaceFieldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        replaceFieldPanel.setBorder(new EmptyBorder(3, 0, 0, 0));
        replaceFieldPanel.setVisible(false);

        // Spacer to align with search field (same width as toggle button)
        JPanel spacer = new JPanel();
        spacer.setPreferredSize(new Dimension(replaceToggle.getPreferredSize().width, 1));

        replaceField = new JComboBox<>();
        replaceField.setEditable(true);
        replaceField.setFont(replaceField.getFont().deriveFont(Font.PLAIN));

        // Set placeholder text
        JTextField replaceEditor = (JTextField) replaceField.getEditor().getEditorComponent();

        replaceFieldPanel.add(spacer, BorderLayout.WEST);
        replaceFieldPanel.add(replaceField, BorderLayout.CENTER);
        inputPanel.add(replaceFieldPanel);

        // Toggle action to show/hide replace
        replaceToggle.addActionListener(e -> {
            boolean visible = !replaceFieldPanel.isVisible();
            replaceFieldPanel.setVisible(visible);
            replaceToggle.setText(visible ? "▼" : "▶");

            // Show/hide replace buttons and preserve case checkbox
            replaceButton.setVisible(visible);
            replaceAllButton.setVisible(visible);
            preserveCaseCheckBox.setVisible(visible);

            // Update button states
            Object selected = resultsList.getSelectedValue();
            replaceButton.setEnabled(visible && selected instanceof Match);
            replaceAllButton.setEnabled(visible && resultsModel.getSize() > 0);

            topPanel.revalidate();
            topPanel.repaint();
        });

        topPanel.add(inputPanel, BorderLayout.NORTH);

        // Options panel (only Preserve Case remains here, others moved to header)
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        preserveCaseCheckBox = new JCheckBox("Preserve Case");
        preserveCaseCheckBox.setMnemonic('P');
        preserveCaseCheckBox.setVisible(false); // Hidden until replace mode is active

        optionsPanel.add(preserveCaseCheckBox);

        topPanel.add(optionsPanel, BorderLayout.CENTER);

        // VSCode-style "..." toggle for advanced options (on its own line, right-aligned)
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton advancedToggle = new JButton("...");
        advancedToggle.setFont(advancedToggle.getFont().deriveFont(Font.BOLD, 14f));
        advancedToggle.setMargin(new Insets(2, 8, 2, 8));
        advancedToggle.setFocusable(false);
        advancedToggle.setToolTipText("Toggle Search Details");
        advancedToggle.addActionListener(e -> {
            advancedVisible = !advancedVisible;
            advancedPanel.setVisible(advancedVisible);
            topPanel.revalidate();
            topPanel.repaint();
        });

        // Create buttons
        searchButton = new JButton("Search");
        searchButton.setMnemonic('S');
        searchButton.addActionListener(e -> performSearch());

        clearButton = new JButton("Clear");
        clearButton.setMnemonic('l');
        clearButton.addActionListener(e -> clearResults());

        replaceButton = new JButton("Replace");
        replaceButton.setMnemonic('e');
        replaceButton.setEnabled(false); // Enable when match is selected
        replaceButton.setVisible(false); // Hidden until replace mode is active
        replaceButton.addActionListener(e -> performReplace());

        replaceAllButton = new JButton("Replace All");
        replaceAllButton.setMnemonic('A');
        replaceAllButton.setEnabled(false); // Enable after search
        replaceAllButton.setVisible(false); // Hidden until replace mode is active
        replaceAllButton.addActionListener(e -> performReplaceAll());

        // Single horizontal panel for all controls: Clear | Replace | Replace All | ... (right-aligned)
        JPanel controlsPanel = new JPanel(new BorderLayout());

        // Left side: Clear, Replace, Replace All buttons
        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        leftButtonsPanel.add(clearButton);
        leftButtonsPanel.add(replaceButton);
        leftButtonsPanel.add(replaceAllButton);

        // Right side: ... toggle button
        JPanel rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        rightButtonsPanel.add(advancedToggle);

        controlsPanel.add(leftButtonsPanel, BorderLayout.WEST);
        controlsPanel.add(rightButtonsPanel, BorderLayout.EAST);

        // Advanced panel (initially hidden)
        advancedPanel = createAdvancedPanel();
        advancedPanel.setVisible(false);

        // Wrapper to hold controls and advanced panels
        JPanel bottomTopPanel = new JPanel(new BorderLayout());
        bottomTopPanel.add(controlsPanel, BorderLayout.NORTH);
        bottomTopPanel.add(advancedPanel, BorderLayout.SOUTH);

        topPanel.add(bottomTopPanel, BorderLayout.SOUTH);

        return topPanel;
    }

    /**
     * Creates the advanced options panel.
     */
    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new TitledBorder("Search Details"),
                new EmptyBorder(5, 5, 5, 5)));

        JPanel fieldsPanel = new JPanel(new FormLayout(5, 5));

        JLabel includeLabel = new JLabel("Files to include:");
        includePatternField = new JTextField("");
        includePatternField.setToolTipText("Glob patterns separated by comma (leave empty for all files), e.g., *.xml,*.java");

        fieldsPanel.add(includeLabel, FormLayout.LEFT);
        fieldsPanel.add(includePatternField, FormLayout.RIGHT_FILL);

        JLabel excludeLabel = new JLabel("Files to exclude:");
        excludePatternField = new JTextField("target/**,.git/**,*.class,*.jar");
        excludePatternField.setToolTipText("Glob patterns separated by comma, e.g., target/**,*.class");

        fieldsPanel.add(excludeLabel, FormLayout.LEFT);
        fieldsPanel.add(excludePatternField, FormLayout.RIGHT_FILL);

        panel.add(fieldsPanel, BorderLayout.CENTER);

        JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchOpenEditorsCheckBox = new JCheckBox("Search only in open editors");
        searchOpenEditorsCheckBox.setEnabled(false); // TODO: Implement in Phase 6
        checkPanel.add(searchOpenEditorsCheckBox);

        panel.add(checkPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Creates the results panel with list view.
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        resultsList = new JList<>(resultsModel);
        resultsCellRenderer = new SearchResultCellRenderer();
        resultsList.setCellRenderer(resultsCellRenderer);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        resultsList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {  // Single click to open file
                    int index = resultsList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        Object item = resultsModel.getElementAt(index);
                        if (item instanceof Match) {
                            navigateToMatch((Match) item);
                        } else if (item instanceof SearchResultGroup) {
                            resultsModel.toggleGroup((SearchResultGroup) item);
                        }
                    }
                }
            }
        });

        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Object selected = resultsList.getSelectedValue();
                // Only enable replace button if replace mode is active AND a match is selected
                replaceButton.setEnabled(replaceButton.isVisible() && selected instanceof Match);
            }
        });

        resultsScrollPane = new JScrollPane(resultsList);
        resultsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(resultsScrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the bottom panel with status label.
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(2, 5, 2, 5));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(statusLabel, BorderLayout.WEST);

        return panel;
    }

    /**
     * Performs a search based on current settings.
     */
    private void performSearch() {
        if (DEBUG) {
            System.out.println("\n=== SearchPanel.performSearch() CALLED ===");
        }

        String searchText = getSearchText();
        if (DEBUG) {
            System.out.println("Search text: '" + searchText + "'");
        }
        if (searchText == null || searchText.trim().isEmpty()) {
            statusLabel.setText("Enter search text");
            return;
        }

        // Cancel any existing search
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            if (DEBUG) {
                System.out.println("Cancelling previous search");
            }
            currentSearchWorker.cancel(true);
        }

        // Determine search root
        searchRoot = determineSearchRoot();
        if (DEBUG) {
            System.out.println("Determined search root: " + searchRoot);
        }
        if (searchRoot == null || !searchRoot.exists()) {
            statusLabel.setText("No valid search root found");
            return;
        }

        // Clear previous results
        resultsModel.clear();
        statusLabel.setText("Searching in " + searchRoot.getAbsolutePath() + "...");

        // Update renderer with search root for relative paths
        resultsCellRenderer.setSearchRoot(searchRoot);

        // Get options
        boolean matchCase = matchCaseCheckBox.isSelected();
        boolean wholeWord = matchWholeWordCheckBox.isSelected();
        boolean useRegex = useRegexCheckBox.isSelected();

        if (DEBUG) {
            System.out.println("Options: matchCase=" + matchCase + ", wholeWord=" + wholeWord + ", useRegex=" + useRegex);
        }

        // Get include/exclude patterns
        String includePattern = includePatternField.getText().trim();
        String excludePattern = excludePatternField.getText().trim();

        // Note: Empty includePattern is handled by GlobFileFilter (defaults to .* matching all files)

        if (DEBUG) {
            System.out.println("Include pattern: '" + includePattern + "'");
            System.out.println("Exclude pattern: '" + excludePattern + "'");
        }

        // Create file filter
        GlobFileFilter fileFilter = new GlobFileFilter(searchRoot, includePattern, excludePattern);

        // Disable search button during search
        searchButton.setEnabled(false);

        // Create and execute search worker
        currentSearchWorker = new SearchWorker(
            searchRoot,
            searchText,
            useRegex,
            matchCase,
            wholeWord,
            fileFilter,
            resultsModel,
            new SearchWorker.SearchStatusCallback() {
                @Override
                public void onProgress(int filesSearched, int matchesFound) {
                    statusLabel.setText(String.format("Searching... %d files searched, %d matches found",
                        filesSearched, matchesFound));
                }

                @Override
                public void onComplete(int filesSearched, int matchesFound) {
                    int fileCount = resultsModel.getGroupCount();
                    statusLabel.setText(String.format("Found %d matches in %d files (searched %d files)",
                        matchesFound, fileCount, filesSearched));

                    // Enable replace all if there are results
                    replaceAllButton.setEnabled(matchesFound > 0);

                    // Auto-select first match to enable Replace button
                    if (matchesFound > 0 && resultsModel.getSize() > 0) {
                        // Find first Match (not SearchResultGroup) in the list
                        for (int i = 0; i < resultsModel.getSize(); i++) {
                            Object item = resultsModel.getElementAt(i);
                            if (item instanceof Match) {
                                resultsList.setSelectedIndex(i);
                                resultsList.ensureIndexIsVisible(i);
                                break;
                            }
                        }
                    }

                    // Re-enable search button
                    searchButton.setEnabled(true);
                }

                @Override
                public void onError(String message) {
                    statusLabel.setText("Error: " + message);
                    searchButton.setEnabled(true);
                }
            }
        );

        currentSearchWorker.execute();
    }

    /**
     * Performs a single replace operation on the selected match.
     */
    private void performReplace() {
        Object selected = resultsList.getSelectedValue();
        if (!(selected instanceof Match)) {
            statusLabel.setText("No match selected");
            return;
        }

        String replaceText = getReplaceText();
        if (replaceText == null) {
            replaceText = "";
        }

        Match match = (Match) selected;
        boolean preserveCase = preserveCaseCheckBox.isSelected();

        // Perform replacement
        boolean success = ReplaceHandler.replaceSingle(match, replaceText, preserveCase);

        if (success) {
            // Reload current document if it was modified
            if (parent.getView() != null && parent.getView().getDocument() != null) {
                URL currentURL = parent.getView().getDocument().getURL();
                if (currentURL != null && currentURL.equals(match.getURL())) {
                    try {
                        parent.getView().reload();
                        // Say we are in step, so the reload we just did is not
                        // reported back to us as an external change.
                        SwingUtilities.invokeLater(() -> {
                            parent.getView().getDocument().acknowledgeDiskState();
                        });
                    } catch (Exception ex) {
                        // Ignore reload errors
                    }
                }
            }

            // Remove this match from results
            SearchResultGroup group = resultsModel.getGroupForMatch(match);
            if (group != null) {
                group.getMatches().remove(match);

                if (group.getMatchCount() == 0) {
                    // Remove empty group
                    resultsModel.clear();
                    for (SearchResultGroup g : resultsModel.getAllGroups()) {
                        if (!g.equals(group)) {
                            resultsModel.addFileGroup(g);
                        }
                    }
                } else {
                    // Refresh display
                    resultsList.repaint();
                }
            }

            int matchCount = resultsModel.getTotalMatchCount();
            statusLabel.setText("Replaced 1 match. " + matchCount + " matches remaining.");

            // Update replace all button state
            replaceAllButton.setEnabled(matchCount > 0);
        } else {
            statusLabel.setText("Failed to replace match");
        }
    }

    /**
     * Performs replace all operation on all matches.
     */
    private void performReplaceAll() {
        int matchCount = resultsModel.getTotalMatchCount();
        if (matchCount == 0) {
            statusLabel.setText("No matches to replace");
            return;
        }

        String replaceText = getReplaceText();
        if (replaceText == null) {
            replaceText = "";
        }

        // Confirm with user
        int result = JOptionPane.showConfirmDialog(
            this,
            "Replace " + matchCount + " matches in " + resultsModel.getGroupCount() + " files?",
            "Confirm Replace All",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        boolean preserveCase = preserveCaseCheckBox.isSelected();

        // Perform replace all
        List<SearchResultGroup> groups = resultsModel.getAllGroups();
        int replaced = ReplaceHandler.replaceAll(groups, replaceText, preserveCase);

        // Reload current document if it was modified
        if (parent.getView() != null && parent.getView().getDocument() != null) {
            URL currentURL = parent.getView().getDocument().getURL();
            if (currentURL != null) {
                // Check if current document was in the replaced files
                for (SearchResultGroup group : groups) {
                    try {
                        if (group.getFile().toURI().toURL().equals(currentURL)) {
                            parent.getView().reload();
                            // Say we are in step, so the reload we just did is not
                            // reported back to us as an external change.
                            SwingUtilities.invokeLater(() -> {
                                parent.getView().getDocument().acknowledgeDiskState();
                            });
                            break;
                        }
                    } catch (Exception ex) {
                        // Ignore reload errors
                    }
                }
            }
        }

        // Clear results
        resultsModel.clear();
        replaceAllButton.setEnabled(false);

        statusLabel.setText("Replaced " + replaced + " matches in " + groups.size() + " files");
    }

    /**
     * Clears the search results.
     */
    private void clearResults() {
        resultsModel.clear();
        statusLabel.setText("Ready");
        replaceAllButton.setEnabled(false);
        replaceButton.setEnabled(false);
    }

    /**
     * Navigates to a match in the editor.
     */
    private void navigateToMatch(Match match) {
        parent.setWait(true);
        parent.setStatus("Opening...");

        // Background thread to avoid blocking UI (pattern from FindInFilesResults)
        new Thread(() -> {
            try {
                parent.open(match.getURL(), null, true);
            } catch (Exception e) {
                if (DEBUG) {
                    System.err.println("SearchPanel: Error opening file: " + e.getMessage());
                }
            } finally {
                parent.setStatus("Done");
                parent.setWait(false);

                SwingUtilities.invokeLater(() -> {
                    parent.switchToEditor();
                    // Select the match in the editor
                    try {
                        parent.getView().getEditor().select(
                                match.getLineNumber(),
                                match.getStart(),
                                match.getEnd());
                    } catch (Exception e) {
                        if (DEBUG) {
                            System.err.println("SearchPanel: Error selecting text: " + e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Determines the search root directory.
     * Tries File Explorer root, then Git repository root, then falls back to working directory.
     */
    private File determineSearchRoot() {
        if (DEBUG) {
            System.out.println("=== SearchPanel.determineSearchRoot() ===");
        }

        // Priority 1: File Explorer root directory (if available)
        if (parent.getFileExplorer() != null) {
            File explorerRoot = parent.getFileExplorer().getRootDirectory();
            if (DEBUG) {
                System.out.println("File Explorer root: " + explorerRoot);
            }
            if (explorerRoot != null && explorerRoot.exists() && explorerRoot.isDirectory()) {
                if (DEBUG) {
                    System.out.println("Using File Explorer root: " + explorerRoot.getAbsolutePath());
                }
                return explorerRoot;
            }
        } else {
            if (DEBUG) {
                System.out.println("File Explorer is null");
            }
        }

        // Priority 2: Git repository root (if available)
        GitPanel gitPanel = parent.getGitPanel();
        if (gitPanel != null) {
            File gitRoot = gitPanel.getRepositoryRoot();
            if (DEBUG) {
                System.out.println("Git repository root: " + gitRoot);
            }
            if (gitRoot != null && gitRoot.exists()) {
                if (DEBUG) {
                    System.out.println("Using Git root: " + gitRoot.getAbsolutePath());
                }
                return gitRoot;
            }
        } else {
            if (DEBUG) {
                System.out.println("Git panel is null");
            }
        }

        // Priority 3: Current working directory
        File cwd = new File(System.getProperty("user.dir"));
        if (DEBUG) {
            System.out.println("Current working directory: " + cwd.getAbsolutePath());
        }
        if (cwd.exists() && cwd.isDirectory()) {
            if (DEBUG) {
                System.out.println("Using working directory: " + cwd.getAbsolutePath());
            }
            return cwd;
        }

        // Priority 4: User home
        File userHome = new File(System.getProperty("user.home"));
        if (DEBUG) {
            System.out.println("Falling back to user home: " + userHome.getAbsolutePath());
        }
        return userHome;
    }

    /**
     * Gets the search text from the search field.
     */
    private String getSearchText() {
        Object item = searchField.getEditor().getItem();
        return item != null ? item.toString() : null;
    }

    /**
     * Gets the replace text from the replace field.
     */
    private String getReplaceText() {
        Object item = replaceField.getEditor().getItem();
        return item != null ? item.toString() : null;
    }

    // ViewPanel implementation

    @Override
    public void setFocus() {
        searchField.requestFocusInWindow();
        if (searchField.getEditor().getEditorComponent() instanceof JTextComponent) {
            ((JTextComponent) searchField.getEditor().getEditorComponent()).selectAll();
        }
    }

    @Override
    public void updatePreferences() {
        // Phase 5: Load preferences from ConfigurationProperties
    }

    @Override
    public void setProperties() {
        // Phase 5: Save preferences to ConfigurationProperties
    }

    /**
     * Activates the search panel in search-only mode.
     * Called from Edit->Find in Files menu action.
     */
    public void activateSearchMode() {
        // Switch to search panel in the explorer
        parent.getExplorerContainer().setSelectedExplorer("search");

        // Ensure replace mode is hidden
        if (replaceFieldPanel != null && replaceFieldPanel.isVisible()) {
            replaceToggle.doClick(); // Toggle off replace mode
        }

        // Focus the search field
        setFocus();
    }

    /**
     * Activates the search panel in replace mode.
     * Called from Edit->Replace in Files menu action.
     */
    public void activateReplaceMode() {
        // Switch to search panel in the explorer
        parent.getExplorerContainer().setSelectedExplorer("search");

        // Ensure replace mode is shown
        if (replaceFieldPanel != null && !replaceFieldPanel.isVisible()) {
            replaceToggle.doClick(); // Toggle on replace mode
        }

        // Focus the search field
        setFocus();
    }
}
