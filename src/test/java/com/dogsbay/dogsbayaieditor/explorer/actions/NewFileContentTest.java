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
package com.dogsbay.dogsbayaieditor.explorer.actions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;


/** What goes in a file created from the project tree. */
class NewFileContentTest {

    // ── What goes in the file ────────────────────────────────────────

    @Test
    void theIdComesFromTheFileName() {
        String template = "<!DOCTYPE concept SYSTEM \"concept.dtd\">\n<concept id=\"concept_id\">\n<title>T</title>\n</concept>";

        assertThat(NewFileAction.withIdFrom(template, "what-is-audacity.dita"))
                .contains("<concept id=\"what-is-audacity\">")
                .doesNotContain("concept_id");
    }

    @Test
    void onlyTheRootElementsIdIsReplacedEvenWithNoDeclaration() {
        // pasted content often has neither declaration nor document type; looking past one that is not there used to land
        // inside the root's own start tag, so a child was renamed and the root kept its placeholder
        String pasted = "<concept id=\"concept_id\"><conbody><section id=\"keep-me\"/></conbody></concept>";

        String result = NewFileAction.withIdFrom(pasted, "install.dita");

        assertThat(result).contains("<concept id=\"install\">").contains("id=\"keep-me\"");
    }

    @Test
    void singleQuotesAreHandledTooAndChildrenAreStillLeftAlone() {
        String template = "<!DOCTYPE concept SYSTEM \"concept.dtd\">\n<concept id='concept_id'><image id=\"fig1\"/></concept>";

        String result = NewFileAction.withIdFrom(template, "install.dita");

        assertThat(result).contains("<concept id='install'>").contains("id=\"fig1\"");
    }

    @Test
    void aRootWithNoIdIsLeftAloneRatherThanHavingAChildRenamed() {
        String map = "<!DOCTYPE map SYSTEM \"map.dtd\">\n<map><topicref href=\"a.dita\" id=\"t1\"/></map>";

        assertThat(NewFileAction.withIdFrom(map, "book.ditamap")).isEqualTo(map);
    }

    @Test
    void aDocumentTypeWithAnInternalSubsetDoesNotConfuseIt() {
        String template = "<!DOCTYPE concept SYSTEM \"concept.dtd\" [<!ENTITY p \"Audacity\">]>\n<concept id=\"concept_id\"/>";

        assertThat(NewFileAction.withIdFrom(template, "install.dita")).contains("id=\"install\"");
    }

    @Test
    void everyShippedTemplateEitherTakesTheFileNameOrIsLeftAlone() throws Exception {
        // the strings above are hand-written; these are the files a writer actually gets. Not every template has a
        // root id — a map, a filter and a rule set do not — and for those the right answer is to change nothing,
        // which is what aRootWithNoIdIsLeftAloneRatherThanHavingAChildRenamed pins for the general case.
        for (Path file : shippedTemplates()) {
            String template = Files.readString(file);
            String result = NewFileAction.withIdFrom(template, "what-is-audacity.dita");
            if (hasRootId(template)) {
                assertThat(result).as(file.getFileName().toString())
                        .contains("id=\"what-is-audacity\"")
                        .doesNotContain("_id\"");
            } else {
                assertThat(result).as("%s has no root id, so nothing should change", file.getFileName())
                        .isEqualTo(template);
            }
        }
    }

    @Test
    void everyTopicTemplateHasARootIdToReplace() throws Exception {
        // the teeth of the test above: a topic whose id the file name cannot claim would leave every new file sharing
        // one id, which is invalid the moment two of them are in the same map
        for (Path file : shippedTemplates()) {
            String template = Files.readString(file);
            if (!isTopic(template)) {
                continue;
            }
            assertThat(hasRootId(template))
                    .as("%s is a topic template and needs a root id", file.getFileName())
                    .isTrue();
        }
    }

    private static java.util.List<Path> shippedTemplates() throws Exception {
        Path templates = Path.of("src/main/resources/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/templates");
        try (var files = Files.list(templates)) {
            return files.filter(f -> f.toString().endsWith(".xml")).sorted().toList();
        }
    }

    /** Whether the root element carries an id of its own. */
    private static boolean hasRootId(String template) {
        String root = afterDoctype(template);
        int close = root.indexOf('>');
        return close > 0 && root.substring(0, close).matches("(?s).*\\bid\\s*=.*");
    }

    /** Whether the template makes a topic — the documents whose id a file name should claim. */
    private static boolean isTopic(String template) {
        var m = java.util.regex.Pattern.compile("<!DOCTYPE\\s+([A-Za-z_][\\w.:-]*)").matcher(template);
        if (!m.find()) {
            return false; // no doctype: a DITAVAL or a Schematron schema, neither of which is a topic
        }
        return switch (m.group(1)) {
            case "topic", "concept", "task", "reference", "glossentry", "glossgroup", "troubleshooting" -> true;
            default -> false;
        };
    }

    /** The template from its root element onwards, so a DOCTYPE's own text is not mistaken for it. */
    private static String afterDoctype(String template) {
        int doctype = template.indexOf("<!DOCTYPE");
        if (doctype < 0) {
            int first = template.indexOf("?>");
            return first < 0 ? template : template.substring(first + 2);
        }
        int end = template.indexOf('>', doctype);
        // an internal subset ends at "]>", not at the first ">"
        int subset = template.indexOf('[', doctype);
        if (subset >= 0 && subset < end) {
            end = template.indexOf("]>", subset) + 1;
        }
        return end <= 0 ? template : template.substring(end + 1);
    }

    @Test
    void theMinimalTemplatesCarryAnEmptyShortDescription() throws Exception {
        // the tutorial requires a short description in every topic, and an empty element is a prompt to write one;
        // prose in its place was something to delete first, which is what made these templates unminimal
        Path templates = Path.of("src/main/resources/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/templates");
        for (String kind : new String[] { "concept", "task", "reference" }) {
            String text = Files.readString(templates.resolve("dita-" + kind + "-minimal.xml"));
            assertThat(text).as(kind).contains("<shortdesc></shortdesc>");
            assertThat(text).as(kind).doesNotContain("Brief description");
            // and it sits where the grammar wants it: after the title, before the body
            assertThat(text.indexOf("<shortdesc>")).isGreaterThan(text.indexOf("</title>"));
            assertThat(text.indexOf("<shortdesc>")).isLessThan(text.indexOf("body>"));
        }
    }

    @Test
    void anIdIsAnXmlNameWhateverTheFileIsCalled() {
        assertThat(NewFileAction.idFor("what is audacity.dita")).isEqualTo("what-is-audacity");
        assertThat(NewFileAction.idFor("01-getting-started.dita")).isEqualTo("_01-getting-started"); // may not start with a digit
        assertThat(NewFileAction.idFor("a (copy).dita")).isEqualTo("a-copy");
        assertThat(NewFileAction.idFor("no-extension")).isEqualTo("no-extension");
        assertThat(NewFileAction.idFor("...")).isNull();
    }

    @Test
    void aNameInAnyScriptStillGivesAnId() {
        // these used to give no id at all, so the file was created with the template's placeholder and collided silently
        assertThat(NewFileAction.idFor("\u914d\u7f6e.dita")).isEqualTo("\u914d\u7f6e");
        assertThat(NewFileAction.idFor("\u00dcberblick.dita")).isEqualTo("\u00dcberblick");
        assertThat(NewFileAction.idFor("caf\u00e9-guide.dita")).isEqualTo("caf\u00e9-guide");
    }
}
