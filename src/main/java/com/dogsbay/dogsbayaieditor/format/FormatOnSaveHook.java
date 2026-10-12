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

import java.net.URL;
import java.nio.file.Path;

import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;
import com.dogsbay.dogsbayaieditor.project.FormatStyleResolver;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.format.FormatEngine;
import com.dogsbay.xml.format.FormatStyle;

/**
 * The format-on-save policy ({@code plans/format-house-style.md}, Phase 4): when a
 * file's project mandates it (or the user enables it), canonicalize the bytes
 * written to disk with the resolved house style. Installed into
 * {@link DogsBayDocument} so every save path (GUI, agent, command) goes through it.
 *
 * <p>Safe by construction: it formats only the on-disk bytes (never the live
 * buffer), returns {@code null} when the file is already canonical (no churn) or
 * isn't well-formed XML, and never throws.
 */
public final class FormatOnSaveHook implements DogsBayDocument.SaveFormatter {

    /** User preference (Preferences ▸ Formatting); the project flag can also turn it on. */
    private static volatile boolean userEnabled = false;

    public static void setUserEnabled(boolean on) {
        userEnabled = on;
    }

    public static boolean isUserEnabled() {
        return userEnabled;
    }

    /** Install the singleton hook into the document layer. Call once at startup. */
    public static void install() {
        DogsBayDocument.setSaveFormatter(new FormatOnSaveHook());
    }

    @Override
    public String format(String text, URL url, String encoding) {
        Path file = fileOf(url);
        if (file == null) {
            return null;
        }
        // Resolve the project config once (style + on-save flag) rather than walking
        // up to .dogsbay twice.
        DogsbayProjectConfig cfg = loadConfig(file);
        boolean enabled = userEnabled || (cfg != null && cfg.isFormatOnSave());
        if (!enabled) {
            return null;
        }
        try {
            // Precedence: project <format-style> → user default → built-in default.
            FormatStyle style = (cfg != null && cfg.getFormatStyle() != null)
                    ? cfg.getFormatStyle() : FormatStyleResolver.userDefault();
            // Always emit LF here: DogsBayDocument.save() restores the document's own
            // line ending (CRLF/CR) AFTER this hook, so returning CRLF would double it.
            style = style.withNewline(FormatStyle.NewlineStyle.LF);
            String formatted = FormatEngine.format(text, url.toString(), encoding, style);
            // no-op when already canonical — keeps format-on-save from churning diffs
            return formatted.equals(text) ? null : formatted;
        } catch (Exception e) {
            return null;   // not well-formed / non-XML → write unchanged
        }
    }

    private static DogsbayProjectConfig loadConfig(Path file) {
        Path root = FormatStyleResolver.workspaceRootFor(file);
        if (root == null) {
            return null;
        }
        try {
            return DogsbayProjectConfig.load(root);
        } catch (Exception e) {
            return null;
        }
    }

    /** True when format-on-save is active for {@code url} (user preference or project flag). */
    public static boolean isEnabledFor(URL url) {
        Path file = (url == null) ? null : fileOf(url);
        DogsbayProjectConfig cfg = (file == null) ? null : loadConfig(file);
        return userEnabled || (cfg != null && cfg.isFormatOnSave());
    }

    private static Path fileOf(URL url) {
        try {
            return "file".equals(url.getProtocol()) ? Path.of(url.toURI()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
