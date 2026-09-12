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
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to copy the relative path of a file or folder to the system clipboard.
 * The path is relative to the explorer root directory.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class CopyRelativePathAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private final File file;
    private final File rootDirectory;

    /**
     * Creates an action to copy the relative path of an explorer node.
     *
     * @param node          the node whose path to copy
     * @param rootDirectory the root directory of the explorer
     */
    public CopyRelativePathAction(FileSystemNode node, File rootDirectory) {
        this(node != null ? node.getFile() : null, rootDirectory);
    }

    /**
     * Creates an action to copy a file's path relative to a root directory.
     *
     * @param file          the file whose path to copy; null disables the action
     * @param rootDirectory the directory to make the path relative to; may be null
     */
    public CopyRelativePathAction(File file, File rootDirectory) {
        super("Copy Relative Path");
        this.file = file;
        this.rootDirectory = rootDirectory;

        setEnabled(file != null);
    }

    /**
     * Works out the path of a file relative to a root directory.
     *
     * <p>Falls back to the absolute path when there is no root, or when the file lies
     * outside it. Relativising across a root boundary yields a chain of {@code ../..}
     * segments that is longer and less useful than the absolute path — a document opened
     * from outside the current explorer root is the normal way to hit this.
     *
     * @param file the file whose path is wanted
     * @param rootDirectory the directory to relativise against; may be null
     * @return the relative path, or the absolute path when relativising doesn't help
     */
    static String relativePath(File file, File rootDirectory) {
        if (rootDirectory == null) {
            return file.getAbsolutePath();
        }

        try {
            Path filePath = Paths.get(file.getAbsolutePath()).normalize();
            Path rootPath = Paths.get(rootDirectory.getAbsolutePath()).normalize();

            if (!filePath.startsWith(rootPath)) {
                return file.getAbsolutePath();
            }
            return rootPath.relativize(filePath).toString();

        } catch (RuntimeException ex) {
            // Different drives on Windows make relativize throw outright.
            if (DEBUG) {
                System.err.println("CopyRelativePathAction: Cannot relativize: " + ex.getMessage());
            }
            return file.getAbsolutePath();
        }
    }

    /**
     * Copies the relative path to the system clipboard.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (file == null) {
            return;
        }

        String relativePath = relativePath(file, rootDirectory);

        if (DEBUG) {
            System.out.println("CopyRelativePathAction: Copying relative path " + relativePath);
        }

        CopyPathAction.copyToClipboard(relativePath);
    }
}
