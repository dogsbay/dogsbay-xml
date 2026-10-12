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

package com.dogsbay.dogsbayaieditor.commands.results;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.dita.live.LivePreview;
import com.dogsbay.xml.dita.BuildStep;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.xml.dita.DitaOtMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** What an agent sees when it polls a live preview. */
class LivePreviewStatusTest {

    @Test
    void summarizesAPublishWithItsFirstErrors() {
        List<DitaOtMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            messages.add(new DitaOtMessage("DOTJ012F", "ERROR", "error " + i, "t.dita", i, 1));
        }
        messages.add(new DitaOtMessage("DOTX029W", "WARN", "a warning", null, -1, -1));
        DeliverableBuild build = new DeliverableBuild("site", "dogsbay", "/out", false, messages, null,
                List.of(new BuildStep("Preprocessing", 1200), new BuildStep("Converting", 300)), null);

        LivePreviewStatus.PublishSummary summary = LivePreviewStatus.PublishSummary.of(build);

        assertThat(summary.success()).isFalse();
        assertThat(summary.errors()).isEqualTo(7);
        assertThat(summary.warnings()).isEqualTo(1);
        assertThat(summary.totalMillis()).isEqualTo(1500);
        assertThat(summary.firstErrors()).containsExactly("error 1", "error 2", "error 3", "error 4", "error 5");
    }

    @Test
    void describesAPreviewBeforeItsFirstPublishAndOneNotRunning() throws Exception {
        Deliverable site = new Deliverable("site", Path.of("guide.ditamap"), List.of(), "dogsbay-site", Path.of("out"),
                List.of(), null, "site");
        LivePreviewStatus fresh = LivePreviewStatus.of(new LivePreview(Path.of("."), site, null));
        assertThat(fresh.deliverable()).isEqualTo("site");
        assertThat(fresh.state()).isEqualTo("STARTING");
        assertThat(fresh.url()).isNull();
        assertThat(fresh.outputDir()).isNull();
        assertThat(fresh.lastPublish()).isNull();
        assertThat(fresh.recentOutput()).isEmpty();

        LivePreviewStatus stopped = LivePreviewStatus.stopped("site");
        assertThat(stopped.state()).isEqualTo("STOPPED");
        JsonNode json = new ObjectMapper().valueToTree(stopped);
        assertThat(json.get("message").asText()).isEqualTo("No live preview is running");
        assertThat(json.has("recentOutput")).isTrue();
    }
}
