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
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.dogsbayaieditor.reports.ReportRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** render_report end to end: the RPC path runs the source, fills the template, writes one offline file. */
class RenderReportCommandTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final JsonRpcHandler handler = new JsonRpcHandler(new HeadlessExecutor());

    private static ObjectNode params(Path root, String output) {
        ObjectNode p = M.createObjectNode();
        p.put("root", root.toString());
        p.put("output", output);
        return p;
    }

    @Test
    void aSourceToolFillsItsDefaultTemplate(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.dita"),
                "<topic id=\"a\"><title>A</title><body><p><xref href=\"missing.dita\"/></p></body></topic>");
        ObjectNode p = params(root, "reports/health.html");
        p.put("source", "project_health");

        ReportResult r = (ReportResult) handler.dispatch("render-report", p);

        Path page = root.resolve("reports/health.html");
        assertThat(r.output()).isEqualTo(page.toString());
        assertThat(r.template()).isEqualTo("health");
        assertThat(r.source()).startsWith("project-health");
        String html = Files.readString(page);
        assertThat(html).contains("missing.dita").contains("id=\"report-data\"");
        assertThat(ReportRenderer.externalReferences(html)).isEmpty();
        assertThat(r.bytes()).isEqualTo(html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void dataCanBeGivenDirectly(@TempDir Path root) throws Exception {
        ObjectNode p = params(root, root.resolve("page.html").toString());
        p.put("template", "health");
        p.put("data", "{\"clean\":true,\"sections\":[]}");

        ReportResult r = (ReportResult) handler.dispatch("render-report", p);

        assertThat(r.source()).isNull();
        assertThat(Files.readString(root.resolve("page.html"))).contains("\"clean\":true");
    }

    @Test
    void aSourceThatWritesIsRefused(@TempDir Path root) {
        ObjectNode p = params(root, "page.html");
        p.put("source", "rename_file");

        assertThatThrownBy(() -> handler.dispatch("render-report", p))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void nothingToRenderIsRefused(@TempDir Path root) {
        assertThatThrownBy(() -> handler.dispatch("render-report", params(root, "page.html")))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("source");
    }

    @Test
    void anUnknownTemplateNamesTheBuiltInOnes(@TempDir Path root) {
        ObjectNode p = params(root, "page.html");
        p.put("template", "sunburst");
        p.put("data", "{}");

        assertThatThrownBy(() -> handler.dispatch("render-report", p))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("relationship-map");
    }

    @Test
    void aProjectTemplateIsUsed(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve(".dogsbay/reports"));
        Files.writeString(root.resolve(".dogsbay/reports/mine.html"), """
                <!doctype html><html><head><title>Mine</title><!--@kit:base--></head>
                <body><script id="report-data" type="application/json"></script></body></html>
                """);
        ObjectNode p = params(root, "out/mine.html");
        p.put("template", ".dogsbay/reports/mine.html");
        p.put("data", "{\"x\":1}");

        handler.dispatch("render-report", p);

        assertThat(Files.readString(root.resolve("out/mine.html"))).contains("<title>Mine</title>").contains("{\"x\":1}");
    }
}
