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

package com.dogsbay.dogsbayaieditor.dita.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the key space view turns paths into something a reader recognises, and back again.
 *
 * <p>The table shows paths relative to the map's folder — an absolute path is unreadable in a narrow column
 * and says nothing a reader wants — so a double-click has to turn what is shown back into a file. Getting
 * that pair wrong means rows that look right and open nothing.
 */
class KeySpaceDialogTest {

    @TempDir Path project;

    @Test
    void aPathUnderTheMapIsShownRelativeToIt() throws Exception {
        File map = project.resolve("guide.ditamap").toFile();
        String topic = project.resolve("topics/intro.dita").toString();

        assertThat(KeySpaceDialog.shortPath(map, topic)).isEqualTo("topics" + File.separator + "intro.dita");
    }

    @Test
    void aPathOutsideTheMapsFolderIsLeftWhole() throws Exception {
        File map = project.resolve("guide.ditamap").toFile();

        // A key can resolve into a shared library beside the project; shortening that to a bare name would
        // say it is here, which it is not.
        assertThat(KeySpaceDialog.shortPath(map, "/elsewhere/shared/legal.dita"))
                .isEqualTo("/elsewhere/shared/legal.dita");
    }

    @Test
    void whatIsShownResolvesBackToTheFile() throws Exception {
        Files.createDirectory(project.resolve("topics"));
        Path topic = Files.writeString(project.resolve("topics/intro.dita"), "<concept id=\"i\"/>");
        File map = project.resolve("guide.ditamap").toFile();

        String shown = KeySpaceDialog.shortPath(map, topic.toString());

        assertThat(KeySpaceDialog.resolve(map, shown)).isEqualTo(topic.toFile());
    }

    @Test
    void aRowPointingAtNothingOpensNothing() {
        File map = project.resolve("guide.ditamap").toFile();

        // A key whose href is broken still earns a row — that is worth seeing — but double-clicking it must
        // not try to open a file that is not there.
        assertThat(KeySpaceDialog.resolve(map, "topics/gone.dita")).isNull();
        assertThat(KeySpaceDialog.resolve(map, "")).isNull();
        assertThat(KeySpaceDialog.resolve(map, null)).isNull();
    }

    @Test
    void aKeyThatDoesNotResolveSaysSo() {
        File map = project.resolve("guide.ditamap").toFile();
        // An unresolved href is relative to the map that defined it, not to this one, so shown plainly it
        // reads exactly like a path that resolved — and a key pointing at a deleted topic looks fine.
        var broken = new com.dogsbay.dogsbayaieditor.commands.results.KeyInfo(
                "gone", map.getAbsolutePath(), 4, "../shared/gone.dita", null, null);

        assertThat(KeySpaceDialog.resolvedOrNot(map, broken)).contains("does not resolve");
    }

    @Test
    void aKeyThatResolvesShowsWhereTo() throws Exception {
        Files.createDirectory(project.resolve("topics"));
        Path topic = project.resolve("topics/intro.dita");
        File map = project.resolve("guide.ditamap").toFile();
        var key = new com.dogsbay.dogsbayaieditor.commands.results.KeyInfo(
                "intro", map.getAbsolutePath(), 3, "topics/intro.dita", topic.toString(), null);

        assertThat(KeySpaceDialog.resolvedOrNot(map, key))
                .isEqualTo("topics" + File.separator + "intro.dita");
    }

    @Test
    void aKeyWithNoHrefShowsNothingRatherThanNull() {
        assertThat(KeySpaceDialog.shortPath(project.resolve("g.ditamap").toFile(), null)).isEmpty();
    }
}
