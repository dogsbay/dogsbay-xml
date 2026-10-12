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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * The exit codes are the point: this is advertised as CI-friendly, and CI reads nothing but the code.
 */
class CheckOutputLinksCmdTest {

    @TempDir Path dir;

    private record Run(int code, String out) {
    }

    private Run run(String... args) {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int code = new CommandLine(new CheckOutputLinksCmd()).execute(args);
            return new Run(code, captured.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(previous);
        }
    }

    @Test
    void cleanOutputExitsZero() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<a href=\"t.html\">T</a>");
        Files.writeString(dir.resolve("t.html"), "<p>T</p>");

        Run run = run(dir.toString());

        assertThat(run.code()).isZero();
        assertThat(run.out()).contains("No broken links");
    }

    @Test
    void aBrokenLinkExitsOneAndSaysWhereItIs() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<a href=\"gone.html\">Gone</a>");

        Run run = run(dir.toString());

        assertThat(run.code()).isEqualTo(1);
        assertThat(run.out()).contains("index.html:1").contains("@href=\"gone.html\"")
                .contains("1 broken link(s)");
    }

    @Test
    void aFolderThatIsNotThereIsSaidPlainly() {
        Run run = run(dir.resolve("never-built").toString());

        assertThat(run.code()).isNotZero();
    }
}
