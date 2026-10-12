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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.dita.BuildStep;

/** Build step times as text and as a per-project JSON Lines record. */
class BuildTimesTest {

    @TempDir Path dir;

    private static DeliverableBuild build() {
        return new DeliverableBuild("site", "dogsbay-site", "/out/site", true, List.of(), null, List.of(
                new BuildStep("Initialization", 40),
                new BuildStep("Preprocessing", 12_100, List.of(
                        new BuildStep("map-reader", 3_000), new BuildStep("topic-conref", 9_095), new BuildStep("preprocess2", 5))),
                new BuildStep("Converting to dogsbay Markdown", 125_000)), "DITA-OT 4.3.5 (Java)");
    }

    @Test
    void formatsStepsWithSlowStagesFirstAndATotal() {
        String report = BuildStepReport.format(build(), "  ");
        assertThat(report).isEqualTo(String.join("\n",
                "  Initialization                              40 ms",
                "  Preprocessing                              12.1 s",
                "    topic-conref                              9.1 s",
                "    map-reader                                3.0 s",
                "  Converting to dogsbay Markdown          2 min 5 s",
                "  Total (DITA-OT 4.3.5 (Java))            2 min 17 s", ""));
        assertThat(BuildStepReport.format(new DeliverableBuild("x", "html5", "o", false, List.of()), "  ")).isEmpty();
    }

    @Test
    void recordsEachBuildAsAJsonLineInAGitignoredFolder() throws Exception {
        BuildTimes.append(dir, build());
        BuildTimes.append(dir, build());
        BuildTimes.append(dir, new DeliverableBuild("skipped", "html5", "o", false, List.of()));

        List<String> lines = Files.readAllLines(BuildTimes.file(dir));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("{\"time\":\"")
                .contains("\"deliverable\":\"site\",\"transtype\":\"dogsbay-site\",\"engine\":\"DITA-OT 4.3.5 (Java)\",\"success\":true,\"totalMillis\":137140")
                .contains("{\"name\":\"Preprocessing\",\"millis\":12100,\"stages\":[{\"name\":\"map-reader\",\"millis\":3000}");
        assertThat(Files.readString(BuildTimes.folder(dir).resolve(".gitignore"))).isEqualTo("*\n");
        assertThat(BuildTimes.line(build(), Instant.parse("2026-09-17T10:00:00Z"))).startsWith("{\"time\":\"2026-09-17T10:00:00Z\"");
    }
}
