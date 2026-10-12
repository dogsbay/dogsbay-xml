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

import java.nio.file.Path;

/**
 * Where the editor keeps a reader's things, by what they are.
 *
 * <p>Settings are the reader's own and are worth backing up. A cache is ours,
 * is rebuilt on demand, and should be deletable without anyone thinking twice.
 * Keeping them in one folder made a 56 KB registry snapshot look like part of
 * someone's configuration.
 */
public final class UserDirectories {

    private UserDirectories() {
    }

    /** Settings: {@code ~/.dogsbay}. */
    public static Path settings() {
        return home().resolve(".dogsbay");
    }

    /**
     * Caches, which the platform expects somewhere it can clear:
     * {@code $XDG_CACHE_HOME/dogsbay} or {@code ~/.cache/dogsbay} on Linux,
     * {@code ~/Library/Caches/dogsbay} on macOS,
     * {@code %LOCALAPPDATA%\dogsbay\cache} on Windows.
     */
    public static Path cache() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            return home().resolve("Library").resolve("Caches").resolve("dogsbay");
        }
        if (os.contains("windows")) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local == null || local.isBlank()
                    ? home().resolve("AppData").resolve("Local") : Path.of(local);
            return base.resolve("dogsbay").resolve("cache");
        }
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg == null || xdg.isBlank() ? home().resolve(".cache") : Path.of(xdg);
        return base.resolve("dogsbay");
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home", "."));
    }
}
