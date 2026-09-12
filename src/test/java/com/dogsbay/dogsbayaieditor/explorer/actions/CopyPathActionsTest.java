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

package com.dogsbay.dogsbayaieditor.explorer.actions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Copy Path / Copy Relative Path actions, shared by the File Explorer
 * context menu and the document-tab context menu.
 *
 * <p>Only the path computation and the enabled/disabled states are covered — writing to
 * the system clipboard needs a display, and these tests run headless.
 */
public class CopyPathActionsTest {

    @TempDir
    Path tempDir;

    @Test
    void relativePathIsRelativeToTheRoot() {
        File root = tempDir.toFile();
        File file = tempDir.resolve("docs").resolve("guide.dita").toFile();

        assertEquals(Path.of("docs", "guide.dita").toString(),
                CopyRelativePathAction.relativePath(file, root));
    }

    @Test
    void aFileDirectlyInTheRootIsJustItsName() {
        File root = tempDir.toFile();
        File file = tempDir.resolve("map.ditamap").toFile();

        assertEquals("map.ditamap", CopyRelativePathAction.relativePath(file, root));
    }

    @Test
    void withoutARootTheAbsolutePathIsUsed() {
        File file = tempDir.resolve("guide.dita").toFile();

        assertEquals(file.getAbsolutePath(), CopyRelativePathAction.relativePath(file, null));
    }

    /**
     * Relativising across a root boundary produces a chain of {@code ../..} segments that
     * is longer and less useful than the absolute path. Opening a document from outside
     * the current explorer root is the normal way to hit this.
     */
    @Test
    void aFileOutsideTheRootFallsBackToTheAbsolutePath() {
        File root = tempDir.resolve("project").toFile();
        File file = tempDir.resolve("elsewhere").resolve("notes.xml").toFile();

        String result = CopyRelativePathAction.relativePath(file, root);

        assertEquals(file.getAbsolutePath(), result);
        assertFalse(result.contains(".."), "should not emit a ../.. chain");
    }

    @Test
    void aSiblingOfTheRootFallsBackToTheAbsolutePath() {
        File root = tempDir.resolve("project").toFile();
        File file = tempDir.resolve("project-notes").resolve("a.xml").toFile();

        // Guards against a prefix-match bug: "project-notes" starts with "project" as a
        // string, but is not inside it as a path.
        assertEquals(file.getAbsolutePath(),
                CopyRelativePathAction.relativePath(file, root));
    }

    @Test
    void unnormalisedRootsStillRelativise() {
        File root = tempDir.resolve("project").resolve("..").resolve("project").toFile();
        File file = tempDir.resolve("project").resolve("a.xml").toFile();

        assertEquals("a.xml", CopyRelativePathAction.relativePath(file, root));
    }

    @Test
    void copyPathIsEnabledForAFile() {
        CopyPathAction action = new CopyPathAction(tempDir.resolve("a.xml").toFile());

        assertTrue(action.isEnabled());
        assertEquals("Copy Path", action.getValue(Action.NAME));
    }

    @Test
    void copyPathIsDisabledWithoutALocation() {
        assertFalse(new CopyPathAction((File) null).isEnabled(),
                "a document that has never been saved has no path to copy");
        assertFalse(new CopyPathAction((String) null).isEnabled());
        assertFalse(new CopyPathAction("   ").isEnabled());
    }

    @Test
    void copyPathAcceptsARemoteLocation() {
        CopyPathAction action = new CopyPathAction("https://example.com/docs/a.xml");

        assertTrue(action.isEnabled(),
                "a remote document has no local file but does have a location");
    }

    @Test
    void copyRelativePathIsDisabledWithoutAFile() {
        assertFalse(new CopyRelativePathAction((File) null, tempDir.toFile()).isEnabled(),
                "nothing to relativise for a remote or unsaved document");
    }

    @Test
    void copyRelativePathIsEnabledEvenWithoutARoot() {
        CopyRelativePathAction action =
                new CopyRelativePathAction(tempDir.resolve("a.xml").toFile(), null);

        assertTrue(action.isEnabled(), "falls back to the absolute path");
        assertEquals("Copy Relative Path", action.getValue(Action.NAME));
    }
}
