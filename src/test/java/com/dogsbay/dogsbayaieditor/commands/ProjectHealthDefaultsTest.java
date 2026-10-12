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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.ProjectHealthReport;
import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/**
 * project_health finds what the project's own rules say is wrong (2026-09-18): the Audacity demo was
 * reported nearly clean while its house rules, keys, a conref push and an index redirect were broken.
 */
class ProjectHealthDefaultsTest {

    private static final String RULES = """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
              <pattern><rule context="topic"><assert test="shortdesc">Every topic needs a shortdesc.</assert></rule></pattern>
              <pattern><rule context="b"><report test="true()">Do not use b.</report></rule></pattern>
            </schema>
            """;

    @TempDir Path root;
    private final HeadlessExecutor executor = new HeadlessExecutor();

    private Path write(String name, String content) throws Exception {
        Path p = root.resolve(name);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, content);
    }

    @BeforeEach
    void project() throws Exception {
        root = root.toRealPath();
        write(".dogsbay/config.xml", """
                <dogsbay-project>
                  <project-type>DITA</project-type>
                  <default-root-map>guide.ditamap</default-root-map>
                  <default-schematron>rules/house.sch</default-schematron>
                  <format-style indent="spaces" size="2" newline="lf" final-newline="true"/>
                </dogsbay-project>
                """);
        write("rules/house.sch", RULES);
        write("guide.ditamap", """
                <map>
                  <keydef keys="product" href="a.dita"/>
                  <topicref href="a.dita"/>
                </map>
                """);
        write("a.dita", """
                <topic id="a">
                  <title>A</title>
                  <shortdesc>About A.</shortdesc>
                  <body>
                    <p>See <keyword keyref="prodcut"/>.</p>
                  </body>
                </topic>
                """);
    }

    private ProjectHealthReport health(String map, String schematron, List<String> include) throws Exception {
        return executor.execute(new ProjectHealthCommand(root.toString(), map, schematron, include, null, false));
    }

    @Test
    void usesTheProjectsRootMapForKeysAndItsHouseRulesAndSaysSo() throws Exception {
        write("b.dita", "<topic id=\"b\"><title>B</title><body><p>No shortdesc.</p></body></topic>\n");
        ProjectHealthReport r = health(null, null, List.of("reuse", "schematron"));

        assertThat(r.defaults().map()).isEqualTo("guide.ditamap");
        assertThat(r.defaults().mapSource()).isEqualTo(ProjectDefaults.CONFIG);
        assertThat(r.defaults().schematron()).isEqualTo(Path.of("rules/house.sch").toString());
        assertThat(r.defaults().schematronSource()).isEqualTo(ProjectDefaults.CONFIG);
        assertThat(r.reuse().undefinedKeys()).extracting("value").containsExactly("prodcut");
        assertThat(r.schematron().findings()).extracting("message").contains("Every topic needs a shortdesc.");
        assertThat(r.checked()).contains("schematron");
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void noneTurnsADefaultOffAndAProjectWithoutRulesSaysSo() throws Exception {
        ProjectHealthReport off = health("none", "none", List.of("reuse", "schematron", "validation"));
        assertThat(off.defaults().mapSource()).isEqualTo(ProjectDefaults.NONE);
        assertThat(off.defaults().schematronSource()).isEqualTo(ProjectDefaults.NONE);
        assertThat(off.reuse().undefinedKeys()).as("no map, no key analysis").isEmpty();
        assertThat(off.checked()).doesNotContain("schematron");

        Files.delete(root.resolve(".dogsbay/config.xml"));
        ProjectHealthReport bare = health(null, null, List.of("reuse", "validation"));
        assertThat(bare.defaults().mapSource()).isEqualTo(ProjectDefaults.NOT_CONFIGURED);
        assertThat(bare.defaults().schematronSource()).isEqualTo(ProjectDefaults.NOT_CONFIGURED);
    }

    @Test
    void reportsConrefPushesIndexRedirectsFormattingAndLeftovers() throws Exception {
        write("push.dita", "<topic id=\"push\"><title>P</title><shortdesc>P.</shortdesc><body>"
                + "<p conaction=\"pushbefore\">orphan push</p></body></topic>\n");
        write("index.dita", "<topic id=\"i\"><title>I</title><shortdesc>I.</shortdesc><body>"
                + "<p><indexterm>levels<index-see>nowhere</index-see></indexterm></p></body></topic>\n");
        write("crlf.dita", "<topic id=\"c\">\r\n  <title>C</title>\r\n  <shortdesc>C.</shortdesc>\r\n  <body>\r\n"
                + "    <p>Text.</p>\r\n  </body>\r\n</topic>\r\n");
        write("todo.dita", """
                <task id="t">
                  <title>T</title>
                  <shortdesc>T.</shortdesc>
                  <taskbody>
                    <context><p>Context.</p><draft-comment>Check the numbers.</draft-comment></context>
                    <!-- TODO: write the steps -->
                  </taskbody>
                </task>
                """);
        ProjectHealthReport r = health(null, "none", null);

        assertThat(r.conrefPush()).isNotEmpty();
        assertThat(r.danglingIndex()).extracting("target").containsExactly("nowhere");
        assertThat(r.unformatted()).anyMatch(f -> f.endsWith("crlf.dita"));
        assertThat(r.markers()).extracting("kind").contains("todo-comment", "draft-comment", "task-without-steps");
        assertThat(r.checked()).contains("conrefPush", "index", "format", "markers");
        assertThat(r.isClean()).isFalse();

        ProjectHealthReport errors = executor.execute(new ProjectHealthCommand(root.toString(), null, "none",
                null, "error", false));
        assertThat(errors.unformatted()).as("warnings are dropped with severity=error").isEmpty();
        assertThat(errors.markers()).isEmpty();
    }

    @Test
    void findingsOnlyOnTheDeletedSideOfProposalsArePendingFixesNotErrors() throws Exception {
        // An agent proposed the fixes; the writer has not accepted them yet
        write("a.dita", """
                <topic id="a">
                  <title>A</title>
                  <shortdesc>About A.</shortdesc>
                  <body>
                    <p>See <keyword keyref="prodcut" status="deleted" rev="ai:claude" otherprops="review-deleted"/><keyword keyref="product" status="new" rev="ai:claude"/>.</p>
                    <p><b status="deleted" rev="ai:claude" otherprops="review-deleted">Bold</b></p>
                    <p><xref href="gone.dita" status="deleted" rev="ai:claude" otherprops="review-deleted"/><xref href="a.dita" status="new" rev="ai:claude"/></p>
                  </body>
                </topic>
                """);
        ProjectHealthReport r = health(null, null, List.of("reuse", "schematron", "proposals"));

        assertThat(r.reuse().undefinedKeys()).isEmpty();
        assertThat(r.reuse().brokenReferences()).isEmpty();
        assertThat(r.schematron().findings()).isEmpty();
        assertThat(r.pendingFixes()).extracting("leg").contains("keys", "reuse", "schematron");
        assertThat(r.openProposals()).hasSize(1);
        assertThat(r.isReviewed()).isFalse();
    }

    @Test
    void theFormatterAppliesTheLineEndingsTheProjectNames() throws Exception {
        // In house style apart from its line endings
        Path crlf = write("crlf.dita", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n\r\n<topic id=\"c\">\r\n"
                + "  <title>C</title>\r\n</topic>\r\n");
        var result = executor.execute(new FormatCommand(crlf, null, null));
        assertThat(result.modified()).isTrue();
        assertThat(result.content()).doesNotContain("\r");

        Files.delete(root.resolve(".dogsbay/config.xml"));
        write(".dogsbay/config.xml", "<dogsbay-project><format-style indent=\"spaces\" size=\"2\"/></dogsbay-project>");
        assertThat(executor.execute(new FormatCommand(crlf, null, null)).modified())
                .as("a project that names no line endings keeps the file's").isFalse();
    }

    @Test
    void theProjectConfigKeepsItsHouseRules() throws Exception {
        DogsbayProjectConfig config = DogsbayProjectConfig.load(root);
        assertThat(config.getDefaultSchematron()).isEqualTo("rules/house.sch");
        config.setDefaultSchematron("other.sch");
        config.saveShared(root);
        assertThat(DogsbayProjectConfig.load(root).getDefaultSchematron()).isEqualTo("other.sch");
        assertThat(DogsbayProjectConfig.load(root).getDefaultRootMap()).isEqualTo("guide.ditamap");
        assertThat(com.dogsbay.dogsbayaieditor.commands.results.ProjectInfo.of("p", root.toString(), "DITA", null,
                true, null).houseRules()).isEqualTo("other.sch");
    }

    @Test
    void toolRepliesSayWhatHappened() throws Exception {
        Path a = root.resolve("a.dita");
        var same = executor.execute(new EditDocumentCommand(a, "About A.", "About A.", false));
        assertThat(same.message()).startsWith("Nothing changed");

        var handler = new com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler(executor);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Object dry = handler.dispatch("metadata-set", mapper.readTree("""
                {"root": "%s", "field": "keyword", "value": "alpha", "dryRun": true}
                """.formatted(root.toString().replace("\\", "\\\\"))));
        assertThat(mapper.valueToTree(dry).path("written").asBoolean()).isFalse();
        assertThat(mapper.valueToTree(dry).path("message").asText()).startsWith("Dry run, nothing written");
        Object real = handler.dispatch("metadata-set", mapper.readTree("""
                {"root": "%s", "field": "keyword", "value": "alpha"}
                """.formatted(root.toString().replace("\\", "\\\\"))));
        assertThat(mapper.valueToTree(real).path("written").asBoolean()).isTrue();
        assertThat(Files.readString(a)).contains("<keyword>alpha</keyword>");
    }
    // ── from the review (2026-09-18): the text that ships must never hide a real error ──

    @Test
    void aRealErrorInAFileWithAnUnrelatedProposalIsStillAnError() throws Exception {
        write("a.dita", """
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="a"><title>A</title><shortdesc>A.</shortdesc><body><title>Not here</title>
                <p status="new" rev="ai:claude">Added.</p></body></topic>
                """);
        ProjectHealthReport r = health(null, "none", List.of("validation"));
        assertThat(r.validation().failed()).isEqualTo(1);
        assertThat(r.pendingFixes()).isEmpty();
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void anErrorThatAcceptingWouldIntroduceIsReported() throws Exception {
        write("a.dita", """
                <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
                <topic id="a"><title status="deleted" rev="ai:claude" otherprops="review-deleted">A</title>
                <shortdesc>A.</shortdesc><body><p>Text.</p></body></topic>
                """);
        ProjectHealthReport r = health(null, "none", List.of("validation"));
        assertThat(r.validation().failed()).as("the shipped topic has no title").isEqualTo(1);
        assertThat(r.isClean()).isFalse();
    }

    @Test
    void aBrokenLinkIsPendingOnlyWhenItsElementIsDeleted() throws Exception {
        write("a.dita", """
                <topic id="a"><title>A</title><shortdesc>A.</shortdesc><body>
                <p><xref href="x&amp;y.dita"/><ph status="new" rev="ai:claude">added</ph></p></body></topic>
                """);
        ProjectHealthReport r = health(null, "none", List.of("reuse"));
        assertThat(r.reuse().brokenReferences()).extracting("value").containsExactly("x&y.dita");
        assertThat(r.pendingFixes()).isEmpty();
    }

    @Test
    void theShippedTextIsValidatedWhereTheFileIs() throws Exception {
        write("dtd/mini.dtd", "<!ELEMENT doc (title)><!ELEMENT title (#PCDATA)>"
                + "<!ATTLIST title status CDATA #IMPLIED rev CDATA #IMPLIED otherprops CDATA #IMPLIED>");
        write("docs/d.dita", """
                <!DOCTYPE doc SYSTEM "../dtd/mini.dtd">
                <doc><title>T</title><title status="new" rev="ai:claude">Second</title></doc>
                """);
        ProjectHealthReport r = health(null, "none", List.of("validation"));
        assertThat(r.validation().failed()).as("two titles, whether accepted or not").isEqualTo(1);
        assertThat(r.pendingFixes()).isEmpty();
    }

    @Test
    void houseRulesTheProjectNamesButDoesNotHaveAreAProblem() throws Exception {
        Files.delete(root.resolve("rules/house.sch"));
        ProjectHealthReport r = health(null, null, List.of("schematron"));
        assertThat(r.defaults().schematronSource()).isEqualTo(ProjectDefaults.MISSING);
        assertThat(r.schematron().findings()).extracting("message").anyMatch(m -> m.toString().contains("do not exist"));
        assertThat(r.isClean()).isFalse();
    }
}
