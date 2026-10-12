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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.dogsbay.dogsbayaieditor.commands.WriteGate;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The editor running on this machine, as seen from outside it (its discovery file): which documents it
 * holds with unsaved changes, so that a change made without it does not go under the writer's work.
 */
final class RunningEditor {

    private RunningEditor() {
    }

    /** How long one answer is trusted: the guard and the write each ask, within one command. */
    private static final long FRESH_MS = 2000;
    private static volatile long askedAt;
    private static volatile Set<Path> answer = Set.of();

    /**
     * The real paths of the documents the running editor holds with unsaved changes; empty when no editor
     * runs (no discovery file, or nothing listening). An editor that runs but does not answer is not "no
     * unsaved work": that throws, so the change is refused rather than written under a tab (review,
     * 2026-09-18: it failed open).
     */
    static Set<Path> unsavedFiles() {
        long now = System.currentTimeMillis();
        if (now - askedAt < FRESH_MS) {
            return answer;
        }
        EditorClient client = EditorClient.connect();
        Set<Path> out = new HashSet<>();
        if (client != null) {
            try {
                for (JsonNode d : client.call("listDocuments", null, java.time.Duration.ofSeconds(5))) {
                    if (d.path("modified").asBoolean(false) && d.hasNonNull("file")) {
                        String file = d.get("file").asText();
                        out.add(WriteGate.real(file.startsWith("file:") ? Path.of(java.net.URI.create(file))
                                : Path.of(file)));
                    }
                }
            } catch (java.net.ConnectException gone) {
                // a discovery file left by an editor that is no longer running
            } catch (Exception e) {
                throw new IllegalStateException("The editor is running but did not say which documents have unsaved "
                        + "changes (" + e.getMessage() + "), so nothing was written; try again, or make the change "
                        + "through the editor");
            }
        }
        answer = Set.copyOf(out);
        askedAt = now;
        return answer;
    }

    /** Whether the running editor holds {@code file} with unsaved changes. */
    static boolean holdsUnsaved(Path file) {
        return unsavedFiles().contains(WriteGate.real(file));
    }
}
