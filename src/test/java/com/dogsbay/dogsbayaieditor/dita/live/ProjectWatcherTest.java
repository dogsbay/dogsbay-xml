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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The project watcher that triggers a live preview's republish. */
class ProjectWatcherTest {

    @TempDir Path dir;

    private ProjectWatcher watcher;
    private final AtomicInteger changes = new AtomicInteger();

    @AfterEach
    void close() {
        if (watcher != null) {
            watcher.close();
        }
    }

    private void start(Path excludedFolder) throws Exception {
        start(excludedFolder, 300);
    }

    private void start(Path excludedFolder, long debounceMillis) throws Exception {
        watcher = new ProjectWatcher(dir, p -> excludedFolder != null && p.toAbsolutePath().normalize().startsWith(excludedFolder),
                debounceMillis, changes::incrementAndGet);
        watcher.start();
    }

    /** Wait long enough for the debounce and a slow file system watcher (polling on some platforms). */
    private static void settle() throws InterruptedException {
        Thread.sleep(2500);
    }

    /**
     * Five files written back to back arrive as one change, not five.
     *
     * <p>The debounce here is longer than the rest of the class uses, and the reason is the
     * watcher underneath. On Linux {@code WatchService} is inotify and the five events arrive
     * within milliseconds of the writes. macOS has no such kernel interface, so the JDK falls
     * back to {@code PollingWatchService}, which compares directory listings on a timer: the
     * five writes are seen at the next poll, together — unless the burst happens to straddle a
     * poll boundary, in which case the watcher reports them in two instalments seconds apart,
     * each one its own debounce window and so its own change. That is what failed this test on
     * the macOS runner on 2026-10-13, having passed on the two runs before it.
     *
     * <p>Coalescing a burst is still the behaviour worth asserting; what the 300ms debounce
     * asserts on a polling file system is that the burst missed a poll boundary, which is luck.
     * So the window here is wider than any poll interval the JDK uses, which makes the
     * assertion exact on every platform rather than usually-exact on one of them. It does not
     * paper over a real defect: with a 300ms debounce on macOS, two republishes for one burst
     * is what a user would genuinely get.
     */
    @Test
    void reportsABurstOfChangesOnceAfterItSettles() throws Exception {
        Files.createDirectories(dir.resolve("topics"));
        start(null, 3000);
        for (int i = 0; i < 5; i++) {
            Files.writeString(dir.resolve("topics/t" + i + ".dita"), "<topic/>");
        }
        Thread.sleep(8000);
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void ignoresTheOutputFolderGitEditorStateAndDependencies() throws Exception {
        Path out = dir.resolve("out/site");
        for (String folder : new String[] { "out/site/content", ".git", ".dogsbay/build-times", "node_modules/x" }) {
            Files.createDirectories(dir.resolve(folder));
        }
        start(out);
        Files.writeString(out.resolve("content/page.md"), "# Page");
        Files.writeString(dir.resolve(".git/index"), "x");
        Files.writeString(dir.resolve(".dogsbay/build-times/builds.jsonl"), "{}");
        Files.writeString(dir.resolve("node_modules/x/index.js"), "x");
        Files.writeString(dir.resolve("guide.ditamap~"), "backup");
        settle();
        assertThat(changes.get()).isZero();

        Files.writeString(dir.resolve("guide.ditamap"), "<map/>");
        settle();
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void anEmptyFolderIsNotAChangeButAFolderOfTopicsIs() throws Exception {
        // out/ is made on another preview's first publish; it holds no source (2026-09-19)
        start(dir.resolve("out/site"));
        Files.createDirectories(dir.resolve("out/site/content"));
        settle();
        assertThat(changes.get()).isZero();

        Path moved = Files.createDirectories(dir.resolve("incoming/topics"));
        Files.writeString(moved.resolve("a.dita"), "<topic/>");
        Files.move(dir.resolve("incoming/topics"), dir.resolve("topics"));
        settle();
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void watchesFoldersCreatedAfterItStarted() throws Exception {
        start(null);
        Files.createDirectories(dir.resolve("new/deeper"));
        settle();
        changes.set(0);
        Files.writeString(dir.resolve("new/deeper/topic.dita"), "<topic/>");
        settle();
        assertThat(changes.get()).isEqualTo(1);
    }
}
