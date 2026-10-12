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

import com.dogsbay.dogsbayaieditor.project.Match;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of search matches within a single file.
 * Used to organize search results by file in the search panel.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class SearchResultGroup {
    private File file;
    private List<Match> matches;
    private boolean expanded;

    /**
     * Creates a new search result group for the specified file.
     *
     * @param file the file containing the matches
     */
    public SearchResultGroup(File file) {
        this.file = file;
        this.matches = new ArrayList<>();
        this.expanded = true; // Start expanded by default
    }

    /**
     * Gets the file containing the matches.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Gets the list of matches in this file.
     *
     * @return list of matches
     */
    public List<Match> getMatches() {
        return matches;
    }

    /**
     * Adds a match to this group.
     *
     * @param match the match to add
     */
    public void addMatch(Match match) {
        matches.add(match);
    }

    /**
     * Gets the number of matches in this file.
     *
     * @return match count
     */
    public int getMatchCount() {
        return matches.size();
    }

    /**
     * Gets the relative path of this file from the specified root directory.
     *
     * @param root the root directory
     * @return relative path, or absolute path if not relative to root
     */
    public String getRelativePath(File root) {
        if (root == null) {
            return file.getAbsolutePath();
        }

        String filePath = file.getAbsolutePath();
        String rootPath = root.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative;
        }

        return filePath;
    }

    /**
     * Checks if this group is expanded in the UI.
     *
     * @return true if expanded
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Sets the expanded state of this group.
     *
     * @param expanded true to expand, false to collapse
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
