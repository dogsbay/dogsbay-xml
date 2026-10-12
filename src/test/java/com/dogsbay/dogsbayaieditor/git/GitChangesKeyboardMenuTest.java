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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.KeyStroke;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Changes list's context menu is the only place to stage, unstage or
 * discard a single file, and it opened only from a pointer. These pin the
 * keyboard route. Showing the menu itself needs a display, so the tests stop at
 * the binding and at where the menu would appear.
 */
class GitChangesKeyboardMenuTest {

    @TempDir Path dir;

    private Git git;
    private GitPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(dir.toFile()).call();
        Files.writeString(dir.resolve("a.txt"), "one");
        git.add().addFilepattern("a.txt").call();
        git.commit().setMessage("Initial commit").call();
        Files.writeString(dir.resolve("a.txt"), "changed");

        panel = new GitPanel(git, dir.toFile(), branch -> { });
        panel.loadGitStatus();
        expandAll(panel.changesTree());
    }

    @AfterEach
    void tearDown() {
        git.getRepository().close();
        git.close();
    }

    private static void expandAll(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    /** The row showing a file rather than a section header. */
    private static int fileRow(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object last = tree.getPathForRow(row).getLastPathComponent();
            if (last instanceof GitFileNode) {
                return row;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("Shift+F10 and the Menu key both open the menu")
    void bothKeysAreBound() {
        JTree tree = panel.changesTree();
        var inputs = tree.getInputMap(JComponent.WHEN_FOCUSED);

        assertThat(inputs.get(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK)))
                .isEqualTo(GitPanel.CONTEXT_MENU_ACTION);
        assertThat(inputs.get(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)))
                .isEqualTo(GitPanel.CONTEXT_MENU_ACTION);
        assertThat(tree.getActionMap().get(GitPanel.CONTEXT_MENU_ACTION)).isNotNull();
    }

    @Test
    @DisplayName("the menu opens beside the selected file, not in a corner")
    void theMenuOpensAtTheSelection() {
        JTree tree = panel.changesTree();
        int row = fileRow(tree);
        assertThat(row).as("a changed file is listed").isNotNegative();
        tree.setSelectionRow(row);

        Point at = panel.contextMenuLocation();
        Rectangle bounds = tree.getRowBounds(row);

        assertThat(at).isNotNull();
        assertThat(at.x).isBetween(bounds.x, bounds.x + bounds.width);
        assertThat(at.y).isEqualTo(bounds.y + bounds.height);
    }

    @Test
    @DisplayName("with nothing selected there is nowhere to open it")
    void nothingSelectedOpensNothing() {
        panel.changesTree().clearSelection();

        assertThat(panel.contextMenuLocation()).isNull();
    }
}
