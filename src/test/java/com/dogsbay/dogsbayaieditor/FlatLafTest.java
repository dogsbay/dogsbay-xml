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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlatLaf integration.
 */
class FlatLafTest {

    @Test
    void flatLightLafClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.formdev.flatlaf.FlatLightLaf"));
    }

    @Test
    void flatDarkLafClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.formdev.flatlaf.FlatDarkLaf"));
    }

    @Test
    void flatIntelliJLafClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.formdev.flatlaf.FlatIntelliJLaf"));
    }

    @Test
    void flatDarculaLafClassLoads() {
        assertDoesNotThrow(() -> Class.forName("com.formdev.flatlaf.FlatDarculaLaf"));
    }

    @Test
    void flatLafIsValidLookAndFeel() throws Exception {
        Object laf = Class.forName("com.formdev.flatlaf.FlatLightLaf")
                .getDeclaredConstructor().newInstance();
        assertTrue(laf instanceof javax.swing.LookAndFeel);
        assertTrue(((javax.swing.LookAndFeel) laf).isSupportedLookAndFeel());
    }
}
