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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a document was before an agent session's changes to it, so that taking them back
 * ({@link ReviewRevertCommand}) can restore it exactly: proposals, and plain edits too (proposals off, or
 * marks that would have added an error), which leave nothing to reject. Rejecting marks one by one cannot
 * always restore the text either: marks that read an inserted step as a wrapper around its sibling came back
 * rejected as an empty step with its command outside it (2026-09-18).
 *
 * <p>A snapshot is kept per session identity and file, in memory (so not across a restart), and only
 * restores while the file holds exactly what the session last wrote: then everything since the snapshot is
 * the session's own, and restoring loses nothing of anyone else's.
 */
public final class ProposalSnapshots {

    private record Snapshot(String before, String lastWritten) {
    }

    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    private static String key(String identity, Path file) {
        return identity + "\n" + WriteGate.real(file); // one key whichever path, or link, names the file
    }

    /**
     * Record an agent's write to a file, marked or not.
     *
     * @param before      the document before the write
     * @param after       the document the write left
     * @param hadOwnMarks whether {@code before} already held this session's proposals
     */
    public void recordWrite(String identity, Path file, String before, String after, boolean hadOwnMarks) {
        String key = key(identity, file);
        Snapshot current = snapshots.get(key);
        if (current != null && before.equals(current.lastWritten())) {
            snapshots.put(key, new Snapshot(current.before(), after));
        } else if (!hadOwnMarks) {
            // the first change against a document this session has not changed since: its text is the one to restore
            snapshots.put(key, new Snapshot(before, after));
        } else {
            // the document changed under us while holding our proposals: nothing exact to restore
            snapshots.remove(key);
        }
    }

    /** The document before this session's changes, when {@code current} is exactly what the session last wrote. */
    public Optional<String> restorable(String identity, Path file, String current) {
        Snapshot snapshot = snapshots.get(key(identity, file));
        if (snapshot == null || !snapshot.lastWritten().equals(current)) {
            return Optional.empty();
        }
        return Optional.of(snapshot.before());
    }

    public void forget(String identity, Path file) {
        snapshots.remove(key(identity, file));
    }
}
