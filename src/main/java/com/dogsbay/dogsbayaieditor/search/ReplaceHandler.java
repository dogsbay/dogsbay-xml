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

import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.project.Match;
import com.dogsbay.xml.XMLUtilities;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for performing replace operations on search matches.
 * Supports single replace, replace all, and preserve case functionality.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class ReplaceHandler {
    private static final boolean DEBUG = false;

    /**
     * Replaces a single match in a file.
     *
     * @param match the match to replace
     * @param replacement the replacement text
     * @param preserveCase true to preserve the case of the original match
     * @return true if successful, false otherwise
     */
    public static boolean replaceSingle(Match match, String replacement, boolean preserveCase) {
        if (match == null || replacement == null) {
            return false;
        }

        try {
            File file = urlToFile(match.getURL());
            if (file == null || !file.exists()) {
                return false;
            }

            // Read file content
            FileText fileText = readFile(file);
            if (fileText == null) {
                return false;
            }
            String content = fileText.content;

            // Calculate absolute position of the match
            int absoluteStart = getAbsolutePosition(content, match.getLineNumber(), match.getStart());
            int absoluteEnd = getAbsolutePosition(content, match.getLineNumber(), match.getEnd());

            if (absoluteStart == -1 || absoluteEnd == -1) {
                if (DEBUG) {
                    System.err.println("ReplaceHandler: Could not find match position in file");
                }
                return false;
            }

            // Get the original matched text
            String originalText = content.substring(absoluteStart, absoluteEnd);

            // Apply preserve case if requested
            String actualReplacement = preserveCase ?
                applyPreserveCase(originalText, replacement) :
                replacement;

            // Perform replacement
            String newContent = content.substring(0, absoluteStart) +
                              actualReplacement +
                              content.substring(absoluteEnd);

            // Write back to file
            return writeFile(file, newContent, fileText.encoding);

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("ReplaceHandler: Error replacing single match: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Replaces all matches across all groups.
     *
     * @param groups list of search result groups
     * @param replacement the replacement text
     * @param preserveCase true to preserve the case of each original match
     * @return number of replacements made
     */
    public static int replaceAll(List<SearchResultGroup> groups, String replacement, boolean preserveCase) {
        if (groups == null || replacement == null) {
            return 0;
        }

        int totalReplaced = 0;

        for (SearchResultGroup group : groups) {
            try {
                File file = group.getFile();
                if (!file.exists()) {
                    continue;
                }

                // Read file content once
                FileText fileText = readFile(file);
                if (fileText == null) {
                    continue;
                }
                String content = fileText.content;

                // Sort matches in reverse order (bottom to top) to avoid position shifts
                List<Match> matches = new ArrayList<>(group.getMatches());
                Collections.sort(matches, new Comparator<Match>() {
                    public int compare(Match m1, Match m2) {
                        // Sort by line number descending, then by start position descending
                        int lineCompare = Integer.compare(m2.getLineNumber(), m1.getLineNumber());
                        if (lineCompare != 0) {
                            return lineCompare;
                        }
                        return Integer.compare(m2.getStart(), m1.getStart());
                    }
                });

                // Apply replacements from end to beginning
                for (Match match : matches) {
                    int absoluteStart = getAbsolutePosition(content, match.getLineNumber(), match.getStart());
                    int absoluteEnd = getAbsolutePosition(content, match.getLineNumber(), match.getEnd());

                    if (absoluteStart != -1 && absoluteEnd != -1) {
                        String originalText = content.substring(absoluteStart, absoluteEnd);
                        String actualReplacement = preserveCase ?
                            applyPreserveCase(originalText, replacement) :
                            replacement;

                        content = content.substring(0, absoluteStart) +
                                actualReplacement +
                                content.substring(absoluteEnd);

                        totalReplaced++;
                    }
                }

                // Write modified content back to file
                if (!writeFile(file, content, fileText.encoding)) {
                    if (DEBUG) {
                        System.err.println("ReplaceHandler: Failed to write file: " + file);
                    }
                }

            } catch (Exception e) {
                if (DEBUG) {
                    System.err.println("ReplaceHandler: Error in replaceAll for file: " +
                                     group.getFile() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        return totalReplaced;
    }

    /**
     * Applies case preservation to the replacement text based on the original text.
     *
     * Rules:
     * - ALL UPPERCASE → replacement.toUpperCase()
     * - all lowercase → replacement.toLowerCase()
     * - Title Case → capitalize first letter, lowercase rest
     * - MiXeD cAsE → use replacement as-is
     *
     * @param original the original matched text
     * @param replacement the replacement text
     * @return replacement with case applied
     */
    private static String applyPreserveCase(String original, String replacement) {
        if (original == null || original.isEmpty() || replacement == null || replacement.isEmpty()) {
            return replacement;
        }

        // Check if all uppercase
        if (original.equals(original.toUpperCase())) {
            return replacement.toUpperCase();
        }

        // Check if all lowercase
        if (original.equals(original.toLowerCase())) {
            return replacement.toLowerCase();
        }

        // Check if title case (first char upper, rest lower)
        if (Character.isUpperCase(original.charAt(0))) {
            boolean restLower = true;
            for (int i = 1; i < original.length(); i++) {
                if (Character.isLetter(original.charAt(i)) &&
                    !Character.isLowerCase(original.charAt(i))) {
                    restLower = false;
                    break;
                }
            }

            if (restLower) {
                // Title case: capitalize first letter, lowercase rest
                return Character.toUpperCase(replacement.charAt(0)) +
                       replacement.substring(1).toLowerCase();
            }
        }

        // Mixed case or other pattern - return replacement as-is
        return replacement;
    }

    /**
     * Converts line number and column position to absolute character position.
     *
     * @param content the file content
     * @param lineNumber 1-based line number
     * @param column 0-based column position within the line
     * @return absolute position, or -1 if not found
     */
    private static int getAbsolutePosition(String content, int lineNumber, int column) {
        if (content == null || lineNumber < 1) {
            return -1;
        }

        int currentLine = 1;
        int position = 0;

        while (position < content.length() && currentLine < lineNumber) {
            if (content.charAt(position) == '\n') {
                currentLine++;
            }
            position++;
        }

        // Add column offset
        position += column;

        return (position <= content.length()) ? position : -1;
    }

    /**
     * Reads a file as a string.
     *
     * @param file the file to read
     * @return file content, or null on error
     */
    /** File text plus the encoding it was read with, so writes round-trip faithfully. */
    static class FileText {
        final String content;
        final String encoding;

        FileText(String content, String encoding) {
            this.content = content;
            this.encoding = encoding;
        }
    }

    /**
     * Reads a file preserving its bytes faithfully: the encoding is detected the
     * same way the search side ({@code Finder} via {@code XMLUtilities.getText})
     * detects it, and line endings / trailing newlines are kept exactly as on disk
     * so untouched lines round-trip byte-identical.
     *
     * @param file the file to read
     * @return the content and detected encoding, or null on error
     */
    static FileText readFile(File file) {
        try {
            XMLUtilities.XMLDeclaration decl = new XMLUtilities.XMLDeclaration();
            String content = XMLUtilities.getText(file.toURI().toURL(), decl);
            return new FileText(content, decl.getEncoding());
        } catch (IOException e) {
            if (DEBUG) {
                System.err.println("ReplaceHandler: Error reading file: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Writes a string to a file in the given encoding, atomically (temp + rename
     * via {@link URLUtilities#save}) so a failed write never truncates the file.
     *
     * @param file the file to write
     * @param content the content to write
     * @param encoding the XML encoding name the file was read with
     * @return true if successful, false otherwise
     */
    static boolean writeFile(File file, String content, String encoding) {
        try {
            String javaEncoding = XMLUtilities.mapXMLEncodingToJava(encoding);
            InputStream input = new ByteArrayInputStream(content.getBytes(javaEncoding));

            URLUtilities.save(file.toURI().toURL(), input, javaEncoding);
            return true;
        } catch (IOException e) {
            if (DEBUG) {
                System.err.println("ReplaceHandler: Error writing file: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Converts a URL to a File.
     *
     * @param url the URL
     * @return File, or null if conversion fails
     */
    private static File urlToFile(URL url) {
        if (url == null) {
            return null;
        }

        try {
            return new File(url.toURI());
        } catch (Exception e) {
            // Fall back to path-based conversion
            try {
                return new File(url.getPath());
            } catch (Exception e2) {
                if (DEBUG) {
                    System.err.println("ReplaceHandler: Error converting URL to File: " + e2.getMessage());
                }
                return null;
            }
        }
    }
}
