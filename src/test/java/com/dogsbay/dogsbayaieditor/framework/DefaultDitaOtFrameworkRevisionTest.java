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

package com.dogsbay.dogsbayaieditor.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** When an installed default framework is replaced by the bundled one. */
class DefaultDitaOtFrameworkRevisionTest {

    @TempDir Path dir;

    @Test
    void extractsWhenTheHomeIsMissing() throws Exception {
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), "2")).isTrue();
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), null)).isTrue();
    }

    @Test
    void replacesAHomeFromAnOlderOrUnrecordedRevision() throws Exception {
        Files.createDirectories(dir.resolve("dita-ot"));
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), "2")).as("installed before revisions").isTrue();
        Files.writeString(dir.resolve(DefaultDitaOtFramework.REVISION_FILE), "1\n");
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), "2")).isTrue();
        Files.writeString(dir.resolve(DefaultDitaOtFramework.REVISION_FILE), "2\n");
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), "2")).isFalse();
    }

    @Test
    void keepsAnInstalledHomeWhenTheBuildCarriesNoRevision() throws Exception {
        Files.createDirectories(dir.resolve("dita-ot"));
        assertThat(DefaultDitaOtFramework.needsExtract(dir.toFile(), null)).isFalse();
    }

    /** Tracks ditaOtBundleRevision in build.gradle.kts: raise both when the bundle changes. */
    @Test
    void theBundledFrameworkHasARevision() throws Exception {
        if (DefaultDitaOtFramework.isBundled()) {
            assertThat(DefaultDitaOtFramework.bundledRevision()).isEqualTo("10");
        }
    }
}
