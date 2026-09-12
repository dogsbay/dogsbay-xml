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

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;

/**
 * Tree node representing a file with Git status information.
 * Used in the Git changes tree to display modified, added, deleted, and staged files.
 */
public class GitFileNode extends DefaultMutableTreeNode {

    /**
     * Git file status enumeration matching JGit status values.
     */
    public enum GitStatus {
        MODIFIED,       // Modified but not staged
        ADDED,          // New file added and staged
        DELETED,        // File deleted
        STAGED,         // Modified and staged for commit
        UNTRACKED,      // New file not yet tracked
        CONFLICT,       // Merge conflict
        REMOVED         // File removed from index
    }

    private final File file;
    private final GitStatus status;
    private final String relativePath;

    /**
     * Creates a new Git file node.
     *
     * @param file The file this node represents
     * @param status The Git status of the file
     * @param relativePath The path relative to repository root
     */
    public GitFileNode(File file, GitStatus status, String relativePath) {
        super(file.getName());
        this.file = file;
        this.status = status;
        this.relativePath = relativePath;
    }

    /**
     * Gets the file this node represents.
     *
     * @return The file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the Git status of this file.
     *
     * @return The status
     */
    public GitStatus getStatus() {
        return status;
    }

    /**
     * Gets the path relative to the repository root.
     *
     * @return The relative path
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Returns a string representation showing file name and status.
     */
    @Override
    public String toString() {
        return file.getName();
    }
}
