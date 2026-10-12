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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DocumentInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ParseResult;

/**
 * The raw-XML toolbox must resolve DITA grammars through the bundled catalog on
 * its own, without {@code xml.catalog.files} being set for it.
 *
 * <p>This is how the CLI and the MCP server actually run: nothing sets that
 * property, and {@code info} on a real {@code .ditamap} failed with
 * "map.dtd (No such file or directory)" while {@code validate} on the same file
 * was fine, because only validation passed the bundled catalog to the parser.
 */
class HeadlessBundledCatalogTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    @TempDir Path dir;

    private Path map;
    private String previousCatalog;

    @BeforeEach
    void writeMapWithNoCatalogProperty() throws Exception {
        previousCatalog = System.getProperty("xml.catalog.files");
        System.clearProperty("xml.catalog.files");

        map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
            <map>
              <title>Guide</title>
              <topicref href="topics/one.dita"/>
            </map>
            """);
    }

    @AfterEach
    void restoreCatalogProperty() {
        if (previousCatalog != null) {
            System.setProperty("xml.catalog.files", previousCatalog);
        }
    }

    @Test
    void infoReadsAMapWhoseDtdOnlyTheBundledCatalogKnows() throws Exception {
        DocumentInfo info = executor.execute(new InfoCommand(map));

        assertThat(info.rootElement()).isEqualTo("map");
        assertThat(info.grammarType()).isEqualTo("DTD");
    }

    @Test
    void parseReadsTheSameMap() throws Exception {
        ParseResult r = executor.execute(new ParseCommand(map));

        assertThat(r.wellFormed()).isTrue();
        assertThat(r.rootElement()).isEqualTo("map");
    }
}
