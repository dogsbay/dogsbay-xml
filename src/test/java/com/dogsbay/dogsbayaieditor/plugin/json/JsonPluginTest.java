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

package com.dogsbay.dogsbayaieditor.plugin.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.Constants;

class JsonPluginTest {

    @Test
    void testPluginMetadata() {
        JsonPlugin plugin = new JsonPlugin();
        assertEquals("com.dogsbay.json", plugin.getId());
        assertEquals("JSON Support", plugin.getName());
        assertTrue(plugin.isBuiltIn());
    }

    @Test
    void testDocumentFormat() {
        JsonDocumentFormat format = new JsonDocumentFormat();
        assertEquals("JSON", format.getName());
        assertTrue(format.matches("test.json"));
        assertTrue(format.matches("config.jsonc"));
        assertTrue(format.matches("data.geojson"));
        assertFalse(format.matches("test.xml"));
        assertFalse(format.matches("test.txt"));
    }

    @Test
    void testScannerTokenizesKeys() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "{\"name\": \"value\"}", null);
        } catch (Exception e) {
            fail(e);
        }

        JsonScanner scanner = new JsonScanner(doc);
        scanner.setRange(0, doc.getLength());

        // {
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // "name" — should be key since followed by :
        assertEquals(Constants.JSON_KEY, scanner.token);
        scanner.scan();
        // :
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // whitespace
        assertEquals(Constants.JSON_TEXT, scanner.token);
        scanner.scan();
        // "value" — string, not key
        assertEquals(Constants.JSON_STRING, scanner.token);
        scanner.scan();
        // }
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
    }

    @Test
    void testScannerTokenizesKeywordsAndNumbers() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "[true, false, null, 42]", null);
        } catch (Exception e) {
            fail(e);
        }

        JsonScanner scanner = new JsonScanner(doc);
        scanner.setRange(0, doc.getLength());

        // [
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // true
        assertEquals(Constants.JSON_KEYWORD, scanner.token);
        scanner.scan();
        // ,
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // space
        assertEquals(Constants.JSON_TEXT, scanner.token);
        scanner.scan();
        // false
        assertEquals(Constants.JSON_KEYWORD, scanner.token);
        scanner.scan();
        // ,
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // space
        assertEquals(Constants.JSON_TEXT, scanner.token);
        scanner.scan();
        // null
        assertEquals(Constants.JSON_KEYWORD, scanner.token);
        scanner.scan();
        // ,
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
        scanner.scan();
        // space
        assertEquals(Constants.JSON_TEXT, scanner.token);
        scanner.scan();
        // 42
        assertEquals(Constants.JSON_NUMBER, scanner.token);
        scanner.scan();
        // ]
        assertEquals(Constants.JSON_PUNCTUATION, scanner.token);
    }

    @Test
    void testScannerTokenizesComments() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "// line comment\n{}", null);
        } catch (Exception e) {
            fail(e);
        }

        JsonScanner scanner = new JsonScanner(doc);
        scanner.setRange(0, doc.getLength());

        assertEquals(Constants.JSON_COMMENT, scanner.token);
    }
}
