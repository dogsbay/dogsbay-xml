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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;

/** P3: edit_map across the headless executor. */
class EditMapCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private File write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p.toFile();
    }

    private String read(File f) throws Exception {
        return Files.readString(f.toPath());
    }

    private static final String MAP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <map>
              <title>Guide</title>
              <topicref href="intro.dita" id="intro"/>
            </map>
            """;

    @Test
    void insertsBookmapDivisionsViaTheEditMapEngine() throws Exception {
        File bm = write("book.ditamap",
                "<bookmap><booktitle><mainbooktitle>Book</mainbooktitle></booktitle></bookmap>");

        // a chapter nests under the bookmap root (DITA 1.3 book structure)
        exec.execute(new EditMapCommand(bm.toString(), "insert", null, "/", null,
                "chapter", null, null, "intro.dita", "Introduction", null, false));
        assertThat(read(bm)).contains("<chapter").contains("intro.dita");

        // a chapter cannot nest under a topicref — the content model rejects it
        File m = write("g.ditamap", MAP);
        assertThatThrownBy(() -> exec.execute(new EditMapCommand(m.toString(), "insert",
                null, "intro", null, "chapter", null, null, "x.dita", "X", null, false)))
                .hasMessageContaining("cannot be nested");
    }

    @Test
    void setAttrInsertRemoveMove() throws Exception {
        File m = write("g.ditamap", MAP);

        // set-attr
        exec.execute(new EditMapCommand(m.toString(), "set-attr", "intro", null, null,
                null, "toc", "yes", null, null, null, false));
        assertThat(read(m)).contains("toc=\"yes\"");

        // insert
        exec.execute(new EditMapCommand(m.toString(), "insert", null, "/", 0,
                "topicref", null, null, "first.dita", "First", null, false));
        assertThat(read(m)).contains("first.dita").contains("navtitle=\"First\"");

        // move: reorder the just-inserted first.dita (now /1) to the end
        exec.execute(new EditMapCommand(m.toString(), "move", "/1", "/", 9,
                null, null, null, null, null, null, false));
        // remove the intro topicref
        exec.execute(new EditMapCommand(m.toString(), "remove", "intro", null, null,
                null, null, null, null, null, null, false));
        assertThat(read(m)).doesNotContain("intro.dita");
    }

    @Test
    void dryRunWritesNothingButReturnsPlan() throws Exception {
        File m = write("g.ditamap", MAP);
        String before = read(m);
        MapEditResult r = exec.execute(new EditMapCommand(m.toString(), "set-attr", "intro",
                null, null, null, "toc", "yes", null, null, null, true));
        assertThat(r.dryRun()).isTrue();
        assertThat(r.files()).hasSize(1);
        assertThat(read(m)).isEqualTo(before); // unchanged
    }

    @Test
    void removeReportsInboundReferenceWarnings() throws Exception {
        File m = write("guide.ditamap", """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title><topicref href="a.dita" id="a"/></map>
            """);
        write("a.dita", "<concept id=\"a\"><title>A</title><conbody/></concept>");
        write("b.dita", """
            <concept id="b"><title>B</title>
              <conbody><xref href="a.dita"/></conbody></concept>
            """);
        MapEditResult r = exec.execute(new EditMapCommand(m.toString(), "remove", "a",
                null, null, null, null, null, null, null, null, true));
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("a.dita"));
    }

    @Test
    void validatesArguments() throws Exception {
        File m = write("g.ditamap", MAP);
        // missing --ref for remove
        assertThatThrownBy(() -> exec.execute(new EditMapCommand(m.toString(), "remove",
                null, null, null, null, null, null, null, null, null, false)))
            .isInstanceOf(CommandException.class)
            .hasFieldOrPropertyWithValue("code", CommandException.ErrorCode.INVALID_ARGUMENT);
        // unknown op
        assertThatThrownBy(() -> exec.execute(new EditMapCommand(m.toString(), "frobnicate",
                "intro", null, null, null, null, null, null, null, null, false)))
            .isInstanceOf(CommandException.class);
        // map not found
        assertThatThrownBy(() -> exec.execute(new EditMapCommand(
                dir.resolve("nope.ditamap").toString(), "remove", "x",
                null, null, null, null, null, null, null, null, false)))
            .isInstanceOf(CommandException.class)
            .hasFieldOrPropertyWithValue("code", CommandException.ErrorCode.FILE_NOT_FOUND);
    }
}
