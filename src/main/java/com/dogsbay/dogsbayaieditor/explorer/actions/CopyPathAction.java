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

import javax.swing.AbstractAction;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;

import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to copy the absolute path of a file or folder to the system clipboard.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class CopyPathAction extends AbstractAction {
    private static final boolean DEBUG = false;

    /** The text to place on the clipboard; null disables the action. */
    private final String path;

    /**
     * Creates an action to copy the absolute path of an explorer node.
     *
     * @param node the node whose path to copy
     */
    public CopyPathAction(FileSystemNode node) {
        this(node != null ? node.getFile() : null);
    }

    /**
     * Creates an action to copy a file's absolute path.
     *
     * @param file the file whose path to copy; null disables the action
     */
    public CopyPathAction(File file) {
        this(file != null ? file.getAbsolutePath() : null);
    }

    /**
     * Creates an action to copy an already-resolved location. Used for documents that
     * have no local file — a remote document's location is its URL.
     *
     * @param path the text to copy; null or blank disables the action
     */
    public CopyPathAction(String path) {
        super("Copy Path");
        this.path = (path != null && !path.isBlank()) ? path : null;

        setEnabled(this.path != null);
    }

    /**
     * Gets the text this action puts on the clipboard.
     *
     * @return the path, or null if there is nothing to copy
     */
    public String getPath() {
        return path;
    }

    /**
     * Copies the path to the system clipboard.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (path == null) {
            return;
        }

        if (DEBUG) {
            System.out.println("CopyPathAction: Copying path " + path);
        }

        copyToClipboard(path);
    }

    /**
     * Puts text on the system clipboard, ignoring failures — a clipboard owned by
     * another application, or no clipboard at all in a headless VM.
     *
     * @param text the text to copy
     */
    static void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("CopyPathAction: Failed to copy path to clipboard");
                ex.printStackTrace();
            }
        }
    }
}
