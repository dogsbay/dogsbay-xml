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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The settings pages, in the order someone reaches for them.
 *
 * <p>{@code SettingsDialog} needs a full application frame, so this reads the
 * source: enough to catch the order being shuffled back, a page returning, or
 * the word Preferences coming back to a label.
 */
class SettingsPagesTest {

    private static String source() throws Exception {
        Path file = Path.of("src/main/java/com/dogsbay/dogsbayaieditor/PreferencesDialog.java");
        assertTrue(Files.isRegularFile(file), "cannot locate the settings dialog at " + file);
        return Files.readString(file);
    }

    @Test
    void serverAndPluginsComeFirst() throws Exception {
        String categories = categoriesLine(source());

        // What someone opens this dialog to change: the server, what is
        // enabled, and the keys.
        assertTrue(categories.indexOf("\"Server\"") < categories.indexOf("\"Plugins\""),
                "Server should come before Plugins: " + categories);
        assertTrue(categories.indexOf("\"Plugins\"") < categories.indexOf("\"Text\""),
                "Plugins should come before the editing pages: " + categories);
    }

    @Test
    void theKeyPageIsCalledBindings() throws Exception {
        String categories = categoriesLine(source());

        assertTrue(categories.contains("\"Bindings\""), "the key page is called Bindings: " + categories);
        assertFalse(categories.contains("\"Keys\""), "the old name is back: " + categories);
    }

    @Test
    void thereIsNoPrintPage() throws Exception {
        String source = source();

        // Printing still reads its settings; it just has no page of its own,
        // so nothing here may reference the controls that page owned.
        assertFalse(categoriesLine(source).contains("\"Print\""), "the Print page is back");
        assertFalse(source.contains("printFontSelectionBox"), "a print control outlived its page");
        assertFalse(source.contains("createPrintTab"), "the print page builder is back");
    }

    @Test
    void theDialogSaysSettings() throws Exception {
        String source = source();

        assertTrue(source.contains("setTitle( \"Settings\")"), "the dialog is titled Settings");
    }


    @Test
    void nothingCommentedOutIsWaitingToComeBack() throws Exception {
        String source = source();

        // The Security page was commented out rather than removed, and the heap
        // controls with it: seventy and thirty lines of code that looked like
        // work in progress and was not.
        assertFalse(source.contains("createSecurityTab"), "the Security page is back");
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("keystore"),
                "a Security control is back");
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("heap"),
                "a heap control is back");
    }

    @Test
    void heapSizeIsNotASetting() throws Exception {
        // A packaged build takes its JVM options from the app image, so a heap
        // size in the settings could not change anything and never did.
        String store = Files.readString(Path.of(
                "src/main/java/com/dogsbay/dogsbayaieditor/properties/ConfigurationProperties.java"));

        assertFalse(store.contains("HeapSize"), "the heap accessors are back");
        assertFalse(store.contains("maximum-heap-size"), "the heap settings are back");
    }

    private static String categoriesLine(String source) {
        int start = source.indexOf("String[] categories");
        return source.substring(start, source.indexOf(';', start));
    }
}
