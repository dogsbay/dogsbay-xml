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

package com.dogsbay.dogsbayaieditor.plugin.yaml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.Constants;

class YamlPluginTest {

    @Test
    void testPluginMetadata() {
        YamlPlugin plugin = new YamlPlugin();
        assertEquals("com.dogsbay.yaml", plugin.getId());
        assertEquals("YAML Support", plugin.getName());
        assertTrue(plugin.isBuiltIn());
    }

    @Test
    void testDocumentFormat() {
        YamlDocumentFormat format = new YamlDocumentFormat();
        assertEquals("YAML", format.getName());
        assertTrue(format.matches("config.yml"));
        assertTrue(format.matches("docker-compose.yaml"));
        assertFalse(format.matches("test.json"));
    }

    @Test
    void testScannerTokenizesKeyValue() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "name: hello", null);
        } catch (Exception e) { fail(e); }

        YamlScanner scanner = new YamlScanner(doc);
        scanner.setRange(0, doc.getLength());

        assertEquals(Constants.YAML_KEY, scanner.token);
        scanner.scan();
        assertEquals(Constants.YAML_PUNCTUATION, scanner.token); // :
        scanner.scan();
        assertEquals(Constants.YAML_STRING, scanner.token); // hello
    }

    @Test
    void testScannerTokenizesComment() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "# this is a comment", null);
        } catch (Exception e) { fail(e); }

        YamlScanner scanner = new YamlScanner(doc);
        scanner.setRange(0, doc.getLength());

        assertEquals(Constants.YAML_COMMENT, scanner.token);
    }

    @Test
    void testScannerTokenizesBoolean() {
        javax.swing.text.PlainDocument doc = new javax.swing.text.PlainDocument();
        try {
            doc.insertString(0, "enabled: true", null);
        } catch (Exception e) { fail(e); }

        YamlScanner scanner = new YamlScanner(doc);
        scanner.setRange(0, doc.getLength());

        assertEquals(Constants.YAML_KEY, scanner.token);
        scanner.scan();
        assertEquals(Constants.YAML_PUNCTUATION, scanner.token);
        scanner.scan();
        assertEquals(Constants.YAML_KEYWORD, scanner.token);
    }
}
