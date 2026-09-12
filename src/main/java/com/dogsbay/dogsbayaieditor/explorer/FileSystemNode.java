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

package com.dogsbay.dogsbayaieditor.explorer;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.Arrays;

/**
 * Tree node representing a file system file or directory.
 * Supports lazy loading of directory contents.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class FileSystemNode extends DefaultMutableTreeNode {
    private static final boolean DEBUG = false;

    /** Whether hidden entries (dot files, {@code .dogsbay/}) are listed. Process-wide, set from the explorer. */
    private static volatile boolean showHidden;

    public static void setShowHidden(boolean show) {
        showHidden = show;
    }

    public static boolean isShowHidden() {
        return showHidden;
    }

    /** True when {@code f} should appear in the tree. */
    static boolean isVisible(File f) {
        return showHidden || !f.isHidden();
    }

    private File file;
    private boolean loaded = false;
    private boolean isDirectory;
    private boolean isCut = false;

    /**
     * Creates a new file system node for the given file.
     *
     * @param file the file or directory this node represents
     */
    public FileSystemNode(File file) {
        super(file);
        this.file = file;
        this.isDirectory = file.isDirectory();

        // Add placeholder child for directories to make them expandable
        if (isDirectory) {
            add(new DefaultMutableTreeNode("Loading..."));
        }
    }

    /**
     * Gets the file this node represents.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Checks if this node represents a directory.
     *
     * @return true if this is a directory
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * Checks if the children of this directory have been loaded.
     *
     * @return true if loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Loads the children of this directory node.
     * Does nothing if already loaded or if this is not a directory.
     */
    public void loadChildren() {
        if (loaded || !isDirectory) {
            return;
        }

        if (DEBUG) {
            System.out.println("FileSystemNode.loadChildren: Loading " + file.getAbsolutePath());
        }

        // Remove placeholder
        removeAllChildren();

        File[] files = file.listFiles();
        if (files != null) {
            // Sort: directories first, then alphabetically
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                } else {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });

            int count = 0;
            for (File child : files) {
                if (isVisible(child)) {
                    add(new FileSystemNode(child));
                    count++;
                }
            }

            if (DEBUG) {
                System.out.println("  Loaded " + count + " children");
            }
        } else {
            if (DEBUG) {
                System.out.println("  No permission or error reading directory");
            }
        }

        loaded = true;
    }

    /**
     * Refreshes this directory node by incrementally updating its children.
     * Only adds new files/folders and removes deleted ones, preserving existing
     * nodes.
     * Does nothing if this is not a directory.
     */
    public void refresh() {
        refresh(null);
    }

    /**
     * Refreshes this directory node by incrementally updating its children.
     * Only adds new files/folders and removes deleted ones, preserving existing
     * nodes.
     * Does nothing if this is not a directory.
     * 
     * @param model the tree model to notify of changes, or null to just update
     *              structure
     */
    public void refresh(javax.swing.tree.DefaultTreeModel model) {
        if (!isDirectory || !loaded) {
            return;
        }

        if (DEBUG) {
            System.out.println("FileSystemNode.refresh: " + file.getAbsolutePath());
        }

        // Get current files from file system
        File[] currentFiles = file.listFiles();
        if (currentFiles == null) {
            return; // No permission or error
        }

        // Build a map of current files for quick lookup
        java.util.Map<String, File> currentFileMap = new java.util.HashMap<>();
        for (File f : currentFiles) {
            if (isVisible(f)) {
                currentFileMap.put(f.getName(), f);
            }
        }

        // Build a map of existing child nodes
        java.util.Map<String, FileSystemNode> existingNodes = new java.util.HashMap<>();
        java.util.List<FileSystemNode> nodesToRemove = new java.util.ArrayList<>();

        for (int i = 0; i < getChildCount(); i++) {
            Object child = getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode node = (FileSystemNode) child;
                String name = node.getFile().getName();
                existingNodes.put(name, node);

                // Mark for removal if file no longer exists
                if (!currentFileMap.containsKey(name)) {
                    nodesToRemove.add(node);
                }
            }
        }

        // Remove deleted nodes
        for (FileSystemNode node : nodesToRemove) {
            if (model != null) {
                model.removeNodeFromParent(node);
            } else {
                remove(node);
            }
            if (DEBUG) {
                System.out.println("  Removed: " + node.getFile().getName());
            }
        }

        // Add new nodes
        for (File f : currentFiles) {
            if (isVisible(f) && !existingNodes.containsKey(f.getName())) {
                FileSystemNode newNode = new FileSystemNode(f);

                // Insert in sorted position (directories first, then alphabetically)
                int insertIndex = findInsertPosition(newNode);

                if (model != null) {
                    model.insertNodeInto(newNode, this, insertIndex);
                } else {
                    insert(newNode, insertIndex);
                }

                if (DEBUG) {
                    System.out.println("  Added: " + f.getName());
                }
            }
        }
    }

    /**
     * Finds the correct insertion position for a new node to maintain sort order.
     * Directories come first, then files, both sorted alphabetically.
     */
    private int findInsertPosition(FileSystemNode newNode) {
        boolean newIsDir = newNode.isDirectory();
        String newName = newNode.getFile().getName();

        for (int i = 0; i < getChildCount(); i++) {
            Object child = getChildAt(i);
            if (!(child instanceof FileSystemNode)) {
                continue;
            }

            FileSystemNode existingNode = (FileSystemNode) child;
            boolean existingIsDir = existingNode.isDirectory();
            String existingName = existingNode.getFile().getName();

            // Directories come before files
            if (newIsDir && !existingIsDir) {
                // New is directory, existing is file -> insert here (before file)
                return i;
            }
            if (!newIsDir && existingIsDir) {
                // New is file, existing is directory -> skip (file comes after directory)
                continue;
            }

            // Within same type (both dirs or both files), sort alphabetically
            if (newName.compareToIgnoreCase(existingName) < 0) {
                return i;
            }
        }

        return getChildCount();
    }

    /**
     * Checks if this node is marked as cut (for visual feedback).
     *
     * @return true if this node is cut
     */
    public boolean isCut() {
        return isCut;
    }

    /**
     * Sets whether this node is marked as cut (for visual feedback).
     *
     * @param cut true to mark as cut, false to clear
     */
    public void setCut(boolean cut) {
        this.isCut = cut;
    }

    /**
     * Returns the display name for this node.
     *
     * @return the file name, or full path for root directories
     */
    @Override
    public String toString() {
        String name = file.getName();
        // For root directories (like "C:\") getName() returns empty string
        return name.isEmpty() ? file.getAbsolutePath() : name;
    }
}
