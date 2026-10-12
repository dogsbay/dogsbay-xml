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

package com.dogsbay.dogsbayaieditor.plugin.dita;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.FormatCommand;
import com.dogsbay.dogsbayaieditor.commands.SchematronCommand;
import com.dogsbay.dogsbayaieditor.commands.ValidateCommand;

/**
 * Every template the editor offers has to be a document someone can build on.
 *
 * <p>A template is the one file nobody proofreads: it is written once, and from then on it is the thing a reader types
 * into. A misspelled element or a wrong public identifier surfaces as a validation error against work the reader
 * believes is theirs, in the first minute of using the editor.
 *
 * <p>Driven off the manifest rather than a list here, so a template added without being validated is not possible.
 */
class BuiltInTemplatesValidateTest {

    private static final String BUILTIN = "/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/";

    private static final Pattern TEMPLATE_SRC = Pattern.compile(
            "<template\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]+)\"");

    @TempDir Path dir;

    private static String read(String resource) throws Exception {
        try (InputStream in = BuiltInTemplatesValidateTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Not on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Every template the manifest declares. */
    private static List<String> templatesInManifest() throws Exception {
        List<String> found = new ArrayList<>();
        Matcher m = TEMPLATE_SRC.matcher(read(BUILTIN + "manifest.xml"));
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @TestFactory
    List<DynamicTest> everyTemplateInTheManifestIsValid() throws Exception {
        List<String> templates = templatesInManifest();
        assertThat(templates).as("the manifest should declare templates").isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (String src : templates) {
            tests.add(DynamicTest.dynamicTest(src, () -> {
                String xml = read(BUILTIN + src);
                // Written under the extension the template is for, because that is what decides whether the bundled
                // DITA catalog is consulted — and a template validated any other way is not validated as itself.
                Path file = dir.resolve(baseName(src) + extensionFor(xml));
                Files.writeString(file, xml);

                var result = new HeadlessExecutor().execute(new ValidateCommand(file));

                assertThat(result.valid())
                        .as("%s: %s", src, result.errors())
                        .isTrue();
            }));
        }
        return tests;
    }

    private static String baseName(String src) {
        String name = src.substring(src.lastIndexOf('/') + 1);
        return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    /** The extension a template's root element belongs under. */
    private static String extensionFor(String xml) {
        String root = rootOf(xml);
        return switch (root) {
            case "map", "bookmap", "subjectScheme" -> ".ditamap";
            case "val" -> ".ditaval";
            case "schema" -> ".sch";
            default -> ".dita";
        };
    }

    private static String rootOf(String xml) {
        Matcher doctype = Pattern.compile("<!DOCTYPE\\s+([A-Za-z_][\\w.:-]*)").matcher(xml);
        if (doctype.find()) {
            return doctype.group(1);
        }
        Matcher element = Pattern.compile("<([A-Za-z_][\\w.:-]*)")
                .matcher(xml.replaceAll("<\\?[^?]*\\?>", ""));
        return element.find() ? element.group(1) : "";
    }

    @TestFactory
    List<DynamicTest> everyTemplateIsShortEnoughToTypeAlongWith() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String src : templatesInManifest()) {
            if (src.endsWith("-template.xml")) {
                continue; // the annotated variants are meant to be long
            }
            tests.add(DynamicTest.dynamicTest(src, () -> {
                long lines = read(BUILTIN + src).lines().count();
                // A reader typing along should see a document they could have written, not placeholder to delete.
                // Troubleshooting is the longest because its body genuinely has that many required parts.
                assertThat(lines).as("%s is %d lines", src, lines).isLessThanOrEqualTo(26);
            }));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyTemplateIsAcceptedByTheToolThatConsumesIt() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String src : templatesInManifest()) {
            String xml = read(BUILTIN + src);
            String root = rootOf(xml);
            if (!"schema".equals(root) && !"bookmap".equals(root)) {
                continue; // the rest are DTD-validated above, which is the tool that consumes them
            }
            tests.add(DynamicTest.dynamicTest(src, () -> {
                if ("schema".equals(root)) {
                    // DTD validation says nothing about a Schematron schema, and the first version of this template
                    // had empty context and test attributes: they compile to <xsl:template match="">, so the schema
                    // did not merely report nothing, it failed to compile at all.
                    Path rules = dir.resolve("rules.sch");
                    Files.writeString(rules, xml);
                    Path topic = dir.resolve("t.dita");
                    Files.writeString(topic, read(BUILTIN + "templates/dita-concept-minimal.xml"));

                    var result = new HeadlessExecutor().execute(
                            new SchematronCommand(topic.toString(), rules.toString()));

                    assertThat(result.total()).as("the schema should have been applied").isEqualTo(1);
                } else {
                    // .bookmap is the extension the dialog offers a bookmap for, and it has to validate under it
                    Path file = dir.resolve("book.bookmap");
                    Files.writeString(file, xml);

                    var result = new HeadlessExecutor().execute(new ValidateCommand(file));

                    assertThat(result.valid()).as("%s as .bookmap: %s", src, result.errors()).isTrue();
                }
            }));
        }
        assertThat(tests).as("the Schematron and bookmap templates should both be covered").hasSize(2);
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyTemplateIsIndentedTheWayTheEditorIndents() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String src : templatesInManifest()) {
            if (src.endsWith("-template.xml")) {
                continue; // the annotated variants carry comments the formatter moves; not worth reflowing
            }
            tests.add(DynamicTest.dynamicTest(src, () -> {
                String xml = read(BUILTIN + src);
                Path file = dir.resolve(baseName(src) + extensionFor(xml));
                Files.writeString(file, xml);

                var result = new HeadlessExecutor().execute(new FormatCommand(file, null, null));

                // Not full house style: the formatter also collapses <shortdesc></shortdesc> to <shortdesc/>, and
                // these templates keep the open form on purpose, because that is where the writer types. What is
                // checked is everything else — indentation and the blank line after the prolog — so a file made from
                // one of our templates differs from house style in that one deliberate way and nothing more.
                assertThat(ignoringEmptyElementForm(result.content()))
                        .as("%s is not indented the way the editor indents", src)
                        .isEqualTo(ignoringEmptyElementForm(xml));
            }));
        }
        return tests;
    }

    /** The document with every empty element written the same way, so the two forms compare equal. */
    private static String ignoringEmptyElementForm(String xml) {
        return xml.replaceAll("<([A-Za-z_][\\w.:-]*)\\s*></\\1>", "<$1/>").stripTrailing();
    }

    @Test
    void everyTemplateHasItsOwnNameInTheManifest() throws Exception {
        Matcher m = Pattern.compile("<template\\b[^>]*\\bname\\s*=\\s*\"([^\"]+)\"").matcher(
                read(BUILTIN + "manifest.xml"));
        List<String> names = new ArrayList<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        // Built-in templates are de-duplicated by name when they are registered, so two templates sharing one would
        // leave the second silently unregistered.
        assertThat(names).hasSize(templatesInManifest().size()).doesNotHaveDuplicates();
    }
}
