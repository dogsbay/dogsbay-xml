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

package com.dogsbay.dogsbayaieditor.format;

import java.nio.file.Files;
import java.nio.file.Path;

import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;
import com.dogsbay.xml.format.FormatStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FormatOnSaveHookTest {

    private final FormatOnSaveHook hook = new FormatOnSaveHook();

    @AfterEach
    void resetUserPref() {
        FormatOnSaveHook.setUserEnabled(false);
    }

    private static java.net.URL url(Path file) throws Exception {
        return file.toUri().toURL();
    }

    @Test
    @DisplayName("isEnabledFor reflects the user preference when no project flag")
    void isEnabledForTracksUserPref() {
        assertThat(FormatOnSaveHook.isEnabledFor(null)).isFalse();
        FormatOnSaveHook.setUserEnabled(true);
        assertThat(FormatOnSaveHook.isEnabledFor(null)).isTrue();
        FormatOnSaveHook.setUserEnabled(false);
        assertThat(FormatOnSaveHook.isEnabledFor(null)).isFalse();
    }

    @Test
    @DisplayName("disabled (no project, no user pref) → returns null (write unchanged)")
    void disabledByDefault(@TempDir Path ws) throws Exception {
        Path file = ws.resolve("a.xml");
        Files.writeString(file, "<root><a>x</a></root>");
        assertThat(hook.format("<root><a>x</a></root>", url(file), "UTF-8")).isNull();
    }

    @Test
    @DisplayName("project mandates format-on-save → unformatted input is canonicalized")
    void formatsWhenProjectEnables(@TempDir Path ws) throws Exception {
        DogsbayProjectConfig cfg = new DogsbayProjectConfig();
        cfg.setFormatStyle(FormatStyle.defaults());
        cfg.setFormatOnSave(true);
        cfg.saveShared(ws);

        Path file = ws.resolve("a.xml");
        Files.writeString(file, "<root><a>x</a></root>");
        String out = hook.format("<root><a>x</a></root>", url(file), "UTF-8");
        assertThat(out).isNotNull();
        assertThat(out).contains("\n  <a>x</a>");   // indented
    }

    @Test
    @DisplayName("already-canonical input → returns null (no churn)")
    void noOpWhenCanonical(@TempDir Path ws) throws Exception {
        DogsbayProjectConfig cfg = new DogsbayProjectConfig();
        cfg.setFormatStyle(FormatStyle.defaults());
        cfg.setFormatOnSave(true);
        cfg.saveShared(ws);

        Path file = ws.resolve("a.xml");
        // the canonical form is whatever the engine produces — feeding it back is a no-op
        String canonical = com.dogsbay.xml.format.FormatEngine.format(
                "<root><a>x</a></root>", url(file).toString(), "UTF-8", FormatStyle.defaults());
        Files.writeString(file, canonical);
        assertThat(hook.format(canonical, url(file), "UTF-8")).isNull();
    }

    @Test
    @DisplayName("hook returns LF even when the project style is CRLF (save() restores endings)")
    void hookAlwaysReturnsLf(@TempDir Path ws) throws Exception {
        DogsbayProjectConfig cfg = new DogsbayProjectConfig();
        cfg.setFormatStyle(FormatStyle.defaults().withNewline(FormatStyle.NewlineStyle.CRLF));
        cfg.setFormatOnSave(true);
        cfg.saveShared(ws);

        Path file = ws.resolve("a.xml");
        Files.writeString(file, "<root><a>x</a></root>");
        String out = hook.format("<root><a>x</a></root>", url(file), "UTF-8");
        // If the hook returned CRLF, save()'s own CRLF restoration would double it.
        assertThat(out).isNotNull();
        assertThat(out).doesNotContain("\r");
    }

    @Test
    @DisplayName("non-XML content never throws and is left unchanged")
    void nonXmlUnchanged(@TempDir Path ws) throws Exception {
        FormatOnSaveHook.setUserEnabled(true);
        Path file = ws.resolve("notes.txt");
        Files.writeString(file, "just some text, not xml");
        assertThat(hook.format("just some text, not xml", url(file), "UTF-8")).isNull();
    }
}
