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
package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.template.TemplateProperties;

/** What a new file starts as: which templates are offered, and what goes in the file. */
class NewDocumentTemplateFilterTest {

    @TempDir
    Path dir;

    // ── Which templates are offered ──────────────────────────────────

    private TemplateProperties template(String name, String content) throws Exception {
        Path file = Files.writeString(dir.resolve(name), content);
        return new TemplateProperties(name, file.toUri().toURL());
    }

    @Test
    void aMapIsNotOfferedAConcept() throws Exception {
        TemplateProperties concept = template("concept.xml",
                "<?xml version=\"1.0\"?>\n<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n<concept/>");
        TemplateProperties map = template("map.xml",
                "<?xml version=\"1.0\"?>\n<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n<map/>");

        // the complaint that started this: naming a map and being offered a concept, pre-selected
        assertThat(NewDocumentDialog.suits(concept, "ditamap")).isFalse();
        assertThat(NewDocumentDialog.suits(map, "ditamap")).isTrue();
        assertThat(NewDocumentDialog.suits(concept, "dita")).isTrue();
        assertThat(NewDocumentDialog.suits(map, "dita")).isFalse();
    }

    @Test
    void aFilterAndARuleSetGoWhereTheyBelongAndNowhereElse() throws Exception {
        TemplateProperties ditaval = template("ditaval.xml", "<?xml version=\"1.0\"?>\n<val/>");
        TemplateProperties schematron = template("sch.xml",
                "<?xml version=\"1.0\"?>\n<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"/>");

        assertThat(NewDocumentDialog.suits(ditaval, "ditaval")).isTrue();
        assertThat(NewDocumentDialog.suits(schematron, "sch")).isTrue();

        // Neither is a map or a topic, so offering one while someone names a topic would be offering a document that
        // cannot be what they asked for
        for (String extension : new String[] { "dita", "ditamap", "bookmap" }) {
            assertThat(NewDocumentDialog.suits(ditaval, extension)).as("ditaval for .%s", extension).isFalse();
            assertThat(NewDocumentDialog.suits(schematron, extension)).as("schematron for .%s", extension).isFalse();
        }
        assertThat(NewDocumentDialog.suits(ditaval, "sch")).isFalse();
        assertThat(NewDocumentDialog.suits(schematron, "ditaval")).isFalse();

        // but rules.xml is an ordinary name for a Schematron file, and hiding it from one would be the mistake this
        // method's own history is about: an absent row looks like a template that does not exist
        for (String extension : new String[] { "xml", "xsl", "rng" }) {
            assertThat(NewDocumentDialog.suits(schematron, extension)).as("schematron for .%s", extension).isTrue();
            assertThat(NewDocumentDialog.suits(ditaval, extension)).as("ditaval for .%s", extension).isTrue();
        }
    }

    @Test
    void aSubjectSchemeIsAMap() throws Exception {
        TemplateProperties scheme = template("scheme.xml",
                "<?xml version=\"1.0\"?>\n<!DOCTYPE subjectScheme PUBLIC "
                + "\"-//OASIS//DTD DITA Subject Scheme Map//EN\" \"subjectScheme.dtd\">\n<subjectScheme/>");

        assertThat(NewDocumentDialog.suits(scheme, "ditamap")).isTrue();
        assertThat(NewDocumentDialog.suits(scheme, "dita")).isFalse();
    }

    @Test
    void aRootWeCannotPlaceIsStillOfferedEverywhere() throws Exception {
        TemplateProperties stylesheet = template("x.xml",
                "<?xml version=\"1.0\"?>\n<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");

        // the rule that matters: hide only what is positively wrong. An unknown root is not wrong anywhere.
        for (String extension : new String[] { "dita", "ditamap", "ditaval", "sch", "xsl" }) {
            assertThat(NewDocumentDialog.suits(stylesheet, extension)).as(".%s", extension).isTrue();
        }
    }

    @Test
    void withNoFileNameEverythingIsOffered() throws Exception {
        TemplateProperties concept = template("c.xml", "<!DOCTYPE concept SYSTEM \"concept.dtd\">\n<concept/>");
        // File ▸ New has no name to go on, so it cannot filter and must not pretend to
        assertThat(NewDocumentDialog.suits(concept, null)).isTrue();
        assertThat(NewDocumentDialog.extensionOf(null)).isNull();
        assertThat(NewDocumentDialog.extensionOf("README")).isNull();
        assertThat(NewDocumentDialog.extensionOf("audacity.DITAMAP")).isEqualTo("ditamap");
    }

    @Test
    void aTemplateThatCannotBeReadIsOfferedRatherThanHidden() throws Exception {
        TemplateProperties broken = new TemplateProperties("gone", dir.resolve("missing.xml").toUri().toURL());
        // hiding it would be the worse mistake: the writer cannot tell it exists to fix it
        assertThat(NewDocumentDialog.suits(broken, "dita")).isTrue();
    }

    @Test
    void theRootIsTakenFromTheDocumentTypeOrTheFirstElement() throws Exception {
        assertThat(NewDocumentDialog.rootOf(template("a.xml", "<!DOCTYPE bookmap SYSTEM \"b.dtd\"><bookmap/>")))
                .isEqualTo("bookmap");
        assertThat(NewDocumentDialog.rootOf(template("b.xml", "<?xml version=\"1.0\"?>\n<subjectScheme/>")))
                .isEqualTo("subjectScheme");
        // a prefix is not part of the name: <x:map> is a map
        assertThat(NewDocumentDialog.localName("x:map")).isEqualTo("map");
        assertThat(NewDocumentDialog.makesMaps("x:map")).isTrue();
        assertThat(NewDocumentDialog.makesMaps("bookmap")).isTrue();
        assertThat(NewDocumentDialog.makesMaps("learningMap")).isTrue();
        assertThat(NewDocumentDialog.makesMaps("concept")).isFalse();
        // a specialisation, a schema, an SVG: not ours to place, so not ours to hide
        assertThat(NewDocumentDialog.makesMaps("myOwnTopicType")).isNull();
        assertThat(NewDocumentDialog.makesMaps("schema")).isNull();
    }

    @Test
    void anExtensionItCannotPlaceIsNotFilteredAtAll() throws Exception {
        TemplateProperties concept = template("c.xml", "<!DOCTYPE concept SYSTEM \"concept.dtd\">\n<concept/>");
        TemplateProperties map = template("m.xml", "<!DOCTYPE map SYSTEM \"map.dtd\">\n<map/>");

        // hiding every template from someone naming a stylesheet or a schema is worse than the fault this fixes, and it
        // looks like having no templates at all
        for (String extension : new String[] { "xsl", "xsd", "svg", "rng", "xhtml", "wsdl", "xml" }) {
            assertThat(NewDocumentDialog.suits(concept, extension)).as(extension).isTrue();
            assertThat(NewDocumentDialog.suits(map, extension)).as(extension).isTrue();
        }
    }

    @Test
    void aSpecialisedMapIsStillOfferedForAMap() throws Exception {
        TemplateProperties learning = template("l.xml", "<!DOCTYPE learningMap SYSTEM \"l.dtd\">\n<learningMap/>");
        TemplateProperties own = template("o.xml", "<!DOCTYPE myThing SYSTEM \"o.dtd\">\n<myThing/>");

        assertThat(NewDocumentDialog.suits(learning, "ditamap")).isTrue();
        assertThat(NewDocumentDialog.suits(own, "ditamap")).isTrue(); // unknown, so offered
        assertThat(NewDocumentDialog.suits(own, "dita")).isTrue();
    }


    @Test
    void aMapIsThePlainestDocumentForADitamap() {
        // Bookmap and Subject Scheme are maps too and sort before Map alphabetically, so the dialog opened on
        // Bookmap: a reader naming a .ditamap and pressing Enter got the wrong document, in the commonest case.
        assertThat(NewDocumentDialog.plainestRootFor("ditamap")).isEqualTo("map");
        assertThat(NewDocumentDialog.plainestRootFor("bookmap")).isEqualTo("bookmap");
        assertThat(NewDocumentDialog.plainestRootFor("ditaval")).isEqualTo("val");
        assertThat(NewDocumentDialog.plainestRootFor("sch")).isEqualTo("schema");
    }

    @Test
    void aTopicHasNoSinglePlainestKind() {
        // concept, task and reference are all first-class, and guessing one of them would be worse than not guessing
        assertThat(NewDocumentDialog.plainestRootFor("dita")).isNull();
        assertThat(NewDocumentDialog.plainestRootFor("xml")).isNull();
        assertThat(NewDocumentDialog.plainestRootFor(null)).isNull();
    }

}
