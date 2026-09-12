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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.dogsbay.xml.XElement;

/**
 * Where the settings live, and which version of the format they are in.
 *
 * <p>The version used to be part of every file name — {@code .dogsbay-v33.xml}
 * for the main document, and the same suffix on each section's sidecar. A
 * release that bumped it started from nothing, because a file named for the
 * previous version is simply a file that isn't there. The main document had a
 * hand-written chain back through v32, v31, v30 and v20 to soften that; the
 * sidecars had none, so every bump silently reset the reader's key bindings
 * and text preferences.
 *
 * <p>The name is now the settings' own — {@code settings.xml},
 * {@code key-mappings.xml} — and the version is an attribute inside it. A file
 * outlives the release that wrote it, and only a change in the format's shape
 * needs anything to happen.
 *
 * <p>Note that {@link #CURRENT_VERSION} is the version of the settings format,
 * not of the product. They move at different speeds, and binding them together
 * is what produced five file names for one file.
 */
public final class SettingsFile {

    /** The settings format this build writes. */
    public static final int CURRENT_VERSION = 1;

    /** The attribute the version lives in, on the file's root element. */
    public static final String VERSION_ATTRIBUTE = "settings-version";

    /**
     * The file the settings live in.
     *
     * <p>One name, which does not change with the release. 4.0 reads no 3.x
     * file: the shape of the settings changed enough that carrying one across
     * would be guesswork, and there is no installed base to carry.
     *
     * @param dir      the settings directory, normally {@code ~/.dogsbay}
     * @param baseName {@link #MAIN}, or a section that keeps its own file
     * @return the path to use, which may not exist yet on a first run
     */
    public static Path locate(Path dir, String baseName) {
        return dir.resolve(baseName + ".xml");
    }

    /** The base name of the main settings document. */
    public static final String MAIN = "settings";

    /**
     * The version a settings file declares, or {@code 0} when it declares none
     * — which is what every file written before this change looks like.
     */
    public static int versionOf(String declared) {
        if (declared == null || declared.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(declared.strip());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /**
     * Whether a file at {@code version} needs migrating before this build uses
     * it.
     *
     * <p>A file from the future is left alone: an older build must not rewrite
     * what a newer one wrote, and refusing to touch it is the only way to be
     * sure it doesn't.
     */
    public static boolean needsMigration(int version) {
        return version < CURRENT_VERSION;
    }

    /**
     * A step that raises a settings file from the version before it to
     * {@link #to()}.
     */
    public interface Migration {
        /** The version this step produces. */
        int to();

        /** Change the file's shape in place. */
        void apply(XElement root);
    }

    /**
     * The steps, in order. Empty while there has only ever been one version of
     * the format; the machinery exists so that the first change to it is a step
     * in this list rather than a sixth file name.
     */
    static final List<Migration> MIGRATIONS = List.of();

    /**
     * Bring a freshly loaded settings file up to date and stamp its version.
     *
     * <p>A file with no version is one written before the version moved
     * inside. It carries no stamp, so every migration step applies to it.
     *
     * @param file the file the root came from, for the backup
     * @param root the root element, changed in place
     * @return false when the file was written by a newer build, in which case
     *         nothing was changed and the caller must not write it back
     */
    public static boolean adopt(Path file, XElement root) {
        return adopt(file, root, MIGRATIONS);
    }

    /** As {@link #adopt(Path, XElement)}, with the steps supplied — for tests. */
    static boolean adopt(Path file, XElement root, List<Migration> steps) {
        if (root == null) {
            return true;
        }
        int version = versionOf(root.getAttribute(VERSION_ATTRIBUTE));
        if (version > CURRENT_VERSION) {
            // Written by a newer build. Reading it is fine; writing it back is
            // not — this build's model does not know the newer settings, so a
            // save would drop them and leave the file still claiming the newer
            // version. Saying so is the caller's job.
            return false;
        }
        if (needsMigration(version)) {
            // Version 0 included: it has no stamp, so every step applies to it,
            // and it is the one file shape that exists on every machine today.
            // Excluding it from the backup left exactly those files unprotected.
            try {
                backUp(file, version);
            } catch (IOException e) {
                // A backup we could not take is not a reason to refuse to
                // migrate; the migration is what keeps the settings usable.
            }
        }
        for (Migration step : steps) {
            if (step.to() > version && step.to() <= CURRENT_VERSION) {
                step.apply(root);
                version = step.to();
            }
        }
        root.addAttribute(VERSION_ATTRIBUTE, String.valueOf(CURRENT_VERSION));
        return true;
    }

    /**
     * Copy a settings file aside before a migration rewrites it.
     *
     * <p>Named for the version being left behind, so a reader who has to go
     * back knows which file is which.
     *
     * @return the backup, or null when there was nothing to copy
     */
    public static Path backUp(Path file, int fromVersion) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".v" + fromVersion + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }
}
