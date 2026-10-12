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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.swing.SwingUtilities;
import java.io.File;

/**
 * Manager class for creating and displaying Git diffs in tabs using DiffView.
 * Provides a clean API for GitPanel to open diffs without dealing with
 * tab management details.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DiffViewManager {
    private static final boolean DEBUG = false;

    /**
     * Opens a diff in a new editor tab using a custom DiffView.
     * If a tab with the same name already exists, switches to it instead.
     * Integrates directly into the DogsBayAIEditor tab system.
     *
     * @param parent      the parent DogsBayAIEditor
     * @param diffContent the unified diff text to display
     * @param tabName     the name for the tab
     */
    public static void openDiffInTab(DogsBayAIEditor parent, String diffContent, String tabName) {
        // Validate content
        final String finalDiffContent = (diffContent == null || diffContent.trim().isEmpty())
            ? "No diff content available"
            : diffContent;

        // Add to tab system on EDT
        SwingUtilities.invokeLater(() -> {
            // A tab of this name may already be open on an older state of the
            // file. Reopening a diff used to select it and stop there, so the
            // tab went on showing the diff it was opened with however many times
            // the file changed underneath it.
            java.awt.Component existing = parent.getSelectedTabbedView().tabComponentByName(tabName);
            if (existing instanceof DiffView view) {
                view.setContent(finalDiffContent);
                parent.getSelectedTabbedView().selectTabByName(tabName);
                if (DEBUG) {
                    System.out.println("Refreshed existing diff tab: " + tabName);
                }
                return;
            }
            if (parent.getSelectedTabbedView().selectTabByName(tabName)) {
                if (DEBUG) {
                    System.out.println("Switched to existing tab: " + tabName);
                }
                return;
            }

            // Create new DiffView and set content
            DiffView diffView = new DiffView();
            diffView.setContent(finalDiffContent);

            // Add to the main tab system
            parent.getSelectedTabbedView().addCustomPanel(diffView, tabName, tabName);

            if (DEBUG) {
                System.out.println("Opened new diff tab: " + tabName);
            }
        });
    }

    /**
     * Creates a formatted tab name for an uncommitted diff.
     *
     * @param file the file being diffed
     * @return the formatted tab name
     */
    public static String createUncommittedDiffTabName(File file) {
        return "Diff: " + file.getName() + " (uncommitted)";
    }

    /**
     * Creates a formatted tab name for a committed diff.
     *
     * @param file   the file being diffed
     * @param commit the commit containing the changes
     * @return the formatted tab name
     */
    public static String createCommittedDiffTabName(File file, RevCommit commit) {
        String shortHash = commit.getId().abbreviate(7).name();
        return "Diff: " + file.getName() + " [" + shortHash + "]";
    }

    /**
     * Opens a diff for uncommitted changes in a new tab.
     * Convenience method that combines diff generation and tab creation.
     *
     * @param parent      the parent DogsBayAIEditor
     * @param file        the file to show diff for
     * @param diffContent the generated diff content
     */
    public static void openUncommittedDiff(DogsBayAIEditor parent, File file, String diffContent) {
        String tabName = createUncommittedDiffTabName(file);
        openDiffInTab(parent, diffContent, tabName);
    }

    /**
     * Opens a diff for committed changes in a new tab.
     * Convenience method that combines diff generation and tab creation.
     *
     * @param parent      the parent DogsBayAIEditor
     * @param file        the file to show diff for
     * @param commit      the commit containing the changes
     * @param diffContent the generated diff content
     */
    public static void openCommittedDiff(DogsBayAIEditor parent, File file, RevCommit commit, String diffContent) {
        String tabName = createCommittedDiffTabName(file, commit);
        openDiffInTab(parent, diffContent, tabName);
    }
}
