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

package com.dogsbay.dogsbayaieditor.plugin.bookmarks;

import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.BookmarkExplorerPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;

/**
 * Built-in plugin that adds the Bookmarks explorer to the left sidebar.
 */
public class BookmarksPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(BookmarksPlugin.class);
    private static final String SIDEBAR_ID = "bookmarks";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/bookmark.png";

    private PluginContext context;
    private BookmarkExplorerPanel bookmarkExplorer;

    @Override
    public String getId() {
        return "com.dogsbay.bookmarks";
    }

    @Override
    public String getName() {
        return "Bookmarks";
    }

    @Override
    public String getDescription() {
        return "Navigate and manage document bookmarks";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating Bookmarks plugin");

        try {
            DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
            bookmarkExplorer = new BookmarkExplorerPanel(editor, editor.getProperties());

            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "Bookmarks",
                    bookmarkExplorer, UIService.SidebarPosition.LEFT);

            LOG.info("Bookmarks plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate Bookmarks plugin", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating Bookmarks plugin");

        try {
            if (context != null) {
                context.getUIService().removeSidebarPanel(SIDEBAR_ID,
                        UIService.SidebarPosition.LEFT);
            }
        } catch (Exception e) {
            LOG.error("Error during Bookmarks plugin deactivation", e);
        }
    }

    public BookmarkExplorerPanel getBookmarkExplorer() {
        return bookmarkExplorer;
    }
}
