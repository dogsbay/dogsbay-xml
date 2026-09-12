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

import java.lang.reflect.Field;

/**
 * Tests that the PreferencesDialog uses the split-pane layout
 * (category list + card layout) rather than the old JTabbedPane.
 */
public class PreferencesDialogLayoutTest {

    @Test
    @DisplayName("PreferencesDialog has categoryList field (split-pane layout)")
    public void testHasCategoryListField() throws Exception {
        Field field = PreferencesDialog.class.getDeclaredField("categoryList");
        assertNotNull(field, "categoryList field should exist");
        assertEquals(javax.swing.JList.class, field.getType(),
            "categoryList should be a JList");
    }

    @Test
    @DisplayName("PreferencesDialog has cardLayout field")
    public void testHasCardLayoutField() throws Exception {
        Field field = PreferencesDialog.class.getDeclaredField("cardLayout");
        assertNotNull(field, "cardLayout field should exist");
        assertEquals(java.awt.CardLayout.class, field.getType(),
            "cardLayout should be a CardLayout");
    }

    @Test
    @DisplayName("PreferencesDialog has cardPanel field")
    public void testHasCardPanelField() throws Exception {
        Field field = PreferencesDialog.class.getDeclaredField("cardPanel");
        assertNotNull(field, "cardPanel field should exist");
        assertEquals(javax.swing.JPanel.class, field.getType(),
            "cardPanel should be a JPanel");
    }

    @Test
    @DisplayName("PreferencesDialog no longer has xmlEditor field")
    public void testNoXmlEditorField() {
        assertThrows(NoSuchFieldException.class, () -> {
            PreferencesDialog.class.getDeclaredField("xmlEditor");
        }, "xmlEditor field should have been removed");
    }
}
