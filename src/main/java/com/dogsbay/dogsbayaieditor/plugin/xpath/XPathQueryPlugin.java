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

package com.dogsbay.dogsbayaieditor.plugin.xpath;

import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.Plugin;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.xpath.XPathQueryPanel;

/**
 * Built-in plugin that adds the XPath Query panel to the left sidebar.
 * Allows running XPath expressions across project files.
 */
public class XPathQueryPlugin implements Plugin {

    private static final Logger LOG = LoggerFactory.getLogger(XPathQueryPlugin.class);
    private static final String SIDEBAR_ID = "xpathQuery";
    private static final String ICON_PATH = "com/dogsbay/dogsbayaieditor/icons/sidebar/search-sparkle.png";

    private PluginContext context;
    private XPathQueryPanel panel;

    @Override
    public String getId() {
        return "com.dogsbay.xpath-query";
    }

    @Override
    public String getName() {
        return "XPath Query";
    }

    @Override
    public String getDescription() {
        return "Run XPath expressions across project files";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public void activate(PluginContext ctx) {
        this.context = ctx;

        LOG.info("Activating XPath Query plugin");

        try {
            DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
            panel = new XPathQueryPanel(editor);

            javax.swing.Icon icon = ctx.getUIService().loadSidebarIcon(ICON_PATH);
            ctx.getUIService().addSidebarPanel(SIDEBAR_ID, icon, "XPath Query",
                    panel, UIService.SidebarPosition.LEFT);

            LOG.info("XPath Query plugin activated");
        } catch (Exception e) {
            LOG.error("Failed to activate XPath Query plugin", e);
        }
    }

    @Override
    public void deactivate() {
        LOG.info("Deactivating XPath Query plugin");

        try {
            if (context != null) {
                context.getUIService().removeSidebarPanel(SIDEBAR_ID,
                        UIService.SidebarPosition.LEFT);
            }
        } catch (Exception e) {
            LOG.error("Error during XPath Query plugin deactivation", e);
        }
    }
}
