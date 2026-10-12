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

package com.dogsbay.dogsbayaieditor.dita.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The registry's identity rules (review, 2026-09-18). */
class PreviewRegistryTest {

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
    }

    private PreviewRegistry.Entry entry(long pid, String processStart, String startedAt) {
        return new PreviewRegistry.Entry(root.toString(), "site", "cli", pid, null, "STARTING", "Starting", null, null,
                startedAt, null, null, processStart);
    }

    private String startOf(Process p) {
        return p.toHandle().info().startInstant().map(Object::toString).orElse(null);
    }

    @Test
    void aReusedProcessIdIsNotThePreviewsProcess() {
        assertThat(entry(other.pid(), startOf(other), "t1").alive()).isTrue();
        assertThat(entry(other.pid(), "2001-01-01T00:00:00Z", "t1").alive()).as("same id, another process").isFalse();
    }

    @Test
    void aDeliverableIsClaimedOnce() {
        assertThat(PreviewRegistry.claim(entry(other.pid(), startOf(other), "t1"))).isTrue();
        assertThat(PreviewRegistry.claim(entry(ProcessHandle.current().pid(), PreviewRegistry.processStart(), "t2")))
                .as("another live process has it").isFalse();
        other.destroyForcibly();
        other.toHandle().onExit().join();
        assertThat(PreviewRegistry.claim(entry(ProcessHandle.current().pid(), PreviewRegistry.processStart(), "t2")))
                .as("a stale claim is taken over").isTrue();
    }

    @Test
    void aStopRequestForAnEarlierPreviewIsDropped() {
        PreviewRegistry.requestStop(entry(ProcessHandle.current().pid(), PreviewRegistry.processStart(), "earlier"));
        assertThat(PreviewRegistry.takeStopRequest(root, "site", "later")).isFalse();
        assertThat(PreviewRegistry.takeStopRequest(root, "site", "earlier")).as("and it is gone").isFalse();
        PreviewRegistry.requestStop(entry(ProcessHandle.current().pid(), PreviewRegistry.processStart(), "now"));
        assertThat(PreviewRegistry.takeStopRequest(root, "site", "now")).isTrue();
    }
}
