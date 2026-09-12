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

package com.dogsbay.dogsbayaieditor.xpath;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import com.dogsbay.dogsbayaieditor.search.GlobFileFilter;
import org.bounce.FormLayout;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;

/**
 * Left-sidebar explorer panel for running XPath queries across all project files.
 * Similar to the Search panel but uses XPath expressions instead of text/regex.
 */
public class XPathQueryPanel extends ViewPanel {
    private static final boolean DEBUG = true;

    private DogsBayAIEditor parent;

    private JComboBox<String> xpathField;

    private JPanel advancedPanel;
    private JTextField includePatternField;
    private JTextField excludePatternField;
    private boolean advancedVisible = false;

    private JButton clearButton;
    private JButton stopButton;

    private JList<Object> resultsList;
    private XPathResultsListModel resultsModel;
    private XPathResultCellRenderer resultsCellRenderer;
    private JLabel statusLabel;

    private File searchRoot;
    private XPathQueryWorker currentWorker;

    public XPathQueryPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());

        this.parent = parent;
        this.resultsModel = new XPathResultsListModel();

        initializeUI();
    }

    private void initializeUI() {
        JPanel contentPanel = new JPanel(new BorderLayout());

        JPanel topPanel = createTopPanel();
        contentPanel.add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = createResultsPanel();
        contentPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = createBottomPanel();
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel headerLabel = new JLabel("XPATH QUERY");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
        headerPanel.add(headerLabel, BorderLayout.WEST);

        inputPanel.add(headerPanel);

        // XPath input field
        JPanel xpathFieldPanel = new JPanel(new BorderLayout(2, 0));
        xpathFieldPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        xpathFieldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        xpathField = new JComboBox<>();
        xpathField.setEditable(true);
        xpathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTextField xpathEditor = (JTextField) xpathField.getEditor().getEditorComponent();
        xpathEditor.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                }
            }
        });

        xpathFieldPanel.add(xpathField, BorderLayout.CENTER);
        inputPanel.add(xpathFieldPanel);

        topPanel.add(inputPanel, BorderLayout.NORTH);

        // Controls row
        JPanel controlsPanel = new JPanel(new BorderLayout());

        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        clearButton = new JButton("Clear");
        clearButton.setMnemonic('l');
        clearButton.addActionListener(e -> clearResults());

        stopButton = new JButton("Stop");
        stopButton.setMnemonic('t');
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopSearch());

        leftButtonsPanel.add(clearButton);
        leftButtonsPanel.add(stopButton);

        JPanel rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
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

        rightButtonsPanel.add(advancedToggle);

        controlsPanel.add(leftButtonsPanel, BorderLayout.WEST);
        controlsPanel.add(rightButtonsPanel, BorderLayout.EAST);

        // Advanced panel
        advancedPanel = createAdvancedPanel();
        advancedPanel.setVisible(false);

        JPanel bottomTopPanel = new JPanel(new BorderLayout());
        bottomTopPanel.add(controlsPanel, BorderLayout.NORTH);
        bottomTopPanel.add(advancedPanel, BorderLayout.SOUTH);

        topPanel.add(bottomTopPanel, BorderLayout.SOUTH);

        return topPanel;
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new TitledBorder("Search Details"),
                new EmptyBorder(5, 5, 5, 5)));

        JPanel fieldsPanel = new JPanel(new FormLayout(5, 5));

        JLabel includeLabel = new JLabel("Files to include:");
        includePatternField = new JTextField("**/*.xml,**/*.xsd,**/*.xsl,**/*.xslt,**/*.xhtml,**/*.dita,**/*.ditamap,**/*.svg,**/*.wsdl");
        includePatternField.setToolTipText("Glob patterns separated by comma (leave empty for all XML files)");

        fieldsPanel.add(includeLabel, FormLayout.LEFT);
        fieldsPanel.add(includePatternField, FormLayout.RIGHT_FILL);

        JLabel excludeLabel = new JLabel("Files to exclude:");
        excludePatternField = new JTextField("target/**,.git/**");
        excludePatternField.setToolTipText("Glob patterns separated by comma");

        fieldsPanel.add(excludeLabel, FormLayout.LEFT);
        fieldsPanel.add(excludePatternField, FormLayout.RIGHT_FILL);

        panel.add(fieldsPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        resultsList = new JList<>(resultsModel);
        resultsCellRenderer = new XPathResultCellRenderer();
        resultsList.setCellRenderer(resultsCellRenderer);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        resultsList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int index = resultsList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        Object item = resultsModel.getElementAt(index);
                        if (item instanceof XPathResultGroup.XPathResultItem) {
                            navigateToResult((XPathResultGroup.XPathResultItem) item);
                        } else if (item instanceof XPathResultGroup) {
                            resultsModel.toggleGroup((XPathResultGroup) item);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(2, 5, 2, 5));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(statusLabel, BorderLayout.WEST);

        return panel;
    }

    private void performSearch() {
        String xpathExpr = getXPathText();
        if (DEBUG) {
            System.out.println("XPathQueryPanel.performSearch(): '" + xpathExpr + "'");
        }
        if (xpathExpr == null || xpathExpr.trim().isEmpty()) {
            statusLabel.setText("Enter an XPath expression");
            return;
        }

        // Cancel existing worker
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        // Add to history
        addToHistory(xpathExpr);

        // Determine search root
        searchRoot = determineSearchRoot();
        if (searchRoot == null || !searchRoot.exists()) {
            statusLabel.setText("No valid search root found");
            return;
        }

        // Clear previous results
        resultsModel.clear();
        statusLabel.setText("Searching in " + searchRoot.getAbsolutePath() + "...");

        resultsCellRenderer.setSearchRoot(searchRoot);

        // Get include/exclude patterns
        String includePattern = includePatternField.getText().trim();
        String excludePattern = excludePatternField.getText().trim();

        GlobFileFilter fileFilter = new GlobFileFilter(searchRoot, includePattern, excludePattern);

        stopButton.setEnabled(true);

        currentWorker = new XPathQueryWorker(
            searchRoot,
            xpathExpr,
            fileFilter,
            resultsModel,
            new XPathQueryWorker.StatusCallback() {
                @Override
                public void onProgress(int filesSearched, int resultsFound) {
                    statusLabel.setText(String.format("Searching... %d files, %d results",
                        filesSearched, resultsFound));
                }

                @Override
                public void onComplete(int filesSearched, int resultsFound) {
                    int fileCount = resultsModel.getGroupCount();
                    statusLabel.setText(String.format("Found %d results in %d files (searched %d files)",
                        resultsFound, fileCount, filesSearched));
                    stopButton.setEnabled(false);
                }

                @Override
                public void onError(String message) {
                    statusLabel.setText("Error: " + message);
                    stopButton.setEnabled(false);
                }
            }
        );

        currentWorker.execute();
    }

    private void clearResults() {
        resultsModel.clear();
        statusLabel.setText("Ready");
    }

    private void stopSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            statusLabel.setText("Search stopped");
            stopButton.setEnabled(false);
        }
    }

    private void navigateToResult(XPathResultGroup.XPathResultItem item) {
        parent.setWait(true);
        parent.setStatus("Opening...");

        new Thread(() -> {
            try {
                URL url = item.getFile().toURI().toURL();
                parent.open(url, null, true);
            } catch (Exception e) {
                if (DEBUG) {
                    System.err.println("XPathQueryPanel: Error opening file: " + e.getMessage());
                }
            } finally {
                parent.setStatus("Done");
                parent.setWait(false);

                SwingUtilities.invokeLater(() -> {
                    parent.switchToEditor();
                    try {
                        parent.getView().getEditor().selectLineWithoutEnd(item.getLineNumber());
                    } catch (Exception e) {
                        if (DEBUG) {
                            System.err.println("XPathQueryPanel: Error selecting line: " + e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    private void addToHistory(String expr) {
        // Avoid duplicates at the top
        for (int i = 0; i < xpathField.getItemCount(); i++) {
            if (expr.equals(xpathField.getItemAt(i))) {
                xpathField.removeItemAt(i);
                break;
            }
        }
        xpathField.insertItemAt(expr, 0);

        // Limit history size
        while (xpathField.getItemCount() > 20) {
            xpathField.removeItemAt(xpathField.getItemCount() - 1);
        }
    }

    private File determineSearchRoot() {
        // Priority 1: File Explorer root
        if (parent.getFileExplorer() != null) {
            File explorerRoot = parent.getFileExplorer().getRootDirectory();
            if (explorerRoot != null && explorerRoot.exists() && explorerRoot.isDirectory()) {
                return explorerRoot;
            }
        }

        // Priority 2: Git repository root
        GitPanel gitPanel = parent.getGitPanel();
        if (gitPanel != null) {
            File gitRoot = gitPanel.getRepositoryRoot();
            if (gitRoot != null && gitRoot.exists()) {
                return gitRoot;
            }
        }

        // Priority 3: Current working directory
        File cwd = new File(System.getProperty("user.dir"));
        if (cwd.exists() && cwd.isDirectory()) {
            return cwd;
        }

        // Priority 4: User home
        return new File(System.getProperty("user.home"));
    }

    private String getXPathText() {
        Object item = xpathField.getEditor().getItem();
        return item != null ? item.toString() : null;
    }

    // ViewPanel implementation

    @Override
    public void setFocus() {
        xpathField.requestFocusInWindow();
        if (xpathField.getEditor().getEditorComponent() instanceof JTextComponent) {
            ((JTextComponent) xpathField.getEditor().getEditorComponent()).selectAll();
        }
    }

    @Override
    public void updatePreferences() {
        // No-op
    }

    @Override
    public void setProperties() {
        // No-op
    }
}
