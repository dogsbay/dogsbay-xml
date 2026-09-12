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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton clipboard for file explorer operations.
 * Manages copy and cut operations for files and directories.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class FileExplorerClipboard {
    private static final boolean DEBUG = false;
    private static FileExplorerClipboard instance;

    private List<File> files;
    private ClipboardOperation operation;

    /**
     * Clipboard operation type.
     */
    public enum ClipboardOperation {
        COPY,
        CUT
    }

    /**
     * Private constructor for singleton pattern.
     */
    private FileExplorerClipboard() {
        files = new ArrayList<>();
        operation = null;
    }

    /**
     * Gets the singleton instance.
     *
     * @return the clipboard instance
     */
    public static synchronized FileExplorerClipboard getInstance() {
        if (instance == null) {
            instance = new FileExplorerClipboard();
        }
        return instance;
    }

    /**
     * Copies files to the clipboard.
     *
     * @param filesToCopy the files to copy
     */
    public synchronized void copy(List<File> filesToCopy) {
        if (filesToCopy == null || filesToCopy.isEmpty()) {
            if (DEBUG) {
                System.out.println("FileExplorerClipboard: No files to copy");
            }
            return;
        }

        this.files = new ArrayList<>(filesToCopy);
        this.operation = ClipboardOperation.COPY;

        if (DEBUG) {
            System.out.println("FileExplorerClipboard: Copied " + files.size() + " files");
        }
    }

    /**
     * Copies a single file to the clipboard.
     *
     * @param file the file to copy
     */
    public synchronized void copy(File file) {
        if (file == null) {
            return;
        }

        List<File> fileList = new ArrayList<>();
        fileList.add(file);
        copy(fileList);
    }

    /**
     * Cuts files to the clipboard.
     *
     * @param filesToCut the files to cut
     */
    public synchronized void cut(List<File> filesToCut) {
        if (filesToCut == null || filesToCut.isEmpty()) {
            if (DEBUG) {
                System.out.println("FileExplorerClipboard: No files to cut");
            }
            return;
        }

        this.files = new ArrayList<>(filesToCut);
        this.operation = ClipboardOperation.CUT;

        if (DEBUG) {
            System.out.println("FileExplorerClipboard: Cut " + files.size() + " files");
        }
    }

    /**
     * Cuts a single file to the clipboard.
     *
     * @param file the file to cut
     */
    public synchronized void cut(File file) {
        if (file == null) {
            return;
        }

        List<File> fileList = new ArrayList<>();
        fileList.add(file);
        cut(fileList);
    }

    /**
     * Pastes the clipboard contents to the target directory.
     *
     * @param targetDir   the target directory
     * @param overwriteMode  0=ask for each, 1=yes to all, 2=no to all
     * @return PasteResult containing success status and list of conflicts
     * @throws IOException if the paste operation fails
     */
    public synchronized PasteResult paste(File targetDir, int overwriteMode) throws IOException {
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
            throw new IOException("Target directory does not exist or is not a directory");
        }

        if (isEmpty()) {
            if (DEBUG) {
                System.out.println("FileExplorerClipboard: Nothing to paste");
            }
            return new PasteResult(false, new ArrayList<>());
        }

        List<File> conflicts = new ArrayList<>();
        List<File> successfullyPasted = new ArrayList<>();
        List<File> pastedDestFiles = new ArrayList<>(); // Track destination files
        boolean overwriteAll = (overwriteMode == 1);
        boolean skipAll = (overwriteMode == 2);

        // Validate all source files still exist
        for (File file : files) {
            if (!file.exists()) {
                throw new IOException("Source file no longer exists: " + file.getName());
            }
        }

        // Perform paste operation
        for (File file : files) {
            File destFile;
            boolean isSameLocation = file.getParentFile() != null &&
                                    file.getParentFile().equals(targetDir);

            // For COPY operations in the same location, generate a unique name (VSCode-style)
            if (operation == ClipboardOperation.COPY && isSameLocation) {
                destFile = FileOperationUtils.generateCopyName(file, targetDir);
                if (DEBUG) {
                    System.out.println("FileExplorerClipboard: Same-location copy, using name: " +
                                     destFile.getName());
                }
            } else {
                destFile = new File(targetDir, file.getName());

                // Check for conflicts (only for different-location pastes)
                if (destFile.exists()) {
                    if (skipAll) {
                        continue; // Skip this file
                    }
                    if (!overwriteAll && overwriteMode == 0) {
                        conflicts.add(file);
                        continue; // Will handle in second pass
                    }
                }
            }

            // Perform the operation
            try {
                if (operation == ClipboardOperation.COPY) {
                    if (file.isDirectory()) {
                        FileOperationUtils.copyDirectory(file, destFile, overwriteAll);
                    } else {
                        FileOperationUtils.copyFile(file, destFile, overwriteAll);
                    }
                } else { // CUT
                    FileOperationUtils.moveFile(file, destFile);
                }
                successfullyPasted.add(file);
                pastedDestFiles.add(destFile); // Track the destination file

                if (DEBUG) {
                    System.out.println("FileExplorerClipboard: Pasted " + file.getName() +
                                     " to " + targetDir.getAbsolutePath());
                }
            } catch (IOException e) {
                throw new IOException("Failed to paste " + file.getName() + ": " + e.getMessage(), e);
            }
        }

        // If this was a cut operation and all files were pasted successfully, clear clipboard
        if (operation == ClipboardOperation.CUT && conflicts.isEmpty()) {
            clear();
        }

        return new PasteResult(true, conflicts, pastedDestFiles);
    }

    /**
     * Clears the clipboard.
     */
    public synchronized void clear() {
        this.files.clear();
        this.operation = null;

        if (DEBUG) {
            System.out.println("FileExplorerClipboard: Cleared");
        }
    }

    /**
     * Checks if the clipboard is empty.
     *
     * @return true if the clipboard is empty
     */
    public synchronized boolean isEmpty() {
        return files.isEmpty() || operation == null;
    }

    /**
     * Gets the current operation type.
     *
     * @return the operation type, or null if clipboard is empty
     */
    public synchronized ClipboardOperation getOperation() {
        return operation;
    }

    /**
     * Gets an unmodifiable list of files in the clipboard.
     *
     * @return the list of files
     */
    public synchronized List<File> getFiles() {
        return Collections.unmodifiableList(files);
    }

    /**
     * Result of a paste operation.
     */
    public static class PasteResult {
        private final boolean success;
        private final List<File> conflicts;
        private final List<File> pastedFiles;

        public PasteResult(boolean success, List<File> conflicts) {
            this(success, conflicts, new ArrayList<>());
        }

        public PasteResult(boolean success, List<File> conflicts, List<File> pastedFiles) {
            this.success = success;
            this.conflicts = conflicts;
            this.pastedFiles = pastedFiles;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<File> getConflicts() {
            return conflicts;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public List<File> getPastedFiles() {
            return pastedFiles;
        }
    }
}
