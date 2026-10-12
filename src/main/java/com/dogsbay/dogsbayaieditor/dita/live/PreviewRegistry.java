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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dogsbay.xml.properties.SettingsHome;

/**
 * The live previews running on this machine, whichever process runs them (the editor, the headless MCP
 * server, a background preview from the command line): one JSON file per preview in
 * {@code ~/.dogsbay/previews}. The process that runs a preview writes its entry on every change and removes
 * it when the preview stops; an entry whose process is gone is stale and is removed when read. Any process
 * can ask for a preview to stop by writing a stop file beside its entry, which the owning process picks up.
 * It is kept in the user's home, not the project, so nothing machine-local lands in the repository.
 */
public final class PreviewRegistry {

    /**
     * One running preview.
     *
     * @param root        the project root (canonical)
     * @param deliverable the deliverable previewed
     * @param owner       who runs it: {@code editor}, {@code mcp} or {@code cli}
     * @param pid         the process that runs it
     * @param url         the dev server's address, once running
     * @param state       the preview's state (STARTING … READY_WITH_ERRORS, FAILED)
     * @param message     what it is doing, or why it failed
     * @param outputDir   the published site's folder, once published
     * @param log         the background process's log file, or null
     * @param startedAt   when it started (ISO-8601)
     * @param errors      the last publish's errors, or null before one finished
     * @param warnings    the last publish's warnings, or null
     * @param processStart when the owning process started (with its id, what identifies it)
     */
    public record Entry(String root, String deliverable, String owner, long pid, String url, String state,
            String message, String outputDir, String log, String startedAt, Integer errors, Integer warnings,
            String processStart) {

        /**
         * Whether the process that runs it is still running: the same process id, started at the same time (a
         * process id reused by another program after a crash is not the preview's).
         */
        public boolean alive() {
            return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).map(h -> processStart == null
                    || h.info().startInstant().map(i -> i.toString().equals(processStart)).orElse(true))
                    .orElse(false);
        }

        /** Whether this process runs it. */
        public boolean ours() {
            return pid == ProcessHandle.current().pid();
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** When this process started: with its id, what identifies it. */
    public static String processStart() {
        return ProcessHandle.current().info().startInstant().map(Object::toString).orElse(null);
    }
    private static volatile String owner = "cli";
    private static volatile String log;

    private PreviewRegistry() {
    }

    /** Who this process is, for the entries it writes: {@code editor}, {@code mcp} or {@code cli}. */
    public static void setOwner(String name) {
        owner = name;
    }

    public static String owner() {
        return owner;
    }

    /** The log file of this process, for a preview running in the background. */
    public static void setLog(String file) {
        log = file;
    }

    public static String log() {
        return log;
    }

    /** Where entries are kept; {@code dogsbay.previews.dir} overrides it (tests). */
    public static Path dir() {
        String override = System.getProperty("dogsbay.previews.dir");
        return override != null ? Path.of(override) : SettingsHome.dir().resolve("previews");
    }

    /** The registry file of a project's deliverable (its name; the log beside it takes the same stem). */
    public static Path file(Path root, String deliverable) {
        String r = LivePreview.canonical(root).toString();
        String hash;
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(r.getBytes(StandardCharsets.UTF_8));
            hash = java.util.HexFormat.of().formatHex(d).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            hash = Integer.toHexString(r.hashCode());
        }
        return dir().resolve(hash + "-" + shortHash(deliverable) + "-" + deliverable.replaceAll("[^A-Za-z0-9._-]", "_")
                + ".json"); // hashed too: "a b" and "a_b", or "Guide" and "guide" on some file systems, are not one file
    }

