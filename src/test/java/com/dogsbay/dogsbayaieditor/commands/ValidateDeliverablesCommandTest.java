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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport;

class ValidateDeliverablesCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();

    private void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    private static DeliverablesReport.Deliverable byName(DeliverablesReport r, String name) {
        return r.deliverables().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    private void twoDeliverables(Path dir, String apiTopics) throws Exception {
        write(dir, "guide.ditamap",
            "<map><topicref href=\"a.xml\"/><topicref href=\"bad.xml\"/></map>");
        write(dir, "api.ditamap", "<map>" + apiTopics + "</map>");
        write(dir, "a.xml", "<topic><title>A</title></topic>");
        write(dir, "bad.xml", "<topic><title>B</title>");          // not well-formed
        write(dir, "c.xml", "<topic><title>C</title></topic>");
        write(dir, "project.json", """
            { "deliverables": [
                { "name": "guide", "context": { "input": "guide.ditamap" } },
                { "name": "api",   "context": { "input": "api.ditamap" } } ] }
            """);
    }

    @Test
    void validatesEachDeliverablesPublicationSet(@TempDir Path dir) throws Exception {
        twoDeliverables(dir, "<topicref href=\"c.xml\"/>");

        DeliverablesReport r = executor.execute(new ValidateDeliverablesCommand(dir.toString()));

        assertThat(r.deliverables()).extracting(DeliverablesReport.Deliverable::name)
                .containsExactlyInAnyOrder("guide", "api");
        // guide pulls in the invalid bad.xml; api is clean
        assertThat(byName(r, "guide").failed()).isEqualTo(1);
        assertThat(byName(r, "api").failed()).isZero();
        assertThat(r.findings()).isNotEmpty()
                .allSatisfy(f -> assertThat(f.file()).endsWith("bad.xml"))
                .allSatisfy(f -> assertThat(f.deliverables()).containsExactly("guide"));
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void aFileBrokenInEveryDeliverableIsReportedOnceNamingThemAll(@TempDir Path dir) throws Exception {
        twoDeliverables(dir, "<topicref href=\"c.xml\"/><topicref href=\"bad.xml\"/>");

        DeliverablesReport r = executor.execute(new ValidateDeliverablesCommand(dir.toString()));

        assertThat(byName(r, "guide").failed()).isEqualTo(1);
        assertThat(byName(r, "api").failed()).isEqualTo(1);
        long distinct = r.findings().stream().map(f -> f.line() + ":" + f.column() + ":" + f.message()).distinct().count();
        assertThat(r.findings()).hasSize((int) distinct);
        assertThat(r.findings()).allSatisfy(f -> assertThat(f.deliverables()).containsExactly("guide", "api"));
    }

    @Test
    void emptyWhenNoProjectFileOrRootMap(@TempDir Path dir) throws Exception {
        write(dir, "lonely.xml", "<root/>");
        DeliverablesReport r = executor.execute(new ValidateDeliverablesCommand(dir.toString()));
        assertThat(r.deliverables()).isEmpty();
        assertThat(r.isClean()).isTrue();
    }
}
