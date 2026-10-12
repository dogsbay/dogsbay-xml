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

package com.dogsbay.dogsbayaieditor.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What `format` puts on stdout.
 *
 * <p>It had no test, and two opposite mistakes were made in it: printing a line added a blank one
 * (so `format f > out` differed from `format --write f`), and then printing raw left the output
 * unterminated for a project whose house style ends files without a final newline.
 */
class FormatCmdOutputTest {

    @TempDir Path dir;

    private String run(String... args) {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            new CommandLine(new FormatCmd()).execute(args);
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(previous);
        }
    }

    private Path topic(String name) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><a x=\"1\"/></root>\n");
        return f;
    }

    private void houseStyle(String attributes) throws Exception {
        Path cfg = dir.resolve(".dogsbay");
        Files.createDirectories(cfg);
        Files.writeString(cfg.resolve("config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<config><format-style " + attributes + "/></config>\n");
    }

    @Test
    @DisplayName("stdout matches what --write puts in the file")
    void stdoutMatchesTheWrittenFile() throws Exception {
        Path a = topic("a.xml");
        Path b = topic("b.xml");

        String printed = run(a.toString());
        run("--write", b.toString());

        assertThat(printed).isEqualTo(Files.readString(b, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("stdout is terminated even when the house style ends files without a newline")
    void stdoutIsAlwaysTerminated() throws Exception {
        houseStyle("final-newline=\"false\"");
        Path a = topic("a.xml");

        String printed = run(a.toString());

        // Unterminated output leaves the shell prompt on the last line and makes wc -l short by one.
        assertThat(printed).endsWith("\n");
        // doesNotContain("\n\n</root>") was dead: the over-termination bug put the blank line AFTER
        // </root>, so that assertion could never fire for this fixture.
        assertThat(printed).doesNotEndWith("\n\n");
    }

    @Test
    @DisplayName("several documents are not run together")
    void severalDocumentsAreSeparated() throws Exception {
        houseStyle("final-newline=\"false\"");
        Path a = topic("a.xml");
        Path b = topic("b.xml");

        String printed = run(a.toString(), b.toString());

        // Both declarations must start a line; concatenating gave `</root><?xml …` on one.
        assertThat(printed.lines().filter(l -> l.startsWith("<?xml")).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("stdout and --write diverge by exactly one newline when the style ends files without one")
    void theDivergenceIsDeliberateAndExact() throws Exception {
        // Deliberate: a stream has to be terminated, a file must honour the style. Recorded so a
        // future "make them match" change has to argue with a test rather than just look tidier.
        houseStyle("final-newline=\"false\"");
        Path a = topic("a.xml");
        Path b = topic("b.xml");

        String printed = run(a.toString());
        run("--write", b.toString());

        assertThat(printed).isEqualTo(Files.readString(b, StandardCharsets.UTF_8) + "\n");
    }
}
