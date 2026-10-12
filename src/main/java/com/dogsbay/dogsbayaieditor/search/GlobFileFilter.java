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

package com.dogsbay.dogsbayaieditor.search;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * File filter that uses glob patterns for matching file paths.
 * Supports both include and exclude patterns with ** for recursive matching.
 *
 * Examples:
 * - *.xml matches all XML files in current directory
 * - **\/*.xml matches all XML files in any subdirectory
 * - src/**\/*.java matches all Java files under src directory
 * - target/** excludes entire target directory
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class GlobFileFilter implements FileFilter {
    private static final boolean DEBUG = true;

    private List<Pattern> includePatterns;
    private List<Pattern> excludePatterns;
    private File searchRoot;

    // Default exclude patterns for common build/config directories
    private static final String[] DEFAULT_EXCLUDES = {
        ".git/**",
        "target/**",
        "build/**",
        "node_modules/**",
        ".idea/**",
        ".vscode/**",
        "*.class",
        "*.jar",
        "*.zip",
        "*.war",
        "*.ear",
        "*.log"
    };

    /**
     * Creates a new glob file filter.
     *
     * @param searchRoot the root directory being searched (for relative path calculation)
     * @param includeGlob comma-separated include patterns (e.g., "*.xml,*.java")
     * @param excludeGlob comma-separated exclude patterns (e.g., "target/**,*.class")
     */
    public GlobFileFilter(File searchRoot, String includeGlob, String excludeGlob) {
        this.searchRoot = searchRoot;
        this.includePatterns = new ArrayList<>();
        this.excludePatterns = new ArrayList<>();

        // Parse include patterns
        if (includeGlob != null && !includeGlob.trim().isEmpty()) {
            String[] patterns = includeGlob.split(",");
            for (String pattern : patterns) {
                pattern = pattern.trim();
                if (!pattern.isEmpty()) {
                    includePatterns.add(convertGlobToRegex(pattern));
                }
            }
        }

        // If no include patterns, include all files by default
        if (includePatterns.isEmpty()) {
            includePatterns.add(Pattern.compile(".*"));
        }

        // Parse exclude patterns
        if (excludeGlob != null && !excludeGlob.trim().isEmpty()) {
            String[] patterns = excludeGlob.split(",");
            for (String pattern : patterns) {
                pattern = pattern.trim();
                if (!pattern.isEmpty()) {
                    excludePatterns.add(convertGlobToRegex(pattern));
                }
            }
        }
    }

    /**
     * Creates a new glob file filter with default excludes.
     *
     * @param searchRoot the root directory being searched
     * @param includeGlob comma-separated include patterns
     */
    public GlobFileFilter(File searchRoot, String includeGlob) {
        this(searchRoot, includeGlob, String.join(",", DEFAULT_EXCLUDES));
    }

    @Override
    public boolean accept(File file) {
        if (file == null) {
            return false;
        }

        // Always accept the search root itself
        if (file.equals(searchRoot)) {
            return true;
        }

        // Get relative path from search root
        String relativePath = getRelativePath(file);

        // For directories, check if they match exclude patterns
        if (file.isDirectory()) {
            // Check if directory is excluded
            for (Pattern pattern : excludePatterns) {
                if (pattern.matcher(relativePath).matches() ||
                    pattern.matcher(relativePath + "/").matches()) {
                    if (DEBUG) {
                        System.out.println("GlobFileFilter: Excluding directory: " + relativePath);
                    }
                    return false;
                }
            }
            return true; // Accept directories that aren't excluded
        }

        // For files, check exclude patterns first
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(relativePath).matches()) {
                if (DEBUG) {
                    System.out.println("GlobFileFilter: Excluding file: " + relativePath);
                }
                return false;
            }
        }

        // Then check include patterns
        for (Pattern pattern : includePatterns) {
            if (pattern.matcher(relativePath).matches()) {
                if (DEBUG) {
                    System.out.println("GlobFileFilter: Including file: " + relativePath);
                }
                return true;
            }
        }

        return false; // Not matched by any include pattern
    }

    /**
     * Gets the relative path of a file from the search root.
     *
     * @param file the file
     * @return relative path using forward slashes
     */
    private String getRelativePath(File file) {
        if (searchRoot == null) {
            return file.getName();
        }

        String filePath = file.getAbsolutePath();
        String rootPath = searchRoot.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            // Normalize to forward slashes for pattern matching
            return relative.replace(File.separatorChar, '/');
        }

        return file.getName();
    }

    /**
     * Converts a glob pattern to a regular expression pattern.
     *
     * Glob syntax supported:
     * - * matches any characters within a path segment
     * - ** matches any characters across path segments
     * - ? matches a single character
     * - [abc] matches character class (standard regex)
     *
     * @param glob the glob pattern
     * @return compiled regex pattern
     */
    private Pattern convertGlobToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");

        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);

            if (c == '*') {
                // Check for **
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    // ** matches anything including /
                    regex.append(".*");
                    i += 2;
                    // Skip following / if present
                    if (i < glob.length() && glob.charAt(i) == '/') {
                        i++;
                    }
                } else {
                    // Single * matches anything except /
                    regex.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                // ? matches any single character except /
                regex.append("[^/]");
                i++;
            } else if (c == '.') {
                // Escape literal dot
                regex.append("\\.");
                i++;
            } else if (c == '\\' && i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                // Windows path separator - treat as /
                regex.append('/');
                i += 2;
            } else if (c == '+' || c == '(' || c == ')' || c == '{' || c == '}' ||
                       c == '^' || c == '$' || c == '|') {
                // Escape regex special characters
                regex.append('\\').append(c);
                i++;
            } else {
                // Literal character
                regex.append(c);
                i++;
            }
        }

        regex.append('$');

        if (DEBUG) {
            System.out.println("GlobFileFilter: Converted '" + glob + "' to regex: " + regex);
        }

        return Pattern.compile(regex.toString());
    }
}
