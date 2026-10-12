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
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry;

/** dogsbay-xml live-preview: status and stop of previews other processes run; the background child's options. */
class LivePreviewCmdTest {

    @TempDir Path registry;
    @TempDir Path root;
    private Process other;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("dogsbay.previews.dir", registry.toString());
        root = LivePreview.canonical(root);
        other = new ProcessBuilder("sleep", "60").start();
    }

    @AfterEach
    void tearDown() {
        other.destroyForcibly();
        System.clearProperty("dogsbay.previews.dir");
        System.clearProperty("dogsbay.previews.stopWait");
    }

    private record Run(int code, String out) {
    }

    private Run run(String... args) {
        PrintStream stdout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = DogsBayCli.commandLine().execute(args);
            return new Run(code, out.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(stdout);
        }
    }

    private void register(String owner) {
        PreviewRegistry.put(new PreviewRegistry.Entry(root.toString(), "site", owner, other.pid(), "http://localhost:4321/",
                "READY", "Ready", null, null, "t1", 0, 0,
                other.toHandle().info().startInstant().map(Object::toString).orElse(null)));
    }

    @Test
    void statusListsPreviewsOtherProcessesRun() {
        assertThat(run("live-preview", "status", root.toString()).out()).contains("No live preview is running");
        register("mcp");
        Run r = run("live-preview", "status", root.toString());
        assertThat(r.out()).contains("site").contains("mcp " + other.pid()).contains("http://localhost:4321/");
    }

    @Test
    void stopSaysWhenNothingRunsAndReportsWhatItCouldNotStop() {
        assertThat(run("live-preview", "stop", root.toString()).out()).contains("No live preview is running");
        register("cli"); // a process whose command line is not a preview's: asked, never ended
        System.setProperty("dogsbay.previews.stopWait", "1000");
        Run r = run("live-preview", "stop", "--all", root.toString());
        assertThat(r.code()).isEqualTo(1);
        assertThat(r.out()).contains("Not stopped: site");
        assertThat(other.isAlive()).isTrue();
    }

    @Test
    void theBackgroundChildDropsDebuggersAndAgents() {
        assertThat(LivePreviewCmd.jvmOptions(List.of("-Xmx2g", "-agentlib:jdwp=transport=dt_socket,address=5005",
                "-javaagent:/x.jar", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-Xdebug")))
                .containsExactly("-Xmx2g", "--add-opens", "java.base/java.lang=ALL-UNNAMED");
    }

    @Test
    void theToolDoesNotStartAPreviewThatWouldDieWithIt() {
        PrintStream stderr = System.err;
        Run r = run("tool", "live_preview", "{\"root\": \"" + root.toString().replace("\\", "\\\\")
                + "\", \"action\": \"start\"}");
        assertThat(r.code()).as("refused").isNotZero();
        assertThat(PreviewRegistry.list(root)).as("nothing started").isEmpty();
        System.setErr(stderr);
    }
}
