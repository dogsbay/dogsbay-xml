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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ReportResult;
import com.dogsbay.dogsbayaieditor.graph.GraphIssue;
import com.dogsbay.dogsbayaieditor.graph.ProjectGraph;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.dogsbayaieditor.reports.ReportRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

/** project_graph through the executor: structure plus the content audits it folds in. */
class ProjectGraphCommandTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();

    /** A map whose topic is invalid, conrefs a missing id, and is otherwise well connected. */
    private static void project(Path dir) throws Exception {
        Files.writeString(dir.resolve("guide.ditamap"), """
                <map><title>Guide</title><topicref href="a.dita"/><topicref href="common.dita"/></map>""");
        // A DITA DOCTYPE, as real topics have: without one there is no grammar to break.
        Files.writeString(dir.resolve("a.dita"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="a"><title>A</title><body><p conref="common.dita#c/nope"/><bogus/></body></topic>""");
        Files.writeString(dir.resolve("common.dita"), """
                <topic id="c"><title>C</title><body><p id="yes">x</p></body></topic>""");
    }

    @Test
    void checksAddInvalidFilesAndBrokenElementIdsToTheIssues(@TempDir Path dir) throws Exception {
        project(dir);

        ProjectGraph graph = executor.execute(new ProjectGraphCommand(dir.toString(), "guide.ditamap", null, true));

        assertThat(graph.issues()).extracting(GraphIssue::rule).contains("invalid-dtd", "broken-element-id");
        assertThat(graph.issues()).filteredOn(i -> i.rule().equals("broken-element-id"))
                .singleElement().satisfies(i -> assertThat(i.file()).isEqualTo("a.dita"));
        assertThat(graph.issues()).filteredOn(i -> i.rule().equals("invalid-dtd"))
                .allSatisfy(i -> assertThat(i.file()).isEqualTo("a.dita"));
    }

    @Test
    void withoutChecksOnlyStructureIsJudged(@TempDir Path dir) throws Exception {
        project(dir);

        ProjectGraph graph = executor.execute(new ProjectGraphCommand(dir.toString(), "guide.ditamap", null, false));

        assertThat(graph.issues()).extracting(GraphIssue::rule).doesNotContain("invalid-dtd", "broken-element-id");
        assertThat(graph.edges()).isNotEmpty();
    }

    @Test
    void aKeyInATitleIsShownRatherThanDropped(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("guide.ditamap"), """
                <map><title>Guide</title><keydef keys="product"><topicmeta><keywords><keyword>Audacity</keyword></keywords></topicmeta></keydef>
                <topicref href="what.dita"/></map>""");
        Files.writeString(dir.resolve("what.dita"), """
                <topic id="w"><title>What is <keyword keyref="product"/>?</title><body/></topic>""");

        ProjectGraph graph = executor.execute(new ProjectGraphCommand(dir.toString(), "guide.ditamap", null, false));

        assertThat(graph.nodes()).filteredOn(n -> n.id().equals("what.dita"))
                .singleElement().satisfies(n -> assertThat(n.title()).isEqualTo("What is [product]?"));
    }

    @Test
    void anUnknownDeliverableIsAnArgumentError(@TempDir Path dir) throws Exception {
        project(dir);

        assertThatThrownBy(() -> executor.execute(new ProjectGraphCommand(dir.toString(), null, "nope", false)))
                .isInstanceOf(CommandException.class)
                .extracting("code").isEqualTo(CommandException.ErrorCode.INVALID_ARGUMENT);
    }

    @Test
    void theRelationshipMapIsRenderedFromTheGraph(@TempDir Path dir) throws Exception {
        project(dir);
        var params = new ObjectMapper().createObjectNode();
        params.put("root", dir.toString());
        params.put("output", "docs/relationship-map.html");
        params.put("source", "project_graph");
        params.putObject("args").put("map", "guide.ditamap");

        ReportResult r = (ReportResult) new JsonRpcHandler(executor).dispatch("render-report", params);

        assertThat(r.template()).isEqualTo("relationship-map");
        String html = Files.readString(dir.resolve("docs/relationship-map.html"));
        assertThat(html).contains("\"kind\":\"topicref\"").contains("broken-element-id");
        assertThat(ReportRenderer.externalReferences(html)).isEmpty();
    }
}
