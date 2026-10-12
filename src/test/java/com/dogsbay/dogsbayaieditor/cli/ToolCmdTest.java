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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/** Every tool from the command line, and the two editing commands. */
class ToolCmdTest {

    private static final String TOPIC = "<topic id=\"a\"><title>A</title><body><p>Old words.</p>"
            + "<note>Remove me.</note></body></topic>";

    @TempDir Path dir;

    /** Run the CLI; return exit code, standard output and error. */
    private record Run(int code, String out, String err) {
    }

    private Run run(String... args) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringWriter err = new StringWriter();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            CommandLine cli = DogsBayCli.commandLine().setErr(new PrintWriter(err))
                    .setExecutionExceptionHandler((ex, cmd, parsed) -> {
                        cmd.getErr().println("Error: " + ex.getMessage());
                        return 2;
                    });
            int code = cli.execute(args);
            return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString());
        } finally {
            System.setOut(stdout);
        }
    }

    @Test
    void listsTheToolsMarkingThoseThatNeedTheEditor() {
        Run r = run("tool", "--list");
        assertThat(r.code()).isZero();
        assertThat(r.out()).contains("edit_structure").containsPattern("get_cursor\\s+\\[editor\\]");
    }

    @Test
    void runsAnyToolWithJsonArguments() throws Exception {
        Path file = Files.writeString(dir.resolve("a.dita"), TOPIC);
        Run r = run("tool", "get_document_content", "{\"file\": \"" + file.toString().replace("\\", "\\\\") + "\"}");
        assertThat(r.code()).as(r.err()).isZero();
        assertThat(r.out()).contains("Old words.");

        Path args = Files.writeString(dir.resolve("args.json"), "{\"file\": \"" + file.toString().replace("\\", "\\\\")
                + "\", \"find\": \"Old\", \"replace\": \"New\"}");
        assertThat(run("tool", "edit_document", "@" + args).code()).isZero();
        assertThat(Files.readString(file)).as("the person's edit: no marks").isEqualTo(TOPIC.replace("Old", "New"));
    }

    @Test
    void saysWhatIsWrongWithACall() {
        assertThat(run("tool", "edit_document", "{\"path\": \"x\"}").err()).contains("did you mean \"file\"");
        assertThat(run("tool", "get_cursor").err()).contains("needs the running editor");
        assertThat(run("tool", "no_such_tool").err()).contains("No tool named no_such_tool");
        assertThat(run("tool", "edit_structure", "--schema").out()).contains("\"steps\"");
    }

    @Test
    void editsAndEditsStructureFromTheirOwnCommands() throws Exception {
        Path file = Files.writeString(dir.resolve("a.dita"), TOPIC);
        Run edit = run("edit", file.toString(), "--find", "Old words.", "--replace", "New words.");
        assertThat(edit.code()).isZero();
        assertThat(edit.out()).contains("1 occurrence replaced");

        Run dry = run("edit-structure", file.toString(), "--steps", "[{\"op\": \"delete\", \"select\": \"//note\"}]",
                "--dry-run");
        assertThat(dry.out()).startsWith("Dry run, nothing written").contains("deleted <note>");
        assertThat(Files.readString(file)).contains("Remove me.");
        assertThat(run("edit-structure", file.toString(), "--steps", "[{\"op\": \"delete\", \"select\": \"//note\"}]")
                .code()).isZero();
        assertThat(Files.readString(file)).isEqualTo(TOPIC.replace("Old", "New").replace("<note>Remove me.</note>", ""));
    }
}
