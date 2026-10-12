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
package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MentionsTest {

    @Test
    void mentionsRoundTripFromFileToTextToContent(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("topics/a.dita"), "<topic id=\"a\"/>");
        Files.writeString(root.resolve("topics/my topic.dita"), "<topic/>");
        Files.writeString(root.getParent().resolve("outside.dita"), "no");

        assertThat(Mentions.forFile(root.resolve("topics/a.dita").toFile(), root)).isEqualTo("@topics/a.dita");
        assertThat(Mentions.forFile(root.resolve("topics/my topic.dita").toFile(), root)).isNull();
        assertThat(Mentions.forFile(root.getParent().resolve("outside.dita").toFile(), root)).isNull();
        assertThat(Mentions.forFile(root.resolve("topics").toFile(), root)).isNull();

        assertThat(Mentions.files("see @topics/a.dita and @topics/a.dita, plus @missing.dita.", root))
                .containsExactly(root.resolve("topics/a.dita"));
        String inlined = Mentions.inlined("Summarise @topics/a.dita.", root);
        assertThat(inlined).startsWith("Summarise @topics/a.dita.\n\n--- topics/a.dita ---\n<topic id=\"a\"/>");
        assertThat(Mentions.inlined("nothing here", root)).isEqualTo("nothing here");
        assertThat(Mentions.inlined("@x", null)).isEqualTo("@x");
    }
}
