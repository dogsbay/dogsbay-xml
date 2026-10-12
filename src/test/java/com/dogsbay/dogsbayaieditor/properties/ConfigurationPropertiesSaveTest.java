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

package com.dogsbay.dogsbayaieditor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * The preferences store must be persisted atomically: a save writes well-formed
 * XML, leaves no temp litter, and never truncates the previously-good file.
 */
class ConfigurationPropertiesSaveTest {

    @TempDir Path dir;

    private ConfigurationProperties newConfig(Path target) throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        DogsBayDocument doc = new DogsBayDocument(target.toUri().toURL(), root);
        return new ConfigurationProperties(doc);
    }

    private long tmpFileCount() throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
        }
    }

    private void assertWellFormed(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Parsing throws if the file is truncated / not well-formed.
        factory.newDocumentBuilder().parse(file.toFile());
    }

    @Test
    void saveToDiskWritesWellFormedXmlAtomically() throws Exception {
        Path target = dir.resolve("config.xml");
        ConfigurationProperties config = newConfig(target);

        config.saveToDisk();

        assertThat(target).exists();
        assertWellFormed(target);
        assertThat(tmpFileCount()).isZero();
    }

    @Test
    void repeatedSavesNeverLeaveTruncatedFile() throws Exception {
        Path target = dir.resolve("config.xml");
        ConfigurationProperties config = newConfig(target);

        // Simulate the timer firing repeatedly; after each the file must stay well-formed.
        for (int i = 0; i < 10; i++) {
            config.saveToDisk();
            assertWellFormed(target);
        }
        assertThat(tmpFileCount()).isZero();
    }
}
