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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Every grammar type the profile declares has to be a type the editor can load.
 *
 * <p>A type is what turns an open document into an editing surface: the fragment shortcuts, the outline, and what
 * validation runs. A template without one gives a reader a correct document and then no help at all with it, which is
 * the state the subject scheme, glossary group, troubleshooting, DITAVAL and Schematron templates shipped in.
 *
 * <p>Driven off the manifest, so a type cannot be declared without being checked, nor a template shipped for a root
 * that no type claims.
 */
class BuiltInTypesTest {

    private static final String BUILTIN = "/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/";

    private static final Pattern TYPE_SRC = Pattern.compile("<type\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TEMPLATE_SRC = Pattern.compile("<template\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ROOT_NAME = Pattern.compile("<root\\b[^>]*\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern EXTENSIONS = Pattern.compile("<type\\b[^>]*\\bextensions\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern FRAGMENT = Pattern.compile("<fragment\\b[^>]*\\border\\s*=\\s*\"(\\d+)\"");
    private static final Pattern FRAGMENT_KEY = Pattern.compile("<fragment\\b[^>]*\\bkey\\s*=\\s*\"([^\"]*)\"");

    private static String read(String resource) throws Exception {
        try (InputStream in = BuiltInTypesTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Not on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> declared(Pattern pattern) throws Exception {
        List<String> found = new ArrayList<>();
        Matcher m = pattern.matcher(read(BUILTIN + "manifest.xml"));
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static String first(Pattern pattern, String xml) {
        Matcher m = pattern.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    /** The root element a template makes, from its DOCTYPE or its first element. */
    private static String rootOfTemplate(String xml) {
        Matcher doctype = Pattern.compile("<!DOCTYPE\\s+([A-Za-z_][\\w.:-]*)").matcher(xml);
        if (doctype.find()) {
            return doctype.group(1);
        }
        Matcher element = Pattern.compile("<([A-Za-z_][\\w.:-]*)").matcher(xml.replaceAll("<\\?[^?]*\\?>", ""));
        return element.find() ? element.group(1) : "";
    }

    @TestFactory
    List<DynamicTest> everyTypeIsWellFormedAndNamesItsRoot() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String src : declared(TYPE_SRC)) {
            tests.add(DynamicTest.dynamicTest(src, () -> {
                String xml = read(BUILTIN + src);
                javax.xml.parsers.DocumentBuilderFactory factory =
                        javax.xml.parsers.DocumentBuilderFactory.newInstance();
                com.dogsbay.xml.SecureXml.hardenDom(factory, true);
                factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));

                assertThat(first(ROOT_NAME, xml)).as("%s must name its root element", src).isNotBlank();
                assertThat(first(EXTENSIONS, xml)).as("%s must claim at least one extension", src).isNotBlank();
            }));
        }
        assertThat(tests).as("the manifest should declare types").isNotEmpty();
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyTypesFragmentsAreDistinctlyOrderedAndKeyed() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (String src : declared(TYPE_SRC)) {
            tests.add(DynamicTest.dynamicTest(src, () -> {
                String xml = read(BUILTIN + src);
                List<String> orders = new ArrayList<>();
                Matcher m = FRAGMENT.matcher(xml);
                while (m.find()) {
                    orders.add(m.group(1));
                }
                List<String> keys = new ArrayList<>();
                Matcher k = FRAGMENT_KEY.matcher(xml);
                while (k.find()) {
                    if (!k.group(1).isBlank()) {
                        keys.add(k.group(1));
                    }
                }
                // A repeated order makes the menu's sequence undefined; a repeated key means one shortcut inserts
                // whichever fragment the editor happened to bind last.
                assertThat(orders).as("%s fragment orders", src).doesNotHaveDuplicates();
                assertThat(keys).as("%s fragment shortcuts", src).doesNotHaveDuplicates();
            }));
        }
        return tests;
    }

    @Test
    void everyTemplateHasATypeForTheDocumentItMakes() throws Exception {
        List<String> roots = new ArrayList<>();
        for (String src : declared(TYPE_SRC)) {
            roots.add(first(ROOT_NAME, read(BUILTIN + src)));
        }
        for (String src : declared(TEMPLATE_SRC)) {
            String root = rootOfTemplate(read(BUILTIN + src));
            assertThat(roots)
                    .as("%s makes a <%s>, and no type claims that root — the document would open with no "
                            + "fragments, no outline and no validation", src, root)
                    .contains(root);
        }
    }
}
