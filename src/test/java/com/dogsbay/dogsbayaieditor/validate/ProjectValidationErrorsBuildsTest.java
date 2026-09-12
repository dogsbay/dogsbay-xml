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

package com.dogsbay.dogsbayaieditor.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.dita.DitaOtMessage;

/** "Build Deliverables" diagnostics flatten into Project-Validation XMLErrors. */
class ProjectValidationErrorsBuildsTest {

    @TempDir Path dir;

    @Test
    void resolvesFileTagsDeliverableAndPrefixesCode() throws Exception {
        Files.writeString(dir.resolve("topic.dita"), "<topic/>");
        DeliverableBuild build = new DeliverableBuild("beginner-windows", "html5",
                dir.resolve("out/beginner-windows").toString(), false, List.of(
                        new DitaOtMessage("DOTJ012F", "FATAL", "Failed to parse.", "topic.dita", 4, 2)));

        List<XMLError> errors = ProjectValidationErrors.fromDitaOtBuilds(List.of(build), dir.toFile());

        assertThat(errors).hasSize(1);
        XMLError e = errors.get(0);
        assertThat(e.getType()).isEqualTo(XMLError.FATAL);
        assertThat(e.getLineNumber()).isEqualTo(4);
        assertThat(e.getMessage()).startsWith("[DOTJ012F] [beginner-windows]");
        assertThat(e.getSystemId())
                .isEqualTo(new File(dir.toFile(), "topic.dita").toURI().toString());
    }

    @Test
    void unresolvableFileGetsNoClickableLocation() {
        DeliverableBuild build = new DeliverableBuild("full", "html5", "out/full", false, List.of(
                new DitaOtMessage(null, "ERROR", "boom in nowhere.dita", "nowhere.dita", -1, -1)));

        List<XMLError> errors = ProjectValidationErrors.fromDitaOtBuilds(List.of(build), dir.toFile());

        assertThat(errors).singleElement().satisfies(e -> {
            // No directory fallback — clicking must not try to open the project folder.
            assertThat(e.getSystemId()).isNull();
            assertThat(e.getMessage()).contains("[full]");
        });
    }

    @Test
    void nullResultsYieldsEmpty() {
        assertThat(ProjectValidationErrors.fromDitaOtBuilds(null, dir.toFile())).isEmpty();
    }
}
