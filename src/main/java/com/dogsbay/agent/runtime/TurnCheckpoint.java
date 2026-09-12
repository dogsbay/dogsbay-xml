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

package com.dogsbay.agent.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-turn checkpoint of files an agent turn changes, so a whole turn can be
 * reverted (plans/agent-turn-checkpoint-undo.md).
 *
 * <p>Copy-on-write: the first time a file is captured in a turn, its current
 * bytes are copied aside; a file that doesn't exist yet is recorded as a
 * <em>creation</em> (revert deletes it). Capturing the same file again in the
 * same turn is a no-op, so the stored baseline is always the true pre-turn state.
 * {@link #beginTurn()} discards the previous turn's captures.
 *
 * <p>Pure file I/O — no editor dependencies; the host handles reloading open
 * editors after a revert.
 */
public final class TurnCheckpoint {

    private final Path baseDir;
    private long turn = 0;
    private Path turnDir;

    // absolute file -> its saved baseline copy (creations live in `created` instead)
    private final Map<Path, Path> baselines = new LinkedHashMap<>();
    private final Set<Path> created = new LinkedHashSet<>();
    private final List<Path> order = new ArrayList<>();

    public TurnCheckpoint(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** Start a fresh turn, discarding any previous turn's captures (and its backups). */
    public synchronized void beginTurn() {
        deleteTurnDir();   // remove the previous turn's .bak copies so they don't accumulate
        turn++;
        turnDir = baseDir.resolve("turn-" + turn);
        baselines.clear();
        created.clear();
        order.clear();
    }

    /** Capture a file's pre-mutation state (copy-on-write; the first capture wins). */
    public synchronized void capture(Path file) {
        if (file == null || turnDir == null) {
            return;
        }
        Path key = file.toAbsolutePath().normalize();
        if (baselines.containsKey(key) || created.contains(key)) {
            return;   // already captured this turn — keep the original baseline
        }
        try {
            if (Files.exists(key)) {
                createTurnDir();
                Path backup = turnDir.resolve("file-" + order.size() + ".bak");
                Files.copy(key, backup, StandardCopyOption.COPY_ATTRIBUTES);
                baselines.put(key, backup);
            } else {
                created.add(key);   // the tool is about to create it → revert deletes it
            }
            order.add(key);
        } catch (IOException e) {
            // capture is best-effort: a file we couldn't back up just isn't revertible
        }
    }

    /**
     * Create this turn's directory, excluding the checkpoints from version
     * control the first time.
     *
     * <p>These are copies of the reader's own files, made so the last turn can
     * be undone, and they are replaced on the next turn. Without this they show
     * up as a handful of untracked {@code file-0.bak} in every commit dialog —
     * noise in the one place noise is expensive.
     *
     * <p>The ignore goes in the checkpoints folder rather than in
     * {@code .xagent/}, which also holds configuration such as {@code mcp.json}
     * that a team may well want to commit.
     */
    private void createTurnDir() throws IOException {
        Files.createDirectories(turnDir);
        // Checked every turn rather than only at creation, so a project that
        // already has a checkpoints folder from an earlier build is healed too.
        Path ignore = baseDir.resolve(".gitignore");
        if (!Files.exists(ignore)) {
            Files.writeString(ignore, "*\n", java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Files captured this turn, in capture order. */
    public synchronized List<Path> changedFiles() {
        return new ArrayList<>(order);
    }

    public synchronized boolean hasChanges() {
        return !order.isEmpty();
    }

    /**
     * Restore every file whose content <em>actually changed</em> this turn to its
     * pre-turn state, delete files the turn created, then clear the turn.
     *
     * <p>Files whose on-disk content is unchanged from the baseline are skipped — so
     * a denied edit, a read-only tool that merely referenced a path, or an
     * in-buffer-only edit (which never touched disk) is correctly a no-op rather
     * than a spurious "restored" that would touch mtimes or clobber the buffer.
     *
     * @return the files that were restored or deleted
     */
    public synchronized List<Path> revert() {
        List<Path> reverted = new ArrayList<>();
        for (Map.Entry<Path, Path> e : baselines.entrySet()) {
            Path target = e.getKey();
            Path backup = e.getValue();
            try {
                if (!Files.exists(target) || Files.mismatch(target, backup) != -1) {
                    Files.copy(backup, target,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    reverted.add(target);
                }
            } catch (IOException ex) {
                // leave files we can't restore as-is rather than failing the whole revert
            }
        }
        for (Path c : created) {
            try {
                if (Files.deleteIfExists(c)) {
                    reverted.add(c);
                }
            } catch (IOException ex) {
                // best-effort delete
            }
        }
        deleteTurnDir();
        baselines.clear();
        created.clear();
        order.clear();
        return reverted;
    }

    /** Recursively delete this turn's backup directory (best-effort). */
    private void deleteTurnDir() {
        if (turnDir == null || !Files.exists(turnDir)) {
            return;
        }
        try (var paths = Files.walk(turnDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignore) {
            // best-effort cleanup
        }
    }
}
