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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** What review_revert may restore exactly. */
class ProposalSnapshotsTest {

    private final ProposalSnapshots snapshots = new ProposalSnapshots();
    private final Path file = Path.of("/p/topics/install.dita");

    @Test
    void restoresTheDocumentBeforeTheSessionsFirstProposal() {
        snapshots.recordWrite("ai:a", file, "v0", "v1-marked", false);
        snapshots.recordWrite("ai:a", file, "v1-marked", "v2-marked", true);
        assertThat(snapshots.restorable("ai:a", file, "v2-marked")).contains("v0");
        assertThat(snapshots.restorable("ai:b", file, "v2-marked")).as("another session's").isEmpty();
    }

    @Test
    void restoresNothingOnceTheDocumentChangedUnderTheSession() {
        snapshots.recordWrite("ai:a", file, "v0", "v1-marked", false);
        assertThat(snapshots.restorable("ai:a", file, "v1-marked, then edited by the writer")).isEmpty();
        snapshots.recordWrite("ai:a", file, "edited by the writer", "v2-marked", true);
        assertThat(snapshots.restorable("ai:a", file, "v2-marked")).as("started from a text it had not seen").isEmpty();
    }

    @Test
    void restoresPlainEditsToo() {
        // proposals off, or marks that would add an error: the edit leaves nothing to reject (2026-09-18)
        snapshots.recordWrite("ai:a", file, "v0", "v1-plain", false);
        snapshots.recordWrite("ai:a", file, "v1-plain", "v2-marked", false);
        assertThat(snapshots.restorable("ai:a", file, "v2-marked")).contains("v0");
        snapshots.recordWrite("ai:b", file, "w0", "w1-plain", false);
        assertThat(snapshots.restorable("ai:b", file, "w1-plain")).contains("w0");
        assertThat(snapshots.restorable("ai:b", file, "w1-plain, then edited by the writer")).isEmpty();
    }

    @Test
    void forgetsAfterARevert() {
        snapshots.recordWrite("ai:a", file, "v0", "v1-marked", false);
        snapshots.forget("ai:a", file);
        assertThat(snapshots.restorable("ai:a", file, "v1-marked")).isEmpty();
    }
}
