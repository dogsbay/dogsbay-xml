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

package com.dogsbay.dogsbayaieditor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * What the reader changed has to survive a restart, and only what they
 * changed. The old store wrote every binding and appended rather than
 * replaced, which is how one file came to hold 854 entries for 206 commands.
 */
class KeyBindingStorageTest {

    @TempDir Path dir;

    private ConfigurationProperties settings() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        return new ConfigurationProperties(
                new DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
    }

    @Test
    void aChoiceSurvivesTheRoundTrip() throws Exception {
        ConfigurationProperties settings = settings();

        settings.setKeyBindingOverrides(Map.of("File:  Open", "control shift O"));

        assertThat(settings.getKeyBindingOverrides())
            .containsExactly(Map.entry("File:  Open", "control shift O"));
    }

    @Test
    void nothingChangedMeansNothingStored() throws Exception {
        ConfigurationProperties settings = settings();

        settings.setKeyBindingOverrides(Map.of());

        assertThat(settings.getKeyBindingOverrides()).isEmpty();
    }

    @Test
    void savingReplacesRatherThanAppends() throws Exception {
        ConfigurationProperties settings = settings();

        settings.setKeyBindingOverrides(Map.of("File:  Open", "control O"));
        settings.setKeyBindingOverrides(Map.of("File:  Open", "control shift O"));
        settings.setKeyBindingOverrides(Map.of("File:  Open", "alt O"));

        // One entry, not three: appending is the bug that grew a 159 KB file.
        assertThat(settings.getKeyBindingOverrides()).hasSize(1);
        assertThat(settings.getKeyBindingOverrides()).containsEntry("File:  Open", "alt O");
    }

    @Test
    void aCommandBoundToNothingIsRememberedAsSuch() throws Exception {
        ConfigurationProperties settings = settings();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("File:  Save", "");

        settings.setKeyBindingOverrides(overrides);

        // Empty means "deliberately no key", which is different from having no
        // entry and following the default.
        assertThat(settings.getKeyBindingOverrides()).containsEntry("File:  Save", "");
    }

    @Test
    void theStoredChoicesReachTheBindings() throws Exception {
        ConfigurationProperties settings = settings();
        settings.setKeyBindingOverrides(Map.of("File:  Open", "control shift O"));

        KeyBindings keys = settings.getKeyBindings();

        assertThat(keys.isOverridden("File:  Open")).isTrue();
        assertThat(keys.strokeFor("File:  Open"))
            .contains(javax.swing.KeyStroke.getKeyStroke("control shift O"));
        // …and the same instance answers everywhere.
        assertThat(settings.getKeyBindings()).isSameAs(keys);
    }
}
