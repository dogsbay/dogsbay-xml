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
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BranchSwitcherPopup menu structure and rendering.
 */
public class BranchSwitcherPopupTest {
    private Path tempDir;
    private Git git;
    private GitPanel gitPanel;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("branch-switcher-test");
        git = Git.init().setDirectory(tempDir.toFile()).call();

        // Create an initial commit so branches work
        File testFile = new File(tempDir.toFile(), "test.txt");
        Files.writeString(testFile.toPath(), "test content");
        git.add().addFilepattern("test.txt").call();
        git.commit().setMessage("Initial commit").call();

        // Create GitPanel using the test constructor
        gitPanel = new GitPanel(git);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (git != null) {
            git.getRepository().close();
            git.close();
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testPopupContainsCreateBranchItems() {
        BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);

        Component[] components = popup.getComponents();
        assertTrue(components.length >= 5, "Popup should have at least 5 components");

        assertTrue(components[0] instanceof JMenuItem);
        assertEquals("Create New Branch...", ((JMenuItem) components[0]).getText());

        assertTrue(components[1] instanceof JMenuItem);
        assertEquals("Create New Branch From...", ((JMenuItem) components[1]).getText());

        // What a branch is for once the work on it is done.
        assertTrue(components[2] instanceof JMenuItem);
        assertEquals("Merge Branch...", ((JMenuItem) components[2]).getText());

        assertTrue(components[3] instanceof JMenuItem);
        assertEquals("Delete Branch...", ((JMenuItem) components[3]).getText());

        assertTrue(components[4] instanceof JPopupMenu.Separator);
    }

    @Test
    void testPopupShowsCurrentBranchAsBold() throws Exception {
        BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);
        Component[] components = popup.getComponents();

        boolean foundCurrentBranch = false;
        for (Component comp : components) {
            if (comp instanceof JMenuItem item) {
                String text = item.getText();
                if (text != null && (text.contains("master") || text.contains("main"))) {
                    assertTrue(item.getFont().isBold(), "Current branch should be bold");
                    assertFalse(item.isEnabled(), "Current branch should be disabled");
                    assertNotNull(item.getIcon(), "Current branch should have a check icon");
                    foundCurrentBranch = true;
                    break;
                }
            }
        }
        assertTrue(foundCurrentBranch, "Should find the current branch in the popup");
    }

    @Test
    void testPopupShowsMultipleBranches() throws Exception {
        git.branchCreate().setName("feature-1").call();
        git.branchCreate().setName("feature-2").call();

        BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);
        Component[] components = popup.getComponents();

        // Count branch items (after separator and header)
        int branchCount = 0;
        boolean pastSeparator = false;
        boolean pastHeader = false;
        for (Component comp : components) {
            if (comp instanceof JPopupMenu.Separator) {
                pastSeparator = true;
                continue;
            }
            if (pastSeparator && !pastHeader) {
                pastHeader = true; // skip the "Switch to branch:" header
                continue;
            }
            if (pastHeader && comp instanceof JMenuItem) {
                branchCount++;
            }
        }

        assertEquals(3, branchCount, "Should show 3 branches (master/main + feature-1 + feature-2)");
    }

    @Test
    void testNonCurrentBranchIsEnabledAndClickable() throws Exception {
        git.branchCreate().setName("other-branch").call();

        BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);
        Component[] components = popup.getComponents();

        boolean foundOtherBranch = false;
        for (Component comp : components) {
            if (comp instanceof JMenuItem item) {
                String text = item.getText();
                if (text != null && text.contains("other-branch")) {
                    assertTrue(item.isEnabled(), "Non-current branch should be enabled");
                    assertNull(item.getIcon(), "Non-current branch should not have a check icon");
                    foundOtherBranch = true;
                    break;
                }
            }
        }
        assertTrue(foundOtherBranch, "Should find the other branch in the popup");
    }

    @Test
    void testBranchItemShowsCommitMessage() throws Exception {
        git.branchCreate().setName("feature-branch").call();

        BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);
        Component[] components = popup.getComponents();

        boolean foundWithMessage = false;
        for (Component comp : components) {
            if (comp instanceof JMenuItem item) {
                String text = item.getText();
                if (text != null && text.contains("feature-branch") && text.contains("Initial commit")) {
                    foundWithMessage = true;
                    break;
                }
            }
        }
        assertTrue(foundWithMessage, "Branch item should display the last commit message");
    }
}
