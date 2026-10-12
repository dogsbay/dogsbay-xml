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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ValidationResult;

/**
 * A4: the bundled DITA catalog must resolve the subjectScheme DOCTYPE so an
 * authored controlled-values map can be DTD-validated (previously failed with
 * "subjectScheme.dtd (No such file or directory)").
 */
class SubjectSchemeCatalogTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private Path bundledCatalog() throws Exception {
        java.net.URL u = getClass().getResource(
            "/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/dtd/catalog.xml");
        Assumptions.assumeTrue(u != null, "bundled DITA catalog not on the test classpath");
        return Path.of(u.toURI());
    }

    @Test
    void subjectSchemeMapValidatesAgainstBundledCatalog() throws Exception {
        Path f = dir.resolve("controlled-values.ditamap");
        Files.writeString(f, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE subjectScheme PUBLIC "-//OASIS//DTD DITA Subject Scheme Map//EN" "subjectScheme.dtd">
            <subjectScheme>
              <subjectdef keys="platform">
                <subjectdef keys="windows"/>
                <subjectdef keys="mac"/>
                <subjectdef keys="linux"/>
              </subjectdef>
              <enumerationdef>
                <attributedef name="platform"/>
                <subjectdef keyref="platform"/>
              </enumerationdef>
            </subjectScheme>
            """);

        ValidationResult r = executor.execute(
            new ValidateCommand(f, null, List.of(bundledCatalog())));

        assertThat(r.valid())
            .as("subjectScheme should validate: %s", r.errors())
            .isTrue();
        assertThat(r.errors()).isEmpty();
    }
}
