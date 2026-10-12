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

package com.dogsbay.dogsbayaieditor;

import com.dogsbay.dogsbayaieditor.explorer.actions.CopyPathAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests which Copy Path / Copy Relative Path actions the document-tab context menu offers
 * for a given document location.
 */
public class TabCopyPathMenuTest {

    @TempDir
    Path tempDir;

    private Action[] actionsFor(URL url, File root) {
        return DogsBayTabbedView.copyPathActionsFor(url, root);
    }

    @Test
    void aSavedLocalDocumentGetsBothActions() throws Exception {
        File root = tempDir.toFile();
        File file = tempDir.resolve("topics").resolve("install.dita").toFile();

        Action[] actions = actionsFor(file.toURI().toURL(), root);

        assertEquals(2, actions.length);
        assertTrue(actions[0].isEnabled(), "Copy Path should be available");
        assertTrue(actions[1].isEnabled(), "Copy Relative Path should be available");
        assertEquals("Copy Path", actions[0].getValue(Action.NAME));
        assertEquals("Copy Relative Path", actions[1].getValue(Action.NAME));
        assertEquals(file.getAbsolutePath(), ((CopyPathAction) actions[0]).getPath());
    }

    @Test
    void anUnsavedDocumentGetsBothActionsDisabled() {
        Action[] actions = actionsFor(null, tempDir.toFile());

        assertFalse(actions[0].isEnabled(),
                "a document that has never been saved has no path to copy");
        assertFalse(actions[1].isEnabled());
    }

    /**
     * A remote document has a location but no local file, so its URL is what Copy Path
     * should yield — and there is nothing for a relative path to be relative to.
     */
    @Test
    void aRemoteDocumentCopiesItsUrlAndHasNoRelativePath() throws Exception {
        Action[] actions = actionsFor(new URL("https://example.com/docs/install.dita"),
                tempDir.toFile());

        assertTrue(actions[0].isEnabled());
        assertEquals("https://example.com/docs/install.dita",
                ((CopyPathAction) actions[0]).getPath());
        assertFalse(actions[1].isEnabled(), "no local root to be relative to");
    }

    @Test
    void aLocalDocumentWithoutAnExplorerRootStillCopiesItsPath() throws Exception {
        File file = tempDir.resolve("a.xml").toFile();

        Action[] actions = actionsFor(file.toURI().toURL(), null);

        assertTrue(actions[0].isEnabled());
        assertTrue(actions[1].isEnabled(), "relative falls back to the absolute path");
        assertEquals(file.getAbsolutePath(), ((CopyPathAction) actions[0]).getPath());
    }

    @Test
    void aPathWithSpacesIsCopiedDecodedNotPercentEncoded() throws Exception {
        File file = tempDir.resolve("my topics").resolve("a b.dita").toFile();

        Action[] actions = actionsFor(file.toURI().toURL(), tempDir.toFile());

        String copied = ((CopyPathAction) actions[0]).getPath();
        assertFalse(copied.contains("%20"),
                "a path pasted into a terminal must not be percent-encoded");
        assertTrue(copied.endsWith("a b.dita"));
    }
}
