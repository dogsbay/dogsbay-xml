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
package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TranscriptExportTest {

    @Test
    void markdownHasTheFactsThenTheTranscript() {
        String md = TranscriptExport.markdown("Fix shortdescs", "Claude Agent", "T1", "/p/demo",
                Instant.parse("2026-09-06T10:00:00Z"), "\nYou: hi\n\nAgent: hello\n");
        assertThat(md).startsWith("# Fix shortdescs\n\n- Agent: Claude Agent\n- Tier: T1\n- Project: /p/demo\n- Exported: 2026-09-06 ");
        assertThat(md).endsWith("---\n\nYou: hi\n\nAgent: hello\n");
        assertThat(TranscriptExport.markdown(null, "Codex", null, null, Instant.now(), "")).startsWith("# Codex\n");
    }

    @Test
    void fileNamesAreSafeAndDated() {
        Instant at = Instant.parse("2026-09-06T10:00:00Z");
        assertThat(TranscriptExport.fileName("Fix: the shortdescs / round 2", at)).matches("Fix-the-shortdescs-round-2-2026-09-0[56]\\.md");
        assertThat(TranscriptExport.fileName("", at)).matches("agent-session-2026-09-0[56]\\.md");
    }
}
