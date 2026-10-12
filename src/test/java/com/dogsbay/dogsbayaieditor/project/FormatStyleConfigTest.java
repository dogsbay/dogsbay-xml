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

package com.dogsbay.dogsbayaieditor.project;

import java.nio.file.Files;
import java.nio.file.Path;

import com.dogsbay.xml.format.FormatStyle;
import com.dogsbay.xml.format.FormatStyleXml;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FormatStyleConfigTest {

    @Test
    @DisplayName("FormatStyle survives an XML round-trip")
    void xmlRoundTrip() {
        FormatStyle style = FormatStyle.defaults()
                .withIndent(FormatStyle.IndentUnit.TABS, 4)
                .withMaxLineWidth(100)
                .withPreserveTextLineBreaks(false)
                .withPreserveSpaceElements(java.util.Set.of("myverbatim"));
        FormatStyle back = FormatStyleXml.parse(FormatStyleXml.toElement(style));
        assertThat(back.unit()).isEqualTo(FormatStyle.IndentUnit.TABS);
        assertThat(back.indentSize()).isEqualTo(4);
        assertThat(back.maxLineWidth()).isEqualTo(100);
        assertThat(back.preserveTextLineBreaks()).isFalse();
        assertThat(back.preserveSpaceElements()).contains("myverbatim", "codeblock");
    }

    @Test
    @DisplayName("project config persists and reloads the house style + format-on-save")
    void configPersistence(@TempDir Path ws) throws Exception {
        DogsbayProjectConfig cfg = new DogsbayProjectConfig();
        cfg.setFormatStyle(FormatStyle.defaults().withIndent(FormatStyle.IndentUnit.TABS, 4));
        cfg.setFormatOnSave(true);
        cfg.saveShared(ws);

        DogsbayProjectConfig loaded = DogsbayProjectConfig.load(ws);
        assertThat(loaded.getFormatStyle().unit()).isEqualTo(FormatStyle.IndentUnit.TABS);
        assertThat(loaded.isFormatOnSave()).isTrue();
    }

    @Test
    @DisplayName("resolver: project style wins; default when none declared")
    void resolverPrecedence(@TempDir Path ws) throws Exception {
        // no .dogsbay yet → default
        assertThat(FormatStyleResolver.forWorkspace(ws)).isEqualTo(FormatStyle.defaults());

        DogsbayProjectConfig cfg = new DogsbayProjectConfig();
        cfg.setFormatStyle(FormatStyle.defaults().withIndent(FormatStyle.IndentUnit.TABS, 4));
        cfg.saveShared(ws);

        assertThat(FormatStyleResolver.forWorkspace(ws).unit()).isEqualTo(FormatStyle.IndentUnit.TABS);

        // a file deep in the workspace resolves to the same project style
        Path deep = ws.resolve("topics/sub/topic.dita");
        Files.createDirectories(deep.getParent());
        Files.writeString(deep, "<x/>");
        assertThat(FormatStyleResolver.forFile(deep).unit()).isEqualTo(FormatStyle.IndentUnit.TABS);
    }

    @Test
    @DisplayName("a workspace with no .dogsbay resolves to the default style")
    void noProjectDefault(@TempDir Path ws) throws Exception {
        Path file = ws.resolve("loose.xml");
        Files.writeString(file, "<x/>");
        assertThat(FormatStyleResolver.forFile(file)).isEqualTo(FormatStyle.defaults());
        assertThat(FormatStyleResolver.isFormatOnSave(file)).isFalse();
    }
}
