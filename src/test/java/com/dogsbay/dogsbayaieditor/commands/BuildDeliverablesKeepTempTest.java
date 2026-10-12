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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;
import com.dogsbay.xml.dita.DitaOtMessage;

/**
 * Deliverable builds with the bundled DITA-OT: a deliverable's {@code clean.temp=no}
 * keeps the temporary files in the project, replacing the previous build's, and the
 * command's {@code keepTemp} overrides the param.
 */
class BuildDeliverablesKeepTempTest {

    @TempDir static Path engine;
    private static File home;

    @TempDir Path root;

    @BeforeAll
    static void extractEngine() throws Exception {
        assumeTrue(DefaultDitaOtFramework.isBundled(), "the bundled DITA-OT framework is not on the classpath");
        home = DefaultDitaOtFramework.extractBundled(engine.toFile());
    }

    private void writeProject() throws Exception {
        Files.createDirectories(root.resolve("topics"));
        Files.writeString(root.resolve("guide.ditamap"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd">
                <map><title>Guide</title><topicref href="topics/t.dita"/></map>
                """);
        Files.writeString(root.resolve("topics/t.dita"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="t"><title>T</title><body><p>Text.</p></body></topic>
                """);
        Files.writeString(root.resolve("project.json"), """
                { "deliverables": [ { "name": "full",
                    "context": { "input": "guide.ditamap" },
                    "publication": { "transtype": "html5",
                      "params": [ { "name": "clean.temp", "value": "no" } ] } } ] }
                """);
    }

    private DeliverableBuild build(Boolean keepTemp) throws Exception {
        List<DeliverableBuild> results = new HeadlessExecutor().execute(
                new BuildDeliverablesCommand(root.toString(), null, null, home.getPath(), null, keepTemp));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).messages()).noneMatch(DitaOtMessage::isError);
        return results.get(0);
    }

    @Test
    void keepsTemporaryFilesInTheProjectReplacingThePreviousBuild() throws Exception {
        writeProject();
        Path kept = DitaOtTemp.dirFor(root, "full");
        Files.createDirectories(kept);
        Files.writeString(kept.resolve("stale.txt"), "from an earlier build");

        DeliverableBuild build = build(null);

        assertThat(build.success()).isTrue();
        assertThat(build.tempDir()).isEqualTo(kept.toString());
        assertThat(kept.resolve(".job.xml")).exists();
        assertThat(kept.resolve("stale.txt")).as("replaced, not accumulated").doesNotExist();
        assertThat(DitaOtTemp.folder(root).resolve(".gitignore")).exists();
    }

    @Test
    void keepTempFalseOverridesTheDeliverableParam() throws Exception {
        writeProject();

        DeliverableBuild build = build(false);

        assertThat(build.success()).isTrue();
        assertThat(build.tempDir()).isNull();
        assertThat(DitaOtTemp.dirFor(root, "full")).doesNotExist();
    }
}
