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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.SpecializationInfoResult;

/** specialization_info: reports DOCTYPE + root + @class chain + @domains. */
class SpecializationInfoCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    @Test
    void reportsSpecializationOfATopic() throws Exception {
        Path f = dir.resolve("c.dita");
        Files.writeString(f, "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" "
                + "\"concept.dtd\">\n"
                + "<concept id=\"c\" class=\"- topic/topic concept/concept \">"
                + "<title>T</title></concept>");

        SpecializationInfoResult r = exec.execute(
                new SpecializationInfoCommand(f.toString()));

        assertThat(r.root()).isEqualTo("concept");
        assertThat(r.specialized()).isTrue();
        assertThat(r.classChain()).containsExactly("topic/topic", "concept/concept");
        assertThat(r.publicId()).contains("DITA Concept");
    }

    @Test
    void missingFileIsAClearError() {
        assertThatThrownBy(() -> exec.execute(
                new SpecializationInfoCommand(dir.resolve("nope.dita").toString())))
                .hasMessageContaining("not found");
    }
}
