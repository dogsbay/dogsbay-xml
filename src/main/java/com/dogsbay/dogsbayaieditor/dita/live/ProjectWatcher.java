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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a project folder and its subfolders for file changes, and calls back once a
 * burst of changes has settled (a save that writes several files, a Git checkout).
 * Folders created later are watched too. Excluded paths (the preview's own output, Git
 * and editor metadata, dependencies) are neither watched nor reported.
 */
public final class ProjectWatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectWatcher.class);

    /** Folder names never watched: version control, editor state, dependencies. */
    public static final Set<String> SKIPPED_FOLDERS = Set.of(".git", ".svn", ".hg", ".dogsbay", ".idea", ".vscode", "node_modules");

    private final Path root;
    private final Predicate<Path> excluded;
    private final long debounceMillis;
    private final Runnable onChange;
    private final Map<WatchKey, Path> folders = new HashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "project-watcher-debounce"));
    private WatchService service;
    private Thread loop;
    private ScheduledFuture<?> pending;

    /**
     * @param root           the project folder
     * @param excluded       paths (folders or files) to ignore, besides {@link #SKIPPED_FOLDERS}
     * @param debounceMillis quiet time after the last change before {@code onChange} runs
     * @param onChange       called on the watcher's timer thread
     */
    public ProjectWatcher(Path root, Predicate<Path> excluded, long debounceMillis, Runnable onChange) {
        this.root = root.toAbsolutePath().normalize();
        this.excluded = excluded == null ? p -> false : excluded;
        this.debounceMillis = debounceMillis;
        this.onChange = onChange;
    }

    /** Start watching. */
    public synchronized void start() throws IOException {
        service = FileSystems.getDefault().newWatchService();
        register(root);
        loop = daemon(this::run, "project-watcher");
        loop.start();
    }

    /** Whether a path is ignored: under a skipped folder, excluded, or an editor's temporary file. */
    boolean ignored(Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            if (SKIPPED_FOLDERS.contains(part.toString())) {
                return true;
            }
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return excluded.test(path) || name.endsWith("~") || name.endsWith(".swp") || name.startsWith(".#");
    }

    private void register(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && ignored(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                WatchKey key = dir.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                synchronized (folders) {
                    folders.put(key, dir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void run() {
        try {
            while (true) {
                WatchKey key = service.take();
                Path dir;
                synchronized (folders) {
                    dir = folders.get(key);
                }
                boolean relevant = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        relevant = true;
                        continue;
                    }
                    if (dir == null) {
                        continue;
                    }
                    Path changed = dir.resolve((Path) event.context());
                    if (ignored(changed)) {
                        continue;
                    }
                    if (Files.isDirectory(changed)) {
                        // A folder is a change only for the files it brings: a folder made to hold another
                        // preview's output (out/, created on its first publish) is not, while a folder of
                        // topics moved into the project is. Changes inside a folder come from its own watch.
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            try {
                                register(changed);
                            } catch (IOException e) {
                                LOG.debug("Could not watch new folder {}: {}", changed, e.getMessage());
                            }
                            relevant |= holdsSources(changed);
                        }
                        continue;
                    }
                    relevant = true;
                }
                if (!key.reset()) {
                    synchronized (folders) {
                        folders.remove(key);
                    }
                }
                if (relevant) {
                    schedule();
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed
        }
    }

    /** Whether a folder holds any file that is not ignored, outside ignored folders. */
    private boolean holdsSources(Path folder) {
        boolean[] found = {false};
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(folder) && ignored(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!ignored(file)) {
                        found[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return true; // cannot tell: publish again rather than miss a change
        }
        return found[0];
    }

    private synchronized void schedule() {
        if (pending != null) {
            pending.cancel(false);
        }
        pending = timer.schedule(() -> {
            try {
                onChange.run();
            } catch (RuntimeException e) {
                LOG.warn("Project change handler failed: {}", e.getMessage(), e);
            }
        }, debounceMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        try {
            if (service != null) {
                service.close();
            }
        } catch (IOException e) {
            // closing
        }
        timer.shutdownNow();
        if (loop != null) {
            loop.interrupt();
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
