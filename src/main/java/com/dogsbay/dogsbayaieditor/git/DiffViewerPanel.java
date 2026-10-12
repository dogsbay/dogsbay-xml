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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Panel for displaying Git diffs with syntax highlighting.
 * Shows added lines in green, removed lines in red.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class DiffViewerPanel extends JPanel {
    private static final boolean DEBUG = false;

    private JTextPane diffTextPane;
    private StyledDocument diffDocument;
    private Git git;
    private File repositoryRoot;

    // Style attributes for diff highlighting
    private SimpleAttributeSet addedStyle;
    private SimpleAttributeSet removedStyle;
    private SimpleAttributeSet contextStyle;
    private SimpleAttributeSet headerStyle;

    /**
     * Creates a new diff viewer panel.
     */
    public DiffViewerPanel() {
        super(new BorderLayout());
        initializeUI();
        initializeStyles();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setBorder(BorderFactory.createTitledBorder("Diff Viewer"));

        // Create text pane for diff display
        diffTextPane = new JTextPane();
        diffTextPane.setEditable(false);
        diffTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        diffDocument = diffTextPane.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(diffTextPane);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Initializes the text styles for diff highlighting.
     */
    private void initializeStyles() {
        // Added lines (green background)
        addedStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(addedStyle, new Color(0, 128, 0));
        StyleConstants.setBackground(addedStyle, new Color(230, 255, 230));

        // Removed lines (red background)
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
     * Sets the Git repository.
     */
    public void setGit(Git git, File repositoryRoot) {
        this.git = git;
        this.repositoryRoot = repositoryRoot;
    }

    /**
     * Shows the diff for an uncommitted file (working tree vs HEAD).
     *
     * @param file the file to show diff for
     */
    public void showUncommittedDiff(File file) {
        if (git == null || repositoryRoot == null) {
            showMessage("No Git repository loaded");
            return;
        }

        try {
            String relativePath = repositoryRoot.toPath()
                .relativize(file.toPath())
                .toString()
                .replace('\\', '/');

            // Check if file is tracked (exists in repository)
            org.eclipse.jgit.api.Status status = git.status()
                .addPath(relativePath)
                .call();

            // Handle untracked (new) files specially
            if (!status.getUntracked().isEmpty() || !status.getUntrackedFolders().isEmpty()) {
                showNewFileDiff(file, relativePath);
                return;
            }

            // Generate diff between HEAD and working tree for tracked files
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(git.getRepository());
                formatter.setContext(3); // 3 lines of context

                // Get HEAD commit
                ObjectId headId = git.getRepository().resolve("HEAD");
                if (headId == null) {
                    showMessage("No HEAD commit found (empty repository?)");
                    return;
                }

                try (RevWalk revWalk = new RevWalk(git.getRepository());
                     ObjectReader reader = git.getRepository().newObjectReader()) {

                    RevCommit headCommit = revWalk.parseCommit(headId);

                    // Set up tree parsers for HEAD and working tree
                    CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                    oldTreeParser.reset(reader, headCommit.getTree());

                    FileTreeIterator workTreeIterator = new FileTreeIterator(git.getRepository());

                    // Scan all diffs between HEAD tree and working tree
                    java.util.List<DiffEntry> diffs = formatter.scan(oldTreeParser, workTreeIterator);

                    // Find and format only the diff for our specific file
                    boolean foundDiff = false;
                    for (DiffEntry diff : diffs) {
                        // Check if this diff entry matches our file
                        String diffPath = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? diff.getOldPath()
                            : diff.getNewPath();

                        if (diffPath.equals(relativePath)) {
                            formatter.format(diff);
                            foundDiff = true;
                            break;
                        }
                    }

                    if (!foundDiff) {
                        showMessage("No changes detected for: " + file.getName());
                        return;
                    }
                }
            }

            String diff = out.toString("UTF-8");
            if (diff.isEmpty()) {
                showMessage("No changes detected for: " + file.getName());
            } else {
                displayDiff(diff, file.getName());
            }

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("DiffViewerPanel: Failed to show uncommitted diff: " + e.getMessage());
                e.printStackTrace();
            }
            showMessage("Error generating diff:\n" + e.getClass().getSimpleName() + ": " + e.getMessage() +
                "\n\nThis file may be untracked or binary.");
        }
    }

    /**
     * Shows a new (untracked) file as all additions.
     */
    private void showNewFileDiff(File file, String relativePath) {
        try {
            diffDocument.remove(0, diffDocument.getLength());

            // Add header
            diffDocument.insertString(0, "New file: " + relativePath + "\n\n",
                headerStyle);

            // Read file content and show as all additions
            java.nio.file.Path filePath = file.toPath();
            if (java.nio.file.Files.size(filePath) > 1024 * 1024) {
                // File too large (>1MB)
                diffDocument.insertString(diffDocument.getLength(),
                    "File is too large to display (" +
                    (java.nio.file.Files.size(filePath) / 1024) + " KB)\n",
                    contextStyle);
            } else {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(filePath);
                int lineNum = 1;
                for (String line : lines) {
                    String displayLine = String.format("+%d: %s\n", lineNum++, line);
                    diffDocument.insertString(diffDocument.getLength(), displayLine, addedStyle);
                }
            }

            // Scroll to top
            diffTextPane.setCaretPosition(0);

        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            showMessage("Error reading new file: " + e.getMessage());
        }
    }

    /**
     * Shows the diff for a file in a specific commit.
     *
     * @param commit the commit containing the file change
     * @param filePath the path of the changed file
     */
    public void showCommitDiff(RevCommit commit, String filePath) {
        if (git == null) {
            showMessage("No Git repository loaded");
            return;
        }

        try {
            // Get parent commit (if exists)
            if (commit.getParentCount() == 0) {
                showMessage("Cannot show diff for initial commit");
                return;
            }

            RevCommit parentCommit = commit.getParent(0);

            // Generate diff
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(git.getRepository());
                formatter.setContext(3);

                try (ObjectReader reader = git.getRepository().newObjectReader()) {
                    CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                    oldTreeParser.reset(reader, parentCommit.getTree());

                    CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                    newTreeParser.reset(reader, commit.getTree());

                    formatter.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath));
                    formatter.format(oldTreeParser, newTreeParser);
                }
            }

            String diff = out.toString("UTF-8");
            if (diff.isEmpty()) {
                showMessage("No diff available for: " + filePath);
            } else {
                displayDiff(diff, filePath);
            }

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("DiffViewerPanel: Failed to show commit diff: " + e.getMessage());
                e.printStackTrace();
            }
            showMessage("Error generating diff: " + e.getMessage());
        }
    }

    /**
     * Displays a diff string with syntax highlighting.
     */
    private void displayDiff(String diff, String fileName) {
        try {
            diffDocument.remove(0, diffDocument.getLength());

            // Add header
            diffDocument.insertString(0, "Diff for: " + fileName + "\n\n",
                headerStyle);

            String[] lines = diff.split("\n");
            for (String line : lines) {
                SimpleAttributeSet style;

                if (line.startsWith("+++") || line.startsWith("---") ||
                    line.startsWith("@@") || line.startsWith("diff ")) {
                    style = headerStyle;
                } else if (line.startsWith("+")) {
                    style = addedStyle;
                } else if (line.startsWith("-")) {
                    style = removedStyle;
                } else {
                    style = contextStyle;
                }

                diffDocument.insertString(diffDocument.getLength(), line + "\n", style);
            }

            // Scroll to top
            diffTextPane.setCaretPosition(0);

        } catch (BadLocationException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows a message in the diff viewer.
     */
    private void showMessage(String message) {
        try {
            diffDocument.remove(0, diffDocument.getLength());
            diffDocument.insertString(0, message, contextStyle);
        } catch (BadLocationException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Clears the diff viewer.
     */
    public void clear() {
        showMessage("Select a file to view its diff");
    }
}
