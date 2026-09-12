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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Tests for the layout toggle feature: primary sidebar, secondary sidebar,
 * and bottom panel visibility properties and toggle button support.
 */
public class LayoutToggleTest {

    @Test
    @DisplayName("ConfigurationProperties has layout visibility methods")
    public void testLayoutVisibilityMethodsExist() throws Exception {
        // Verify the methods exist via reflection
        assertDoesNotThrow(() -> {
            ConfigurationProperties.class.getMethod("isPrimarySidebarVisible");
            ConfigurationProperties.class.getMethod("setPrimarySidebarVisible", boolean.class);
            ConfigurationProperties.class.getMethod("isSecondarySidebarVisible");
            ConfigurationProperties.class.getMethod("setSecondarySidebarVisible", boolean.class);
            ConfigurationProperties.class.getMethod("isBottomPanelVisible");
            ConfigurationProperties.class.getMethod("setBottomPanelVisible", boolean.class);
        }, "ConfigurationProperties should have layout visibility getter/setter methods");
    }

    @Test
    @DisplayName("Layout toggle icon resources exist")
    public void testLayoutIconsExist() {
        ClassLoader cl = getClass().getClassLoader();
        assertNotNull(cl.getResource("com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-left.png"),
            "layout-sidebar-left.png should exist");
        assertNotNull(cl.getResource("com/dogsbay/dogsbayaieditor/icons/sidebar/layout-panel.png"),
            "layout-panel.png should exist");
        assertNotNull(cl.getResource("com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-right.png"),
            "layout-sidebar-right.png should exist");
    }

    @Test
    @DisplayName("DogsBayAIEditor has layout toggle fields")
    public void testToggleFieldsExist() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> cls = Class.forName("com.dogsbay.dogsbayaieditor.DogsBayAIEditor");
            // Verify the toggle button fields exist
            cls.getDeclaredField("togglePrimarySidebar");
            cls.getDeclaredField("toggleBottomPanel");
            cls.getDeclaredField("toggleSecondarySidebar");
            cls.getDeclaredField("rightExplorerContainer");
            cls.getDeclaredField("mainContentSplit");
        }, "DogsBayAIEditor should have layout toggle fields");

        // Menu item fields moved to MenuBuilder
        assertDoesNotThrow(() -> {
            Class<?> mbCls = Class.forName("com.dogsbay.dogsbayaieditor.services.MenuBuilder");
            mbCls.getDeclaredField("togglePrimarySidebarMenuItem");
            mbCls.getDeclaredField("toggleBottomPanelMenuItem");
            mbCls.getDeclaredField("toggleSecondarySidebarMenuItem");
        }, "MenuBuilder should have layout toggle menu item fields");
    }

    @Test
    @DisplayName("DogsBayAIEditor has toggle methods")
    public void testToggleMethodsExist() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> cls = Class.forName("com.dogsbay.dogsbayaieditor.DogsBayAIEditor");
            cls.getDeclaredMethod("togglePrimarySidebar");
            cls.getDeclaredMethod("toggleSecondarySidebar");
            cls.getDeclaredMethod("toggleBottomPanel");
            cls.getDeclaredMethod("createLayoutToggleButton", javax.swing.ImageIcon.class, String.class);
        }, "DogsBayAIEditor should have toggle methods");
    }
}