    private static String shortHash(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static Path stopFile(Path entry) {
        return entry.resolveSibling(entry.getFileName().toString().replaceFirst("\\.json$", ".stop"));
    }

    /** The registry folder, created readable by this user only where the file system allows. */
    private static Path ensureDir() throws IOException {
        Path d = dir();
        if (!Files.isDirectory(d)) {
            Files.createDirectories(d);
            try {
                Files.setPosixFilePermissions(d, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException | IOException notPosix) {
                // Windows: the user's profile is private already
            }
        }
        return d;
    }

    /** Record (or update) a preview this process runs. */
    public static void put(Entry entry) {
        Path tmp = null;
        try {
            Path d = ensureDir();
            Path target = file(Path.of(entry.root()), entry.deliverable());
            tmp = Files.createTempFile(d, ".entry", ".tmp");
            Files.writeString(tmp, JSON.writeValueAsString(entry));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tmp = null;
        } catch (IOException e) {
            // the registry is how others see a preview, not how it runs: a preview keeps running without it
        } finally {
            if (tmp != null) {
                deleteQuietly(tmp);
            }
        }
    }

    /**
     * Claim a project's deliverable for this process before starting its preview, so two processes starting
     * it at once do not both build into one output folder: the entry is created only if there is none (a
     * stale one, whose process is gone, is cleared first). False when another live process has it.
     */
    public static boolean claim(Entry entry) {
        Path target = file(Path.of(entry.root()), entry.deliverable());
        try {
            ensureDir();
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    Files.writeString(target, JSON.writeValueAsString(entry), java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE);
                    deleteQuietly(stopFile(target)); // a request left for an earlier preview is not for this one
                    return true;
                } catch (java.nio.file.FileAlreadyExistsException taken) {
                    Optional<Entry> held = read(target);
                    if (held.isPresent() && held.get().alive() && !held.get().ours()) {
                        return false;
                    }
                    deleteIfUnchanged(target, held.orElse(null));
                }
            }
        } catch (IOException e) {
            return true; // no registry to claim in: the preview runs, unseen by other processes
        }
        return false;
    }

    /** Forget a preview this process ran (another process's entry is left alone). */
    public static void remove(Path root, String deliverable) {
        Path f = file(root, deliverable);
        read(f).filter(Entry::ours).ifPresent(e -> {
            deleteIfUnchanged(f, e);
            deleteQuietly(stopFile(f));
        });
    }

    /**
     * Delete an entry only if it still is the one read ({@code seen}; null for an unreadable one): another
     * process may have replaced a stale entry with its own meanwhile, which must stay.
     */
    private static void deleteIfUnchanged(Path f, Entry seen) {
        Optional<Entry> now = read(f);
        boolean same = seen == null ? now.isEmpty()
                : now.isPresent() && now.get().pid() == seen.pid() && java.util.Objects.equals(now.get().startedAt(),
                        seen.startedAt());
        if (same) {
            deleteQuietly(f);
        }
    }

    /** The running previews of a project (all projects when {@code root} is null); stale entries are removed. */
    public static List<Entry> list(Path root) {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) {
            return out;
        }
        String r = root == null ? null : LivePreview.canonical(root).toString();
        try (var files = Files.list(dir())) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Optional<Entry> e = read(f);
                if (e.isEmpty()) {
                    // unreadable (being written, or from another version): stale once it is an hour old
                    try {
                        if (Files.getLastModifiedTime(f).toMillis() < System.currentTimeMillis() - 3_600_000L) {
                            deleteIfUnchanged(f, null);
                        }
                    } catch (IOException ignore) {
                        // gone meanwhile
                    }
                    continue;
                }
                if (!e.get().alive()) {
                    deleteIfUnchanged(f, e.get());
                    if (!Files.exists(f)) {
                        deleteQuietly(stopFile(f));
                    }
                    continue;
                }
                if (r == null || r.equals(e.get().root())) {
                    out.add(e.get());
                }
            }
        } catch (IOException e) {
            // an unreadable folder lists nothing
        }
        return out;
    }

    /** The running preview of a project's deliverable, if any process runs it. */
    public static Optional<Entry> find(Path root, String deliverable) {
        return list(root).stream().filter(e -> e.deliverable().equals(deliverable)).findFirst();
    }

    /** Ask the process that runs {@code entry}'s preview to stop it: that preview, not a later one. */
    public static void requestStop(Entry entry) {
        try {
            ensureDir();
            Files.writeString(stopFile(file(Path.of(entry.root()), entry.deliverable())),
                    entry.pid() + "\n" + entry.startedAt());
        } catch (IOException e) {
            // nothing to ask with: the caller reports the preview still running
        }
    }

    /** Withdraw a stop request that was not taken (its preview ended some other way). */
    public static void withdrawStop(Path root, String deliverable) {
        deleteQuietly(stopFile(file(root, deliverable)));
    }

    /**
     * Whether someone asked for this process's preview (started at {@code startedAt}) to stop; the request is
     * taken. A request for another preview of the deliverable (an earlier one) is dropped.
     */
    static boolean takeStopRequest(Path root, String deliverable, String startedAt) {
        Path stop = stopFile(file(root, deliverable));
        if (!Files.exists(stop)) {
            return false;
        }
        String request;
        try {
            request = Files.readString(stop);
        } catch (IOException e) {
            return false;
        }
        deleteQuietly(stop);
        return request.equals(ProcessHandle.current().pid() + "\n" + startedAt);
    }

    private static Optional<Entry> read(Path f) {
        try {
            return Optional.of(JSON.readValue(Files.readString(f), Entry.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // left for the next reader to clean up
        }
    }
}
