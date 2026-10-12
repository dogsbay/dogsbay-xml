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

package com.dogsbay.dogsbayaieditor.dita;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Whether a build wrote its output. DITA-OT recovers from many content errors (an
 * undeclared element, a broken conref) and still writes the output, so a build the
 * editor reports as failed can have fresh output worth opening.
 */
public final class BuildOutput {

    /** File systems with coarse timestamps (FAT, some network shares) round to 2 seconds. */
    static final long CLOCK_SLACK_MILLIS = 2000;

    private BuildOutput() {}

    /** True when any file under {@code dir} (or {@code dir} itself, if a file) was modified at or after {@code since}. */
    public static boolean writtenSince(File dir, long since) {
        if (dir == null || !dir.exists()) {
            return false;
        }
        long threshold = since - CLOCK_SLACK_MILLIS;
        try (Stream<Path> files = Files.walk(dir.toPath())) {
            return files.filter(Files::isRegularFile).anyMatch(p -> p.toFile().lastModified() >= threshold);
        } catch (IOException | java.io.UncheckedIOException e) {
            return false;
        }
    }
}
