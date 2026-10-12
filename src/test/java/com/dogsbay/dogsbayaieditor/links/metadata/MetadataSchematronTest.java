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

package com.dogsbay.dogsbayaieditor.links.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.ExportMetadataSchematronCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule.Presence;
import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/** M3b: policy → Schematron export. */
class MetadataSchematronTest {

    @TempDir Path dir;

    private MetadataPolicy policy() {
        return new MetadataPolicy(List.of(
                new MetadataRule("task", MetadataField.CREATED, Presence.REQUIRED,
                        List.of(), "\\d{4}-\\d\\d-\\d\\d"),
                new MetadataRule("concept", MetadataField.AUDIENCE, Presence.REQUIRED,
                        List.of("administrator", "user"), null),
                new MetadataRule("reference", MetadataField.AUTHOR, Presence.FORBIDDEN,
                        List.of(), null),
                new MetadataRule(null, MetadataField.KEYWORD, Presence.RECOMMENDED,
                        List.of(), null)));
    }

    @Test
    void generatesWellFormedSchematronWithExpectedChecks() throws Exception {
        String sch = policy().toSchematron();

        // well-formed XML, ISO Schematron namespace
        var doc = new SAXReader().read(new java.io.ByteArrayInputStream(
                sch.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(doc.getRootElement().getName()).isEqualTo("schema");
        assertThat(doc.getRootElement().getNamespaceURI())
            .isEqualTo("http://purl.oclc.org/dsdl/schematron");

        // contexts: typed by element name + the untyped /* root
        assertThat(sch).contains("context=\"task\"").contains("context=\"concept\"")
            .contains("context=\"reference\"").contains("context=\"/*\"");
        // required → assert on presence; forbidden → report; recommended → role warning
        assertThat(sch).contains("<assert").contains("<report");
        assertThat(sch).contains("role=\"warning\"");
        // value + pattern checks present
        assertThat(sch).contains("'administrator','user'");
        assertThat(sch).contains("matches(");
    }

    @Test
    void exportCommandReturnsAndWritesFromConfigPolicy() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.setMetadataPolicy(policy());
        c.saveShared(dir);

        Path out = dir.resolve("policy.sch");
        String sch = new HeadlessExecutor().execute(new ExportMetadataSchematronCommand(
                dir.toString(), null, out.toString()));

        assertThat(sch).contains("<schema");
        assertThat(java.nio.file.Files.readString(out)).isEqualTo(sch);
    }

    @Test
    void everyTestExpressionHasBalancedBracketsAndParens() throws Exception {
        // Catches the missing-']' class of XPath bug that still parses as XML.
        var doc = new SAXReader().read(new java.io.ByteArrayInputStream(
                policy().toSchematron().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var asserts = doc.selectNodes("//*[local-name()='assert' or local-name()='report']");
        assertThat(asserts).isNotEmpty();
        for (Object node : asserts) {
            String test = ((org.dom4j.Element) node).attributeValue("test");
            assertThat(balanced(test, '(', ')')).as("parens in: " + test).isTrue();
            assertThat(balanced(test, '[', ']')).as("brackets in: " + test).isTrue();
        }
        // pattern checks are anchored to match the in-tool full-match semantics
        assertThat(policy().toSchematron()).contains("matches(").contains("^(").contains(")$");
    }

    private static boolean balanced(String s, char open, char close) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == open) {
                depth++;
            } else if (s.charAt(i) == close && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    @Test
    void multiWordAllowedValuesRoundTrip() {
        MetadataPolicy p = new MetadataPolicy(List.of(
                new MetadataRule("concept", MetadataField.CATEGORY, Presence.REQUIRED,
                        List.of("Getting Started", "Reference"), null)));
        MetadataPolicy reparsed = MetadataPolicy.parse(p.toElement());
        assertThat(reparsed.rules().get(0).allowedValues())
            .containsExactly("Getting Started", "Reference"); // not split on the space
    }

    @Test
    void emptyPolicyExportsEmptySchema() {
        String sch = MetadataPolicy.empty().toSchematron();
        assertThat(sch).contains("<schema").doesNotContain("<pattern");
    }
}
