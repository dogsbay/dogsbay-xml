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

package com.dogsbay.dogsbayaieditor.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class VersionTest {

    @Test
    @DisplayName("Parses simple version")
    public void testParse() {
        Version v = new Version("3.3.1");
        assertEquals("3.3.1", v.toString());
    }

    @Test
    @DisplayName("Strips v prefix")
    public void testVPrefix() {
        Version v = new Version("v3.3.2");
        assertEquals("3.3.2", v.toString());
    }

    @Test
    @DisplayName("Newer version detected")
    public void testNewerThan() {
        assertTrue(new Version("3.3.2").isNewerThan(new Version("3.3.1")));
        assertTrue(new Version("3.4.0").isNewerThan(new Version("3.3.9")));
        assertTrue(new Version("4.0.0").isNewerThan(new Version("3.99.99")));
    }

    @Test
    @DisplayName("Same version is not newer")
    public void testSameVersion() {
        assertFalse(new Version("3.3.1").isNewerThan(new Version("3.3.1")));
    }

    @Test
    @DisplayName("Older version is not newer")
    public void testOlderVersion() {
        assertFalse(new Version("3.3.0").isNewerThan(new Version("3.3.1")));
    }

    @Test
    @DisplayName("Handles v prefix in comparison")
    public void testVPrefixComparison() {
        assertTrue(new Version("v3.4.0").isNewerThan(new Version("3.3.1")));
    }

    @Test
    @DisplayName("Handles two-part version")
    public void testTwoPart() {
        Version v = new Version("3.4");
        assertEquals("3.4.0", v.toString());
        assertTrue(v.isNewerThan(new Version("3.3.1")));
    }
}
