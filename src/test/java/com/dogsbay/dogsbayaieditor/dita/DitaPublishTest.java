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

package com.dogsbay.dogsbayaieditor.dita;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DITA publish feature classes.
 */
public class DitaPublishTest {

    @Test
    @DisplayName("DitaPublishDialog class can be loaded")
    public void testDitaPublishDialogClassLoads() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.dogsbayaieditor.dita.ui.DitaPublishDialog");
        }, "DitaPublishDialog should be loadable");
    }

    @Test
    @DisplayName("DitaOtTransformer class can be loaded")
    public void testDitaOtTransformerClassLoads() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.dita.DitaOtTransformer");
        }, "DitaOtTransformer should be loadable");
    }

    @Test
    @DisplayName("ConfigurationProperties has DITA publish methods")
    public void testConfigurationPropertiesHasPublishMethods() throws Exception {
        Class<?> clazz = Class.forName("com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties");
        assertNotNull(clazz.getMethod("getDitaPublishTranstype"), "getDitaPublishTranstype should exist");
        assertNotNull(clazz.getMethod("setDitaPublishTranstype", String.class), "setDitaPublishTranstype should exist");
        assertNotNull(clazz.getMethod("getDitaPublishOutputDir"), "getDitaPublishOutputDir should exist");
        assertNotNull(clazz.getMethod("setDitaPublishOutputDir", String.class), "setDitaPublishOutputDir should exist");
    }
}
