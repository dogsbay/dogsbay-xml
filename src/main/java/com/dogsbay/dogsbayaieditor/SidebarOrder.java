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

package com.dogsbay.dogsbayaieditor;

import java.util.List;

/**
 * The order the sidebar's buttons appear in.
 *
 * <p>Stated here rather than left to the order plugins happen to start in. A
 * rail whose arrangement depends on activation order is a rail that rearranges
 * itself when a plugin is disabled, and the top of it is the most valuable
 * space the window has.
 *
 * <p>The agent and its proposals come first: they are where the work starts,
 * and what a reader reaches for most.
 */
public final class SidebarOrder {

    /** Ids in the order they should appear; anything else follows, as it arrives. */
    private static final List<String> ORDER = List.of(
            "agent",
            "proposals",
            "fileExplorer",
            "search",
            "git",
            "dita",
            "outline",
            "xpathQuery",
            "whereUsed",
            "bookmarks",
            "properties",
            "navigator",
            "helper",
            "metadata");

    private SidebarOrder() {
    }

    /**
     * Where this panel sits, lower first.
     *
     * @return the rank, or a rank past every named one for a panel this list
     *         does not know — a plugin someone else wrote, which should appear
     *         rather than be refused a place
     */
    public static int rankOf(String id) {
        if (id == null) {
            return ORDER.size();
        }
        int index = ORDER.indexOf(id);
        return index < 0 ? ORDER.size() : index;
    }
}
