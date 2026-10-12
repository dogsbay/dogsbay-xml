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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whether a build wrote its output, so output from a build with errors is still offered. */
class BuildOutputTest {

    @TempDir Path dir;

    @Test
    void seesFilesWrittenDuringTheBuildAnywhereInTheOutput() throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out.resolve("astro/dist/topics"));
        Path old = Files.writeString(out.resolve("old.html"), "x");
        long started = System.currentTimeMillis();
        old.toFile().setLastModified(started - 60_000);
        assertThat(BuildOutput.writtenSince(out.toFile(), started)).as("only an earlier build's file").isFalse();

        Files.writeString(out.resolve("astro/dist/topics/new.html"), "y");
        assertThat(BuildOutput.writtenSince(out.toFile(), started)).isTrue();
        assertThat(BuildOutput.writtenSince(dir.resolve("missing").toFile(), started)).isFalse();
        assertThat(BuildOutput.writtenSince(null, started)).isFalse();
    }

    @Test
    void offersASitePreviewOnlyForASiteThisBuildWrote() throws Exception {
        Path dist = dir.resolve("out/astro/dist");
        Files.createDirectories(dist);
        File index = Files.writeString(dist.resolve("index.html"), "<html/>").toFile();
        long started = System.currentTimeMillis();

        index.setLastModified(started - 60_000);
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile(), started)).as("a stale site").isNull();
        index.setLastModified(started + 1_000);
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile(), started)).isEqualTo(dist.toFile());
    }

    /** The same staleness rule on the folder dogsbay-site writes now. */
    @Test
    void offersASitePreviewOnlyForASsgSiteThisBuildWrote() throws Exception {
        Path site = dir.resolve("out/site");
        Files.createDirectories(site.resolve("_dogsbay"));
        File index = Files.writeString(site.resolve("index.html"), "<html/>").toFile();
        long started = System.currentTimeMillis();

        index.setLastModified(started - 60_000);
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile(), started)).as("a stale site").isNull();
        index.setLastModified(started + 1_000);
        assertThat(SitePreview.siteRoot(dir.resolve("out").toFile(), started)).isEqualTo(site.toFile());
    }
}
