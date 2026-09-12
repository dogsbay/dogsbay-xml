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

package com.dogsbay.dogsbayaieditor.xpath;

import javax.swing.AbstractListModel;
import java.util.ArrayList;
import java.util.List;

/**
 * List model for XPath results that supports hierarchical display
 * of file groups and their result items with expand/collapse.
 */
public class XPathResultsListModel extends AbstractListModel<Object> {
    private List<Object> items;
    private List<XPathResultGroup> groups;

    public XPathResultsListModel() {
        this.items = new ArrayList<>();
        this.groups = new ArrayList<>();
    }

    public void addFileGroup(XPathResultGroup group) {
        if (group == null || group.getResultCount() == 0) {
            return;
        }

        groups.add(group);

        int index = items.size();
        items.add(group);
        fireIntervalAdded(this, index, index);

        if (group.isExpanded()) {
            expandGroup(group);
        }
    }

    public void expandGroup(XPathResultGroup group) {
        if (!group.isExpanded()) {
            group.setExpanded(true);
        }

        int groupIndex = items.indexOf(group);
        if (groupIndex == -1) {
            return;
        }

        List<XPathResultGroup.XPathResultItem> results = group.getResults();
        int insertIndex = groupIndex + 1;

        for (XPathResultGroup.XPathResultItem item : results) {
            items.add(insertIndex, item);
            insertIndex++;
        }

        if (!results.isEmpty()) {
            fireIntervalAdded(this, groupIndex + 1, groupIndex + results.size());
        }
    }

    public void collapseGroup(XPathResultGroup group) {
        if (group.isExpanded()) {
            group.setExpanded(false);
        }

        int groupIndex = items.indexOf(group);
        if (groupIndex == -1) {
            return;
        }

        List<Object> toRemove = new ArrayList<>();
        int removeCount = 0;

        for (int i = groupIndex + 1; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof XPathResultGroup) {
                break;
            }
            if (item instanceof XPathResultGroup.XPathResultItem) {
                toRemove.add(item);
                removeCount++;
            }
        }

        if (!toRemove.isEmpty()) {
            items.removeAll(toRemove);
            fireIntervalRemoved(this, groupIndex + 1, groupIndex + removeCount);
        }
    }

    public void toggleGroup(XPathResultGroup group) {
        if (group.isExpanded()) {
            collapseGroup(group);
        } else {
            expandGroup(group);
        }
    }

    public void clear() {
        int size = items.size();
        if (size > 0) {
            items.clear();
            groups.clear();
            fireIntervalRemoved(this, 0, size - 1);
        }
    }

    public int getGroupCount() {
        return groups.size();
    }

    public int getTotalResultCount() {
        int total = 0;
        for (XPathResultGroup group : groups) {
            total += group.getResultCount();
        }
        return total;
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
}
