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

import javax.swing.AbstractListModel;
import java.util.ArrayList;
import java.util.List;

/**
 * List model for search results that supports hierarchical display
 * of file groups and their matches. The model maintains a flat list
 * for JList rendering while supporting expand/collapse of file groups.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/06 $
 * @author DogsBay Ltd
 */
public class SearchResultsListModel extends AbstractListModel<Object> {
    private List<Object> items; // Mix of SearchResultGroup and Match
    private List<SearchResultGroup> groups; // All groups for iteration

    /**
     * Creates a new empty search results list model.
     */
    public SearchResultsListModel() {
        this.items = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    /**
     * Adds a file group to the results.
     * The group and its matches (if expanded) are added to the display list.
     *
     * @param group the search result group to add
     */
    public void addFileGroup(SearchResultGroup group) {
        if (group == null || group.getMatchCount() == 0) {
            return;
        }

        groups.add(group);

        int index = items.size();
        items.add(group);
        fireIntervalAdded(this, index, index);

        // If expanded, add all matches
        if (group.isExpanded()) {
            expandGroup(group);
        }
    }

    /**
     * Expands a group to show its matches.
     *
     * @param group the group to expand
     */
    public void expandGroup(SearchResultGroup group) {
        if (!group.isExpanded()) {
            group.setExpanded(true);
        }

        // Find the group's index
        int groupIndex = items.indexOf(group);
        if (groupIndex == -1) {
            return;
        }

        // Insert matches after the group
        List<Match> matches = group.getMatches();
        int insertIndex = groupIndex + 1;

        for (Match match : matches) {
            items.add(insertIndex, match);
            insertIndex++;
        }

        if (!matches.isEmpty()) {
            fireIntervalAdded(this, groupIndex + 1, groupIndex + matches.size());
        }
    }

    /**
     * Collapses a group to hide its matches.
     *
     * @param group the group to collapse
     */
    public void collapseGroup(SearchResultGroup group) {
        if (group.isExpanded()) {
            group.setExpanded(false);
        }

        // Find the group's index
        int groupIndex = items.indexOf(group);
        if (groupIndex == -1) {
            return;
        }

        // Remove all matches that belong to this group
        List<Match> toRemove = new ArrayList<>();
        int removeCount = 0;

        for (int i = groupIndex + 1; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof SearchResultGroup) {
                break; // Hit next group
            }
            if (item instanceof Match) {
                toRemove.add((Match) item);
                removeCount++;
            }
        }

        if (!toRemove.isEmpty()) {
            items.removeAll(toRemove);
            fireIntervalRemoved(this, groupIndex + 1, groupIndex + removeCount);
        }
    }

    /**
     * Toggles the expand/collapse state of a group.
     *
     * @param group the group to toggle
     */
    public void toggleGroup(SearchResultGroup group) {
        if (group.isExpanded()) {
            collapseGroup(group);
        } else {
            expandGroup(group);
        }
    }

    /**
     * Clears all results from the model.
     */
    public void clear() {
        int size = items.size();
        if (size > 0) {
            items.clear();
            groups.clear();
            fireIntervalRemoved(this, 0, size - 1);
        }
    }

    /**
     * Gets the total number of file groups.
     *
     * @return number of groups
     */
    public int getGroupCount() {
        return groups.size();
    }

    /**
     * Gets the total number of matches across all groups.
     *
     * @return total match count
     */
    public int getTotalMatchCount() {
        int total = 0;
        for (SearchResultGroup group : groups) {
            total += group.getMatchCount();
        }
        return total;
    }

    /**
     * Gets all groups (for iteration).
     *
     * @return list of all groups
     */
    public List<SearchResultGroup> getAllGroups() {
        return new ArrayList<>(groups);
    }

    @Override
    public int getSize() {
        return items.size();
    }

    @Override
    public Object getElementAt(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return null;
    }

    /**
     * Finds the group that contains the specified match.
     *
     * @param match the match to find
     * @return the containing group, or null if not found
     */
    public SearchResultGroup getGroupForMatch(Match match) {
        for (SearchResultGroup group : groups) {
            if (group.getMatches().contains(match)) {
                return group;
            }
        }
        return null;
    }
}
