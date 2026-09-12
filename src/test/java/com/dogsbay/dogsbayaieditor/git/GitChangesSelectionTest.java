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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the changes-tree selection model.
 *
 * <p>The changes tree mixes file nodes with section header nodes; multi-selection must
 * never surface a header, and Swing's own internal parent-selection calls must not be
 * able to grow the selection behind the user's back.
 */
public class GitChangesSelectionTest {

    private JTree tree;
    private DefaultMutableTreeNode stagedSection;
    private DefaultMutableTreeNode changesSection;
    private GitFileNode staged1;
    private GitFileNode modified1;
    private GitFileNode untracked1;

    @BeforeEach
    void setUp() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Changes");

        stagedSection = new DefaultMutableTreeNode("Staged Changes (1)");
        staged1 = new GitFileNode(new File("/repo/a.txt"), GitFileNode.GitStatus.STAGED, "a.txt");
        stagedSection.add(staged1);

        changesSection = new DefaultMutableTreeNode("Changes (2)");
        modified1 = new GitFileNode(new File("/repo/b.txt"), GitFileNode.GitStatus.MODIFIED, "b.txt");
        untracked1 = new GitFileNode(new File("/repo/c.txt"), GitFileNode.GitStatus.UNTRACKED, "c.txt");
        changesSection.add(modified1);
        changesSection.add(untracked1);

        root.add(stagedSection);
        root.add(changesSection);

        tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setSelectionModel(new GitPanel.GitFileSelectionModel());
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /** Collects the nodes currently selected. */
    private List<Object> selectedNodes() {
        List<Object> nodes = new ArrayList<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            for (TreePath path : paths) {
                nodes.add(path.getLastPathComponent());
            }
        }
        return nodes;
    }

    @Test
    void sectionHeadersAreNeverSelected() {
        tree.setSelectionPath(new TreePath(changesSection.getPath()));

        assertTrue(selectedNodes().isEmpty(),
                "a section header must not select anything by itself; the click gesture "
                        + "handles selecting its files");
    }

    @Test
    void rangeSpanningHeadersSelectsOnlyFiles() {
        // Equivalent to shift-clicking from the first visible row to the last.
        tree.setSelectionInterval(0, tree.getRowCount() - 1);

        List<Object> selected = selectedNodes();
        assertEquals(3, selected.size(), "only the three files should be selected");
        assertTrue(selected.containsAll(List.of(staged1, modified1, untracked1)));
        assertFalse(selected.contains(stagedSection));
        assertFalse(selected.contains(changesSection));
    }

    @Test
    void selectingIndividualFilesIsUnaffected() {
        tree.setSelectionPath(new TreePath(modified1.getPath()));
        tree.addSelectionPath(new TreePath(untracked1.getPath()));

        List<Object> selected = selectedNodes();
        assertEquals(2, selected.size());
        assertTrue(selected.containsAll(List.of(modified1, untracked1)));
    }

    /**
     * Regression: {@code JTree.setExpandedState} calls {@code addSelectionPath(parent)}
     * when collapsing a node that contains the selection. A model that expanded a header
     * into its children turned that into "select the whole section", and because
     * {@code getExpandsSelectedPaths} is true the section then sprang back open.
     */
    @Test
    void collapsingASectionDoesNotGrowTheSelection() {
        tree.setSelectionPath(new TreePath(modified1.getPath()));
        TreePath sectionPath = new TreePath(changesSection.getPath());

        tree.collapsePath(sectionPath);

        assertTrue(tree.isCollapsed(sectionPath), "the section must actually collapse");
        assertFalse(selectedNodes().contains(untracked1),
                "collapsing must not pull the section's other files into the selection");
        assertFalse(selectedNodes().contains(changesSection),
                "collapsing must not select the header");
    }

    @Test
    void collapseAllLeavesSelectionAlone() {
        tree.setSelectionPath(new TreePath(staged1.getPath()));

        for (int row = tree.getRowCount() - 1; row >= 0; row--) {
            tree.collapseRow(row);
        }

        assertFalse(selectedNodes().contains(modified1));
        assertFalse(selectedNodes().contains(untracked1));
    }

    @Test
    void clickingASectionHeaderYieldsItsFiles() {
        TreePath[] paths = GitPanel.fileChildPaths(new TreePath(changesSection.getPath()));

        assertEquals(2, paths.length);
        assertSame(modified1, paths[0].getLastPathComponent());
        assertSame(untracked1, paths[1].getLastPathComponent());
    }

    @Test
    void emptySectionHeaderYieldsNoFiles() {
        DefaultMutableTreeNode empty = new DefaultMutableTreeNode("Empty (0)");
        ((DefaultMutableTreeNode) tree.getModel().getRoot()).add(empty);

        assertEquals(0, GitPanel.fileChildPaths(new TreePath(empty.getPath())).length);
    }

    @Test
    void clearingSelectionIsHandled() {
        tree.setSelectionInterval(0, tree.getRowCount() - 1);
        assertFalse(selectedNodes().isEmpty());

        tree.setSelectionPaths(null);

        assertTrue(selectedNodes().isEmpty());
    }

    /**
     * A file that is staged and then edited again appears in both sections. Toggling both
     * rows at once would stage it and then immediately unstage it, discarding the staged
     * snapshot, so such a file is excluded from a bulk toggle.
     */
    @Test
    void bulkToggleDropsFilesPresentInBothSections() {
        GitFileNode stagedDup = new GitFileNode(new File("/repo/d.txt"),
                GitFileNode.GitStatus.STAGED, "d.txt");
        GitFileNode modifiedDup = new GitFileNode(new File("/repo/d.txt"),
                GitFileNode.GitStatus.MODIFIED, "d.txt");

        List<GitFileNode> safe = GitPanel.ambiguityFreeToggleSet(
                List.of(staged1, stagedDup, modifiedDup, modified1));

        assertEquals(List.of(staged1, modified1), safe,
                "the staged-and-modified file should be dropped, the rest kept");
    }

    @Test
    void bulkToggleKeepsEverythingWhenThereIsNoAmbiguity() {
        List<GitFileNode> nodes = List.of(staged1, modified1, untracked1);

        assertEquals(nodes, GitPanel.ambiguityFreeToggleSet(nodes));
    }
}
