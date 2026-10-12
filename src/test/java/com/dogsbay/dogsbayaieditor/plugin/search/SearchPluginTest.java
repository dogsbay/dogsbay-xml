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

package com.dogsbay.dogsbayaieditor.plugin.search;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SearchPluginTest {
    @Test void testMetadata() {
        SearchPlugin p = new SearchPlugin();
        assertEquals("com.dogsbay.search", p.getId());
        assertEquals("Search in Files", p.getName());
        assertTrue(p.isBuiltIn());
    }
    @Test void testDeactivateSafe() { assertDoesNotThrow(new SearchPlugin()::deactivate); }
}
