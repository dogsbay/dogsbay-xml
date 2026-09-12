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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Utility class for file system operations in the File Explorer.
 * Provides static methods for creating, copying, moving, and deleting files and directories.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class FileOperationUtils {
    private static final boolean DEBUG = false;

    // Invalid filename characters (Windows-based, also valid for Linux)
    private static final String INVALID_CHARS = "<>:\"/\\|?*";
    private static final Pattern INVALID_PATTERN = Pattern.compile("[" + Pattern.quote(INVALID_CHARS) + "]");

    // Reserved Windows filenames
    private static final String[] RESERVED_NAMES = {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    /**
     * Private constructor to prevent instantiation.
     */
    private FileOperationUtils() {
    }

    /**
     * Validates a filename for illegal characters and reserved names.
     *
     * @param name the filename to validate
     * @return true if the filename is valid, false otherwise
     */
    public static boolean isValidFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String trimmed = name.trim();

        // Check for illegal characters
        if (INVALID_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // Check for reserved names (Windows)
        String upperName = trimmed.toUpperCase();
        for (String reserved : RESERVED_NAMES) {
            if (upperName.equals(reserved) || upperName.startsWith(reserved + ".")) {
                return false;
            }
        }

        // Check for names ending with period or space (Windows restriction)
        if (trimmed.endsWith(".") || trimmed.endsWith(" ")) {
            return false;
        }

        return true;
    }

    /**
     * Generates a unique filename by appending a number if the file already exists.
     *
     * @param parent   the parent directory
     * @param baseName the base name for the file
     * @return a unique filename
     */
    public static String generateUniqueFileName(File parent, String baseName) {
        String name = baseName;
        int counter = 1;

        // Split name and extension
        String nameWithoutExt = baseName;
        String extension = "";
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < baseName.length() - 1) {
            nameWithoutExt = baseName.substring(0, lastDot);
            extension = baseName.substring(lastDot);
        }

        while (new File(parent, name).exists()) {
            name = nameWithoutExt + " (" + counter + ")" + extension;
            counter++;
        }

        return name;
    }

    /**
     * Creates a new file in the specified parent directory.
     *
     * @param parent the parent directory
     * @param name   the name of the file to create
     * @return the created file
     * @throws IOException if the file cannot be created
     */
    public static File createFile(File parent, String name) throws IOException {
        if (parent == null || !parent.exists() || !parent.isDirectory()) {
            throw new IOException("Parent directory does not exist or is not a directory");
        }

        if (!isValidFileName(name)) {
            throw new IOException("Invalid filename: " + name);
        }

        File newFile = new File(parent, name.trim());

        if (newFile.exists()) {
            throw new IOException("File already exists: " + name);
        }

        if (!newFile.createNewFile()) {
            throw new IOException("Failed to create file: " + name);
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Created file " + newFile.getAbsolutePath());
        }

        return newFile;
    }

    /**
     * Creates a new directory in the specified parent directory.
     *
     * @param parent the parent directory
     * @param name   the name of the directory to create
     * @return the created directory
     * @throws IOException if the directory cannot be created
     */
    public static File createDirectory(File parent, String name) throws IOException {
        if (parent == null || !parent.exists() || !parent.isDirectory()) {
            throw new IOException("Parent directory does not exist or is not a directory");
        }

        if (!isValidFileName(name)) {
            throw new IOException("Invalid directory name: " + name);
        }

        File newDir = new File(parent, name.trim());

        if (newDir.exists()) {
            throw new IOException("Directory already exists: " + name);
        }

        if (!newDir.mkdir()) {
            throw new IOException("Failed to create directory: " + name);
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Created directory " + newDir.getAbsolutePath());
        }

        return newDir;
    }

    /**
     * Copies a file from source to destination.
     *
     * @param src       the source file
     * @param dest      the destination file
     * @param overwrite whether to overwrite if destination exists
     * @return true if the file was copied successfully
     * @throws IOException if the copy operation fails
     */
    public static boolean copyFile(File src, File dest, boolean overwrite) throws IOException {
        if (src == null || !src.exists() || !src.isFile()) {
            throw new IOException("Source file does not exist or is not a file: " + src);
        }

        if (dest == null) {
            throw new IOException("Destination file is null");
        }

        if (dest.exists() && !overwrite) {
            return false; // Don't overwrite
        }

        // Use Java NIO for efficient file copying
        if (overwrite) {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(src.toPath(), dest.toPath());
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Copied file " + src.getAbsolutePath() +
                             " to " + dest.getAbsolutePath());
        }

        return true;
    }

    /**
     * Copies a directory recursively from source to destination.
     *
     * @param src       the source directory
     * @param dest      the destination directory
     * @param overwrite whether to overwrite if files exist
     * @return true if the directory was copied successfully
     * @throws IOException if the copy operation fails
     */
    public static boolean copyDirectory(File src, File dest, boolean overwrite) throws IOException {
        if (src == null || !src.exists() || !src.isDirectory()) {
            throw new IOException("Source directory does not exist or is not a directory: " + src);
        }

        if (dest == null) {
            throw new IOException("Destination directory is null");
        }

        // Prevent copying a directory into itself
        if (dest.getAbsolutePath().startsWith(src.getAbsolutePath() + File.separator)) {
            throw new IOException("Cannot copy directory into itself");
        }

        // Create destination directory if it doesn't exist
        if (!dest.exists()) {
            if (!dest.mkdirs()) {
                throw new IOException("Failed to create destination directory: " + dest);
            }
        }

        // Copy all files and subdirectories
        File[] files = src.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(dest, file.getName());

                if (file.isDirectory()) {
                    copyDirectory(file, destFile, overwrite);
                } else {
                    copyFile(file, destFile, overwrite);
                }
            }
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Copied directory " + src.getAbsolutePath() +
                             " to " + dest.getAbsolutePath());
        }

        return true;
    }

    /**
     * Moves a file from source to destination.
     *
     * @param src  the source file
     * @param dest the destination file
     * @return true if the file was moved successfully
     * @throws IOException if the move operation fails
     */
    public static boolean moveFile(File src, File dest) throws IOException {
        if (src == null || !src.exists()) {
            throw new IOException("Source file does not exist: " + src);
        }

        if (dest == null) {
            throw new IOException("Destination file is null");
        }

        if (dest.exists()) {
            throw new IOException("Destination file already exists: " + dest);
        }

        // Try atomic rename first (works if on same filesystem)
        if (src.renameTo(dest)) {
            if (DEBUG) {
                System.out.println("FileOperationUtils: Moved file " + src.getAbsolutePath() +
                                 " to " + dest.getAbsolutePath());
            }
            return true;
        }

        // Fallback to copy + delete (across filesystems)
        if (src.isDirectory()) {
            copyDirectory(src, dest, false);
            deleteDirectory(src);
        } else {
            copyFile(src, dest, false);
            if (!src.delete()) {
                throw new IOException("Failed to delete source file after copy: " + src);
            }
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Moved file (via copy+delete) " +
                             src.getAbsolutePath() + " to " + dest.getAbsolutePath());
        }

        return true;
    }

    /**
     * Deletes a file.
     *
     * @param file the file to delete
     * @return true if the file was deleted successfully
     * @throws IOException if the delete operation fails
     */
    public static boolean deleteFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            throw new IOException("Cannot delete directory with deleteFile: " + file);
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete file: " + file);
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Deleted file " + file.getAbsolutePath());
        }

        return true;
    }

    /**
     * Deletes a directory recursively.
     *
     * @param dir the directory to delete
     * @return true if the directory was deleted successfully
     * @throws IOException if the delete operation fails
     */
    public static boolean deleteDirectory(File dir) throws IOException {
        if (dir == null || !dir.exists()) {
            return false;
        }

        if (!dir.isDirectory()) {
            throw new IOException("Cannot delete non-directory with deleteDirectory: " + dir);
        }

        // Recursively delete contents
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        throw new IOException("Failed to delete file: " + file);
                    }
                }
            }
        }

        // Delete the directory itself
        if (!dir.delete()) {
            throw new IOException("Failed to delete directory: " + dir);
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Deleted directory " + dir.getAbsolutePath());
        }

        return true;
    }

    /**
     * Renames a file or directory.
     *
     * @param file    the file to rename
     * @param newName the new name
     * @return the renamed file
     * @throws IOException if the rename operation fails
     */
    public static File renameFile(File file, String newName) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + file);
        }

        if (!isValidFileName(newName)) {
            throw new IOException("Invalid filename: " + newName);
        }

        File newFile = new File(file.getParentFile(), newName.trim());

        if (newFile.exists()) {
            throw new IOException("A file or directory with that name already exists: " + newName);
        }

        if (!file.renameTo(newFile)) {
            throw new IOException("Failed to rename file: " + file);
        }

        if (DEBUG) {
            System.out.println("FileOperationUtils: Renamed file " + file.getAbsolutePath() +
                             " to " + newFile.getAbsolutePath());
        }

        return newFile;
    }

    /**
     * Generates a unique filename for copying a file in the same directory.
     * Uses VSCode-style naming: "filename Copy.ext", "filename Copy 2.ext", etc.
     *
     * @param sourceFile the source file being copied
     * @param targetDir  the target directory
     * @return a File object with a unique name in the target directory
     */
    public static File generateCopyName(File sourceFile, File targetDir) {
        if (sourceFile == null || targetDir == null) {
            return null;
        }

        String originalName = sourceFile.getName();
        String baseName;
        String extension = "";

        // Split filename and extension (only for files, not directories)
        if (!sourceFile.isDirectory() && originalName.contains(".")) {
            int lastDot = originalName.lastIndexOf('.');
            baseName = originalName.substring(0, lastDot);
            extension = originalName.substring(lastDot); // includes the dot
        } else {
            baseName = originalName;
        }

        // Try "filename Copy.ext" first
        String copyName = baseName + " Copy" + extension;
        File copyFile = new File(targetDir, copyName);

        if (!copyFile.exists()) {
            return copyFile;
        }

        // Try "filename Copy 2.ext", "filename Copy 3.ext", etc.
        int counter = 2;
        while (counter < 1000) { // Safety limit
            copyName = baseName + " Copy " + counter + extension;
            copyFile = new File(targetDir, copyName);

            if (!copyFile.exists()) {
                return copyFile;
            }

            counter++;
        }

        // Fallback: use timestamp if we somehow hit the limit
        long timestamp = System.currentTimeMillis();
        copyName = baseName + " Copy " + timestamp + extension;
        return new File(targetDir, copyName);
    }
}
