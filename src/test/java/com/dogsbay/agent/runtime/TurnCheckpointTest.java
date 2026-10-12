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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TurnCheckpointTest {

    @Test
    @DisplayName("revert restores a modified file to its pre-turn content")
    void revertRestoresModifiedFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.dita");
        Files.writeString(file, "ORIGINAL");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(file);
        Files.writeString(file, "CHANGED BY AGENT");   // tool mutates it

        assertThat(cp.hasChanges()).isTrue();
        assertThat(cp.revert()).containsExactly(file);
        assertThat(Files.readString(file)).isEqualTo("ORIGINAL");
    }

    @Test
    @DisplayName("changedFiles lists what a turn touched, so open buffers can be refreshed")
    void changedFilesDrivesTheBufferRefresh(@TempDir Path dir) throws Exception {
        // The agent writes to disk. AgentChatController reads this list on TurnEnd
        // and asks the host to refresh open buffers — without it the editor kept
        // showing pre-edit content while the file underneath had changed.
        Path one = dir.resolve("a.dita");
        Path two = dir.resolve("b.dita");
        Files.writeString(one, "A");
        Files.writeString(two, "B");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(one);
        Files.writeString(one, "A EDITED");
        cp.capture(two);
        Files.writeString(two, "B EDITED");

        assertThat(cp.changedFiles()).containsExactly(one, two);
    }

    @Test
    @DisplayName("changedFiles is a superset: it includes files a denied edit left alone")
    void changedFilesIncludesUntouchedCaptures(@TempDir Path dir) throws Exception {
        // capture() runs before the tool does, so a denied or no-op edit still
        // registers. The refresh path must therefore not assume every entry
        // differs on disk — it delegates to the editor's own external-change
        // check, which compares content before reloading anything.
        Path file = dir.resolve("untouched.dita");
        Files.writeString(file, "SAME");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(file);   // tool asked, then wrote nothing

        assertThat(cp.changedFiles()).containsExactly(file);
        assertThat(cp.revert()).isEmpty();   // revert filters by real difference
    }

    @Test
    @DisplayName("revert deletes a file the turn created")
    void revertDeletesCreatedFile(@TempDir Path dir) throws Exception {
        Path created = dir.resolve("new.dita");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(created);                 // captured before it exists → creation
        Files.writeString(created, "made by agent");

        assertThat(cp.revert()).containsExactly(created);
        assertThat(Files.exists(created)).isFalse();
    }

    @Test
    @DisplayName("copy-on-write keeps the FIRST baseline across multiple edits in a turn")
    void copyOnWriteKeepsFirstBaseline(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, "v0");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(file);
        Files.writeString(file, "v1");
        cp.capture(file);                    // second capture must NOT overwrite the baseline
        Files.writeString(file, "v2");

        cp.revert();
        assertThat(Files.readString(file)).isEqualTo("v0");
    }

    @Test
    @DisplayName("beginTurn discards the previous turn's captures")
    void beginTurnResets(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, "first-turn");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(file);
        cp.beginTurn();                      // new turn — earlier capture is gone

        assertThat(cp.hasChanges()).isFalse();
        Files.writeString(file, "untouched-by-revert");
        assertThat(cp.revert()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("untouched-by-revert");
    }

    @Test
    @DisplayName("capture before beginTurn is a no-op")
    void noCaptureBeforeBeginTurn(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, "x");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.capture(file);   // no turn started yet
        assertThat(cp.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("revert skips a captured file whose content never changed")
    void revertSkipsUnchangedFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, "same");
        TurnCheckpoint cp = new TurnCheckpoint(dir.resolve(".cp"));

        cp.beginTurn();
        cp.capture(file);            // captured, but the tool was denied / read-only...
        // ...so the file content is unchanged on disk
        assertThat(cp.revert()).isEmpty();   // nothing actually restored
    }

    @Test
    @DisplayName("backup directory is cleaned up after revert")
    void cleansUpBackups(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, "v0");
        Path cpDir = dir.resolve(".cp");
        TurnCheckpoint cp = new TurnCheckpoint(cpDir);

        cp.beginTurn();
        cp.capture(file);
        Files.writeString(file, "v1");
        cp.revert();

        assertThat(Files.exists(cpDir.resolve("turn-1"))).isFalse();   // .bak copies removed
    }

    /**
     * The backups are copies of the reader's own files, replaced every turn, so
     * git has no business showing them — five untracked file-0.bak in the
     * commit dialog is noise where noise is expensive.
     */
    @Test
    void theCheckpointsExcludeThemselvesFromVersionControl(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("topic.dita"), "<topic/>");
        Path checkpoints = dir.resolve(".xagent").resolve("checkpoints");
        var checkpoint = new TurnCheckpoint(checkpoints);

        checkpoint.beginTurn();
        checkpoint.capture(file);

        assertThat(checkpoints.resolve(".gitignore")).exists();
        assertThat(Files.readString(checkpoints.resolve(".gitignore")).strip()).isEqualTo("*");
        // The ignore sits with the checkpoints, not over .xagent, which also
        // holds configuration a team may want to commit.
        assertThat(dir.resolve(".xagent").resolve(".gitignore")).doesNotExist();
    }

    @Test
    void aProjectFromAnEarlierBuildIsHealed(@TempDir Path dir) throws Exception {
        // The folder is already there, from a build that wrote no ignore.
        Path checkpoints = Files.createDirectories(dir.resolve(".xagent").resolve("checkpoints"));
        Path file = Files.writeString(dir.resolve("topic.dita"), "<topic/>");

        var checkpoint = new TurnCheckpoint(checkpoints);
        checkpoint.beginTurn();
        checkpoint.capture(file);

        assertThat(checkpoints.resolve(".gitignore")).exists();
    }
}
