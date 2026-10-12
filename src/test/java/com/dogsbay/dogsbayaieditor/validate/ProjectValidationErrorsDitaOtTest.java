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

import com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.dita.DitaOtMessage;

class ProjectValidationErrorsDitaOtTest {

    @TempDir Path dir;

    @Test
    void mapsSeverityResolvesFileAndPrefixesCode() throws Exception {
        Files.writeString(dir.resolve("topic.dita"), "<topic/>");
        Files.writeString(dir.resolve("guide.ditamap"), "<map/>");
        String map = dir.resolve("guide.ditamap").toString();

        var results = List.of(new DitaOtValidation("full", map, null, false, List.of(
            new DitaOtMessage("DOTJ012F", "FATAL", "Failed to parse.", "topic.dita", 4, 2),
            new DitaOtMessage("DOTX047W", "WARN", "Key not defined.", null, -1, -1))));

        List<XMLError> errors = ProjectValidationErrors.fromDitaOt(results, dir.toFile());

        assertThat(errors).hasSize(2);

        XMLError fatal = errors.get(0);
        assertThat(fatal.getType()).isEqualTo(XMLError.FATAL);
        assertThat(fatal.getLineNumber()).isEqualTo(4);
        assertThat(fatal.getMessage()).startsWith("[DOTJ012F]");
        assertThat(fatal.getSystemId())
            .isEqualTo(new File(dir.toFile(), "topic.dita").toURI().toString());

        XMLError warn = errors.get(1);
        assertThat(warn.getType()).isEqualTo(XMLError.WARNING); // WARN -> WARNING
        // no per-message file -> falls back to the deliverable map
        assertThat(warn.getSystemId()).isEqualTo(new File(map).toURI().toString());
    }

    @Test
    void unresolvableFileFallsBackToMap() {
        String map = dir.resolve("guide.ditamap").toString();
        var results = List.of(new DitaOtValidation("full", map, null, false, List.of(
            new DitaOtMessage(null, "ERROR", "boom in nowhere.dita", "nowhere.dita", -1, -1))));

        List<XMLError> errors = ProjectValidationErrors.fromDitaOt(results, dir.toFile());

        assertThat(errors).singleElement().satisfies(e ->
            assertThat(e.getSystemId()).isEqualTo(new File(map).toURI().toString()));
    }

    @Test
    void deduplicatesIdenticalDiagnosticsAcrossDeliverables() throws Exception {
        Files.writeString(dir.resolve("shared.dita"), "<topic/>");
        String mapA = dir.resolve("a.ditamap").toString();
        String mapB = dir.resolve("b.ditamap").toString();
        // Same defect in a shared topic, reported once per deliverable that ships it.
        DitaOtMessage dup = new DitaOtMessage("DOTJ088E", "ERROR",
            "bad element", "shared.dita", 7, 3);
        // A defect unique to the filtered deliverable B must survive.
        DitaOtMessage onlyB = new DitaOtMessage("DOTX031E", "ERROR",
            "filtered link broken", "shared.dita", 20, 1);

        var results = List.of(
            new DitaOtValidation("full", mapA, null, false, List.of(dup)),
            new DitaOtValidation("beginner", mapB, "f.ditaval", false, List.of(dup, onlyB)));

        List<XMLError> errors = ProjectValidationErrors.fromDitaOt(results, dir.toFile());

        assertThat(errors).hasSize(2); // dup collapsed to one, onlyB kept
        assertThat(errors).extracting(XMLError::getMessage)
            .anySatisfy(m -> assertThat(m).contains("bad element"))
            .anySatisfy(m -> assertThat(m).contains("filtered link broken"));
    }

    @Test
    void nullResultsYieldsEmpty() {
        assertThat(ProjectValidationErrors.fromDitaOt(null, dir.toFile())).isEmpty();
    }
}
