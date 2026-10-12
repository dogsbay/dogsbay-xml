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

    // ── Branches that live only on a remote ──────────────────────────

    /** A ref as JGit names it, which is all the naming helpers look at. */
    private static org.eclipse.jgit.lib.Ref ref(String name) {
        return new org.eclipse.jgit.lib.ObjectIdRef.Unpeeled(
                org.eclipse.jgit.lib.Ref.Storage.LOOSE, name, org.eclipse.jgit.lib.ObjectId.zeroId());
    }

    @Test
    void aBranchIsNamedTheWayAPersonReadsIt() {
        assertEquals("tutorial/01-setup",
                BranchSwitcherPopup.shortNameOf(ref("refs/heads/tutorial/01-setup")));
        assertEquals("origin/tutorial/01-setup",
                BranchSwitcherPopup.shortNameOf(ref("refs/remotes/origin/tutorial/01-setup")));
    }

    @Test
    void aRemoteBranchAlreadyCheckedOutIsNotOfferedTwice() {
        var kept = BranchSwitcherPopup.withoutDuplicateRemotes(java.util.List.of(
                ref("refs/heads/tutorial/01-setup"),
                ref("refs/remotes/origin/tutorial/01-setup"),
                ref("refs/remotes/origin/tutorial/02-topics"),
                ref("refs/remotes/origin/HEAD")),
                java.util.Set.of("origin"),
                local -> "origin");

        var names = kept.stream().map(BranchSwitcherPopup::shortNameOf).toList();
        // the checked-out stage once, the next stage from the remote, and never the remote's HEAD pointer
        assertEquals(java.util.List.of("tutorial/01-setup", "origin/tutorial/02-topics"), names);
    }

    @Test
    void theSameBranchOnASecondRemoteIsStillOffered() {
        // a reader working from their own fork: origin is theirs, upstream is the documentation team's, and the two
        // copies of a stage are not the same work
        var kept = BranchSwitcherPopup.withoutDuplicateRemotes(java.util.List.of(
                ref("refs/heads/tutorial/01-setup"),
                ref("refs/remotes/origin/tutorial/01-setup"),
                ref("refs/remotes/upstream/tutorial/01-setup")),
                java.util.Set.of("origin", "upstream"),
                local -> "origin");

        var names = kept.stream().map(BranchSwitcherPopup::shortNameOf).toList();
        assertEquals(java.util.List.of("tutorial/01-setup", "upstream/tutorial/01-setup"), names);
    }

    @Test
    void aLocalBranchThatFollowsNothingDoesNotHideTheRemoteOfTheSameName() {
        var kept = BranchSwitcherPopup.withoutDuplicateRemotes(java.util.List.of(
                ref("refs/heads/draft"),
                ref("refs/remotes/origin/draft")),
                java.util.Set.of("origin"),
                local -> null);

        var names = kept.stream().map(BranchSwitcherPopup::shortNameOf).toList();
        assertEquals(java.util.List.of("draft", "origin/draft"), names);
    }

    @Test
    void aBranchWhoseNameEndsInHeadIsNotMistakenForTheRemotePointer() {
        var kept = BranchSwitcherPopup.withoutDuplicateRemotes(java.util.List.of(
                ref("refs/remotes/origin/HEAD"),
                ref("refs/remotes/origin/feature/HEAD")),
                java.util.Set.of("origin"),
                local -> null);

        var names = kept.stream().map(BranchSwitcherPopup::shortNameOf).toList();
        assertEquals(java.util.List.of("origin/feature/HEAD"), names);
    }

    @Test
    void aRemoteWhoseOwnNameHasASlashIsSplitWhereItReallyEnds() {
        assertEquals("tutorial/01-setup",
                BranchSwitcherPopup.withoutRemote("my/fork/tutorial/01-setup", java.util.Set.of("my/fork")));
        // and the longer of two matching remotes wins
        assertEquals("tutorial/01-setup",
                BranchSwitcherPopup.withoutRemote("my/fork/tutorial/01-setup", java.util.Set.of("my", "my/fork")));
    }

    @Test
    void aStageOnTheRemoteBecomesALocalBranchThatFollowsIt() throws Exception {
        // what a reader has after cloning: the stages exist, but only on the remote
        Path remoteDir = Files.createTempDirectory("branch-switcher-remote");
        Path cloneDir = Files.createTempDirectory("branch-switcher-clone");
        try {
        try (Git remote = Git.init().setDirectory(remoteDir.toFile()).call()) {
            Files.writeString(remoteDir.resolve("a.txt"), "x");
            remote.add().addFilepattern("a.txt").call();
            remote.commit().setMessage("first").call();
            for (String stage : new String[] { "tutorial/01-setup", "tutorial/02-topics", "tutorial/26-learning" }) {
                remote.branchCreate().setName(stage).call();
            }
        }
        try (Git clone = Git.cloneRepository().setURI(remoteDir.toUri().toString())
                .setDirectory(cloneDir.toFile()).call()) {
            GitPanel panel = new GitPanel(clone);
            BranchSwitcherPopup popup = new BranchSwitcherPopup(panel);

            String labels = labelsOf(popup);
            // the naming the tutorial repository actually uses: origin/tutorial/NN-slug, several of them
            for (String stage : new String[] { "01-setup", "02-topics", "26-learning" }) {
                assertTrue(labels.contains("origin/tutorial/" + stage),
                        "stage " + stage + " should be offered: " + labels);
            }

            // through the menu item the reader actually clicks, not past it
            clickItemContaining(popup, "origin/tutorial/02-topics");

            // a local branch, not a detached head: the next edit has somewhere to belong
            assertEquals("tutorial/02-topics", clone.getRepository().getBranch());
            assertEquals("origin", clone.getRepository().getConfig()
                    .getString("branch", "tutorial/02-topics", "remote"));
        }
        } finally {
            deleteTree(remoteDir);
            deleteTree(cloneDir);
        }
    }

    /** Press the one enabled menu item whose label contains the given text. */
    private static void clickItemContaining(BranchSwitcherPopup popup, String text) {
        for (java.awt.Component c : popup.getComponents()) {
            if (c instanceof javax.swing.JMenuItem item
                    && item.getText().contains(text) && item.isEnabled()) {
                assertTrue(item.getActionListeners().length > 0, "no action on '" + item.getText() + "'");
                item.doClick();
                return;
            }
        }
        fail("no enabled menu item for " + text + " in " + labelsOf(popup));
    }

    /** Remove a directory tree, so a test that makes its own repositories does not leave them behind. */
    private static void deleteTree(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Every menu label in the popup, joined. */
    private static String labelsOf(BranchSwitcherPopup popup) {
        StringBuilder out = new StringBuilder();
        for (java.awt.Component c : popup.getComponents()) {
            if (c instanceof javax.swing.JMenuItem item) {
                out.append(item.getText()).append('\n');
            }
        }
        return out.toString();
    }
}
