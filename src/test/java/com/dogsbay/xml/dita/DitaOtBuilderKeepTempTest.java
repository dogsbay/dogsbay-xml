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

package com.dogsbay.xml.dita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;

/**
 * Real DITA-OT builds with the engine bundled in the editor: {@code clean.temp=no}
 * keeps the temporary files (which DITA-OT's Java processor would otherwise always
 * delete), and a normal build still leaves none behind.
 */
class DitaOtBuilderKeepTempTest {

    @TempDir static Path engine;
    private static File home;

    @TempDir Path dir;

    @BeforeAll
    static void extractEngine() throws Exception {
        assumeTrue(DefaultDitaOtFramework.isBundled(), "the bundled DITA-OT framework is not on the classpath");
        home = DefaultDitaOtFramework.extractBundled(engine.toFile());
    }

    /** A map with a text key and one topic that uses it. */
    static Path writeProject(Path root) throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("guide.ditamap"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
                <map>
                  <title>Guide</title>
                  <keydef keys="product"><topicmeta><keywords><keyword>Audacity</keyword></keywords></topicmeta></keydef>
                  <topicref href="topics/t.dita"/>
                </map>
                """);
        Files.writeString(root.resolve("topics/t.dita"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="t">
                  <title>About <keyword keyref="product"/></title>
                  <body><p platform="windows">Install <keyword keyref="product"/>.</p></body>
                </topic>
                """);
        return root.resolve("guide.ditamap");
    }

    private static long count(Path dir, String suffix) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        }
    }

    @Test
    void keepsTemporaryFilesWhenCleanTempIsNo() throws Exception {
        Path map = writeProject(dir.resolve("project"));
        Path temp = dir.resolve("temp");
        Path out = dir.resolve("out");

        List<DitaOtMessage> messages = new DitaOtBuilder(home.getPath()).build(map.toFile(), "html5", List.of(),
                Map.of("clean.temp", "no"), out.toFile(), temp.toFile());

        assertThat(messages).noneMatch(DitaOtMessage::isError);
        assertThat(temp.resolve(".job.xml")).as("DITA-OT's record of the temp files").exists();
        String topic = Files.readString(temp.resolve("topics/t.dita"));
        assertThat(topic).as("preprocessed, with source stamps, keys and conditions")
                .contains("xtrf=").contains("keyref=\"product\"").contains("platform=\"windows\"");
        assertThat(count(out, ".html")).isPositive();
    }

    @Test
    void leavesNoTemporaryFilesByDefault() throws Exception {
        Path map = writeProject(dir.resolve("project"));
        Path temp = dir.resolve("temp");
        Path out = dir.resolve("out");
        Map<String, String> params = new HashMap<>();
        params.put("clean.temp", "yes");

        List<DitaOtMessage> messages = new DitaOtBuilder(home.getPath()).build(map.toFile(), "html5", List.of(),
                params, out.toFile(), temp.toFile());

        assertThat(messages).noneMatch(DitaOtMessage::isError);
        assertThat(count(temp, ".job.xml")).isZero();
        assertThat(count(out, ".html")).isPositive();
    }

    @Test
    void recognisesTheValuesThatKeepTemporaryFiles() {
        assertThat(DitaOtBuilder.keepsTemp(Map.of("clean.temp", "no"))).isTrue();
        assertThat(DitaOtBuilder.keepsTemp(Map.of("clean.temp", " NO "))).isTrue();
        assertThat(DitaOtBuilder.keepsTemp(Map.of("clean.temp", "false"))).isTrue();
        assertThat(DitaOtBuilder.keepsTemp(Map.of("clean.temp", "yes"))).isFalse();
        assertThat(DitaOtBuilder.keepsTemp(Map.of("args.filter", "x"))).isFalse();
        assertThat(DitaOtBuilder.keepsTemp(null)).isFalse();
    }
}
