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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus;
import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * Previews across processes (2026-09-18): the registry, and starting, stopping and listing a preview another
 * process runs. A real preview needs DITA-OT and the dogsbay CLI; here another process is a sleeping child
 * whose registry entry says it runs one.
 */
class PreviewCommandsTest {

    @TempDir Path registry;
    @TempDir Path root;
    private Process other;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("dogsbay.previews.dir", registry.toString());
        PreviewCommands.stopWaitMillis = 1000;
        root = LivePreview.canonical(root);
        other = new ProcessBuilder("sleep", "60").start();
    }

    @AfterEach
    void tearDown() {
        other.destroyForcibly();
        System.clearProperty("dogsbay.previews.dir");
        PreviewCommands.stopWaitMillis = 10_000;
    }

    private PreviewRegistry.Entry entry(long pid, String owner, String deliverable) {
        String started = ProcessHandle.of(pid).flatMap(h -> h.info().startInstant()).map(Object::toString).orElse(null);
        return new PreviewRegistry.Entry(root.toString(), deliverable, owner, pid, "http://localhost:4321/",
                "READY", "Ready", root.resolve("out/site").toString(), null, "2026-09-18T10:00:00Z", 0, 2, started);
    }

    @Test
    void theRegistryListsLivePreviewsAndForgetsStaleOnes() throws Exception {
        PreviewRegistry.put(entry(other.pid(), "mcp", "site"));
        Process gone = new ProcessBuilder("true").start();
        gone.waitFor();
        PreviewRegistry.put(entry(gone.pid(), "cli", "site-windows"));

        assertThat(PreviewRegistry.list(root)).extracting(PreviewRegistry.Entry::deliverable).containsExactly("site");
        assertThat(Files.list(registry).map(p -> p.getFileName().toString()))
                .as("the stale entry is removed").noneMatch(n -> n.contains("site-windows"));
        assertThat(PreviewRegistry.list(root.resolve("elsewhere"))).isEmpty();
    }

    @Test
    void statusShowsAPreviewAnotherProcessRuns() throws Exception {
        PreviewRegistry.put(entry(other.pid(), "mcp", "site"));
        List<LivePreviewStatus> s = PreviewCommands.status(root, null);
        assertThat(s).singleElement().satisfies(x -> {
            assertThat(x.owner()).isEqualTo("mcp");
            assertThat(x.pid()).isEqualTo(other.pid());
            assertThat(x.url()).isEqualTo("http://localhost:4321/");
            assertThat(x.lastPublish().warnings()).isEqualTo(2);
        });
        assertThat(PreviewCommands.status(root, "nope")).singleElement()
                .satisfies(x -> assertThat(x.state()).isEqualTo("STOPPED"));
    }

    @Test
    void aDeliverableAnotherProcessPreviewsIsNotStartedAgain() throws Exception {
        PreviewRegistry.put(entry(other.pid(), "editor", "site"));
        boolean[] started = {false};
        LivePreviewStatus s = PreviewCommands.start(root,
                new Deliverable("site", root.resolve("guide.ditamap"), null, "dogsbay-site"), null,
                (r, d, home) -> {
                    started[0] = true;
                    return null;
                }, p -> { });
        assertThat(started[0]).isFalse();
        assertThat(s.message()).startsWith("Already running in the editor");
        assertThat(s.url()).isEqualTo("http://localhost:4321/");
    }

    @Test
    void aBackgroundPreviewThatDoesNotAnswerAStopIsEnded() throws Exception {
        // a stand-in whose command line says it is a background live preview
        Process preview = new ProcessBuilder("bash", "-c", "sleep 60; : live-preview run --background").start();
        try {
            PreviewRegistry.put(entry(preview.pid(), "cli", "site"));
            LivePreviewStatus s = PreviewCommands.stop(root, null, p -> { });
            assertThat(s.state()).isEqualTo("STOPPED");
            assertThat(preview.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).as("its process was ended").isTrue();
            assertThat(PreviewRegistry.list(root)).isEmpty();
        } finally {
            preview.destroyForcibly();
        }
    }

    @Test
    void aProcessThatIsNotAPreviewIsNeverEnded() throws Exception {
        // Both of these turn on the editor being able to read another process's command line and see
        // that it is not a preview's (PreviewCommands.isPreview, which assumes "it is a preview" when
        // the line is unreadable, so as never to kill something it cannot identify). Windows does not
        // hand out another process's command line, so there is nothing to tell apart there.
        org.junit.jupiter.api.Assumptions.assumeTrue(other.info().commandLine().isPresent(),
                "this OS does not expose another process's command line");
        // an entry naming an unrelated process (its id reused, say): refused, and the process left alone
        PreviewRegistry.put(entry(other.pid(), "cli", "site"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PreviewCommands.stop(root, null, p -> { }))
                .hasMessageContaining("still running");
        assertThat(other.isAlive()).isTrue();
    }

    @Test
    void anotherKindOfOwnerIsAskedAndNotKilled() throws Exception {
        PreviewRegistry.put(entry(other.pid(), "editor", "site"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PreviewCommands.stop(root, "site", p -> { }))
                .hasMessageContaining("still running");
        assertThat(other.isAlive()).as("the editor is never ended from outside").isTrue();
        assertThat(Files.list(registry).map(p -> p.getFileName().toString()))
                .as("an unanswered request is withdrawn, so it cannot stop a later preview").noneMatch(n -> n.endsWith(".stop"));
    }

    @Test
    void theHeadlessServerOffersThePreviewTools() {
        assertThat(HeadlessExecutor.needsEditor(LivePreviewCommand.class)).isFalse();
        assertThat(HeadlessExecutor.needsEditor(LivePreviewStatusCommand.class)).isFalse();
    }
}
