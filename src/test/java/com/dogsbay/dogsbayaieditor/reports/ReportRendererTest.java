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

package com.dogsbay.dogsbayaieditor.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The report renderer embeds data safely, inlines kits and produces offline HTML. */
class ReportRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DATA_ELEMENT = Pattern.compile(
            "<script id=\"report-data\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Map<String, String> META =
            Map.of("generated", "2026-09-14T10:00:00Z", "editorVersion", "test", "source", "unit");

    private static final String MINIMAL = """
            <!doctype html><html><head><title>T</title><!--@kit:base--></head><body>
            <script id="report-data" type="application/json">
              placeholder
            </script></body></html>
            """;

    private static final String RELATIONSHIP_DATA = """
            {"generated":"2026-09-14T10:00:00Z","root":"guide.ditamap","scope":"All",
             "deliverables":[{"name":"web","map":"guide.ditamap","ditavals":["web.ditaval"],"ships":["guide.ditamap","a.dita"]}],
             "nodes":[{"id":"guide.ditamap","kind":"map","type":"map","title":"Guide","ships":["web"]},
                      {"id":"a.dita","kind":"topic","type":"concept","title":"A <b>&amp; co","ships":["web"]},
                      {"id":"b.dita","kind":"topic","type":"task","title":"B","missing":true},
                      {"id":"key:prod","kind":"key","title":"prod"},
                      {"id":"web.ditaval","kind":"ditaval","title":"web"}],
             "edges":[{"from":"guide.ditamap","to":"a.dita","kind":"topicref","line":3},
                      {"from":"a.dita","to":"b.dita","kind":"link","line":9,"broken":true},
                      {"from":"a.dita","to":"key:prod","kind":"keyref","line":4,"via":"prod"},
                      {"from":"guide.ditamap","to":"web.ditaval","kind":"ditavalref"}],
             "issues":[{"severity":"error","rule":"broken-link","file":"a.dita","line":9,"message":"b.dita not found"}]}
            """;

    private static final String HEALTH_DATA = """
            {"generated":"2026-09-14T10:00:00Z","root":"guide.ditamap","scope":"All","clean":false,
             "sections":[{"name":"Links","passed":10,"failed":1,"total":11,
                          "findings":[{"severity":"error","rule":"broken-link","file":"a.dita","line":9,"message":"missing"},
                                      {"severity":"warning","rule":"unused-key","message":"Unused key","count":2,"files":["x.dita","y.dita"]}]},
                         {"name":"Spelling","findings":[]}]}
            """;

    private static String dataElement(String html) {
        Matcher m = DATA_ELEMENT.matcher(html);
        assertThat(m.find()).as("data element present").isTrue();
        String text = m.group(1);
        assertThat(m.find()).as("data element appears once").isFalse();
        return text;
    }

    @Test
    void hostileStringsEmbedSafelyAndRoundTrip() throws Exception {
        ObjectNode original = MAPPER.createObjectNode();
        original.put("title", "</script><script>alert(1)</script> & <!-- \u2028 line \u2029 para");
        String json = MAPPER.writeValueAsString(original);

        String html = ReportRenderer.render(MINIMAL, json, META);
        String embedded = dataElement(html);

        assertThat(embedded.toLowerCase()).doesNotContain("</script").doesNotContain("<!--");
        assertThat(embedded).doesNotContain("\u2028").doesNotContain("\u2029").doesNotContain("<").doesNotContain(">");
        assertThat(MAPPER.readTree(embedded)).isEqualTo(original);
        assertThat(html).doesNotContain("placeholder");
    }

    @Test
    void metaSlotIsFilledWhenPresent() throws Exception {
        String template = MINIMAL.replace("</body>",
                "<script id=\"report-meta\" type=\"application/json\"></script></body>");
        String html = ReportRenderer.render(template, "{}", Map.of("source", "</script>"));
        Matcher m = Pattern.compile("<script id=\"report-meta\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL).matcher(html);
        assertThat(m.find()).isTrue();
        assertThat(MAPPER.readTree(m.group(1)).get("source").asText()).isEqualTo("</script>");
    }

    @Test
    void kitsAreInlined() {
        String html = ReportRenderer.render(MINIMAL, "{}", Map.of());
        assertThat(html).doesNotContain("<!--@kit:");
        assertThat(html).contains("<style>").contains("--bg:");
        assertThat(html).contains("window.Report");
    }

    @Test
    void unknownKitThrowsNamingIt() {
        String template = MINIMAL.replace("<!--@kit:base-->", "<!--@kit:nosuchkit-->");
        assertThatThrownBy(() -> ReportRenderer.render(template, "{}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nosuchkit");
    }

    @Test
    void missingDataSlotThrows() {
        String template = "<html><body><!--@kit:base--></body></html>";
        assertThatThrownBy(() -> ReportRenderer.render(template, "{}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("report-data");
    }

    @Test
    void duplicateDataSlotThrows() {
        String template = MINIMAL.replace("</body>",
                "<script id=\"report-data\" type=\"application/json\"></script></body>");
        assertThatThrownBy(() -> ReportRenderer.render(template, "{}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "not json", "{} {}", ""})
    void invalidJsonThrows(String json) {
        assertThatThrownBy(() -> ReportRenderer.render(MINIMAL, json, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void builtInTemplatesAreListedAndLoad() {
        assertThat(ReportRenderer.builtInTemplates()).contains("relationship-map", "health");
        for (String name : ReportRenderer.builtInTemplates()) {
            assertThat(ReportRenderer.loadTemplate(name, null)).contains("id=\"report-data\"");
        }
    }

    @Test
    void unknownTemplateNameThrowsListingBuiltIns() {
        assertThatThrownBy(() -> ReportRenderer.loadTemplate("nope", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope")
                .hasMessageContaining("relationship-map")
                .hasMessageContaining("health");
        assertThatThrownBy(() -> ReportRenderer.loadTemplate(".dogsbay/reports/missing.html", Path.of("/nonexistent")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationship-map");
    }

    @Test
    void projectTemplateLoadsFromRelativePath(@TempDir Path root) throws Exception {
        Path file = root.resolve(".dogsbay/reports/mine.html");
        Files.createDirectories(file.getParent());
        Files.writeString(file, MINIMAL, StandardCharsets.UTF_8);

        assertThat(ReportRenderer.loadTemplate(".dogsbay/reports/mine.html", root)).isEqualTo(MINIMAL);
        assertThat(ReportRenderer.loadTemplate(file.toString(), null)).isEqualTo(MINIMAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"relationship-map", "health"})
    void builtInTemplatesRenderOffline(String name) throws Exception {
        String data = name.equals("health") ? HEALTH_DATA : RELATIONSHIP_DATA;
        String html = ReportRenderer.render(ReportRenderer.loadTemplate(name, null), data, META);

        assertThat(ReportRenderer.externalReferences(html)).isEmpty();
        assertThat(MAPPER.readTree(dataElement(html))).isEqualTo(MAPPER.readTree(data));
        assertThat(html).doesNotContain("<!--@kit:").contains("<title>");
        Matcher htmlTag = Pattern.compile("<html\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        assertThat(htmlTag.find()).isTrue();
        assertThat(htmlTag.group()).doesNotContain("data-theme=");
    }

    @Test
    void externalReferencesDetectsNetworkUrlsButNotSvgNamespace() {
        String html = """
                <svg xmlns="http://www.w3.org/2000/svg"></svg>
                <script>document.createElementNS('http://www.w3.org/2000/svg','g')</script>
                <script src="https://cdn.example.com/x.js"></script>
                <link href='//fonts.example.com/f.css'>
                <style>@import "https://example.com/a.css"; .x { background: url(http://example.com/i.png) }</style>
                <a href="#local">x</a>
                """;
        assertThat(ReportRenderer.externalReferences(html)).containsExactlyInAnyOrder(
                "https://cdn.example.com/x.js", "//fonts.example.com/f.css",
                "https://example.com/a.css", "http://example.com/i.png");
    }

    @Test
    void writeCreatesParentDirectories(@TempDir Path root) throws Exception {
        Path out = root.resolve("a/b/c/report.html");
        ReportRenderer.write(out, "<p>héllo</p>");
        assertThat(Files.readString(out, StandardCharsets.UTF_8)).isEqualTo("<p>héllo</p>");
        ReportRenderer.write(out, "second");
        assertThat(Files.readString(out, StandardCharsets.UTF_8)).isEqualTo("second");
        try (var listing = Files.list(out.getParent())) {
            assertThat(listing).hasSize(1);
        }
    }
}
