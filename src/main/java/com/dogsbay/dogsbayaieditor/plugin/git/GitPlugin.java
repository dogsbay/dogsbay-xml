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

package com.dogsbay.dogsbayaieditor.plugin.git;

import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;

/**
 * Built-in plugin that adds Git source control to the left sidebar.
 * Provides repository status, commit, branch switching, and diff viewing.
 */
public class GitPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(GitPlugin.class);
    private static final String SIDEBAR_ID = "git";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/source-control.png";

    private PluginContext context;
    private GitPanel gitPanel;

    @Override
    public String getId() {
        return "com.dogsbay.git";
    }

    @Override
    public String getName() {
        return "Git Integration";
    }

    @Override
    public String getDescription() {
        return "Git status, commit, branch switching, and diff viewer";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating Git plugin");

        try {
            DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
            gitPanel = new GitPanel(editor);

            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "Git",
                    gitPanel, UIService.SidebarPosition.LEFT);

            gitPanel.refreshIconBadge();

            LOG.info("Git plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate Git plugin", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating Git plugin");

        try {
            if (context != null) {
                context.getUIService().removeSidebarPanel(SIDEBAR_ID,
                        UIService.SidebarPosition.LEFT);
            }
        } catch (Exception e) {
            LOG.error("Error during Git plugin deactivation", e);
        }
    }

    /**
     * Returns the Git panel (for use by other components during transition).
     */
    public GitPanel getGitPanel() {
        return gitPanel;
    }
}
