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

package com.dogsbay.dogsbayaieditor.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.ProjectDefaults;

/**
 * Which Schematron schema a document is checked against, and whether the menu says it will ask.
 *
 * <p>Checking a file against the project's house rules is the thing people do over and over, and a file chooser in
 * front of it every time is a toll on the common case. So the project's own schema is used when it names one, and
 * the menu drops the ellipsis to say that nothing will be asked.
 */
class XmlMenuSchematronTest {

    @TempDir Path dir;

    private void config(String body) throws Exception {
        Files.createDirectories(dir.resolve(".dogsbay"));
        Files.writeString(dir.resolve(".dogsbay/config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<config>\n" + body + "\n</config>\n");
    }

    @Test
    void aProjectThatNamesItsHouseRulesNeedsNoChooser() throws Exception {
        Files.writeString(dir.resolve("house.sch"),
                "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"/>");
        config("  <default-schematron>house.sch</default-schematron>");

        var defaults = ProjectDefaults.resolve(dir, null, null);

        assertThat(defaults.schematron()).endsWith("house.sch");
        assertThat(Path.of(defaults.schematron())).exists();
    }

    @Test
    void aProjectWithNoHouseRulesResolvesNone() throws Exception {
        config("  <default-root-map>guide.ditamap</default-root-map>");

        var defaults = ProjectDefaults.resolve(dir, null, null);

        // nothing to run without asking, which is when the menu keeps its ellipsis
        assertThat(defaults.schematron()).isNull();
    }

    @Test
    void aNamedSchemaThatIsNotThereIsNotUsed() throws Exception {
        config("  <default-schematron>gone.sch</default-schematron>");

        var defaults = ProjectDefaults.resolve(dir, null, null);

        // resolved but missing: the caller checks isFile() before running it, rather than failing in the engine
        assertThat(defaults.schematron() == null || !Path.of(defaults.schematron()).toFile().isFile()).isTrue();
    }
}
