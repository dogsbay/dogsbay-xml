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

package com.dogsbay.dogsbayaieditor.links;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for LinkExtractor, KeySpace and ReverseLinkIndex. Headless,
 * filesystem via a temp tree, no network, no catalogs.
 */
class LinksTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("links-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    // -------------------------------------------------------------------------
    // LinkExtractor
    // -------------------------------------------------------------------------

    @Test
    void extract_hrefWithFragment_resolvedAndSplit() throws IOException {
        File src = write("topics/a.dita",
                "<concept id=\"a\"><title>A</title><conbody>\n"
                + "<p><xref href=\"../shared/b.dita#b/sect\">see</xref></p>\n"
                + "</conbody></concept>");
        write("shared/b.dita", "<concept id=\"b\"/>");

        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(1, refs.size());
        Reference r = refs.get(0);
        assertEquals("xref", r.element());
        assertEquals("href", r.attribute());
        assertEquals("b/sect", r.fragment());
        assertEquals(canon("shared/b.dita"), r.targetPath());
        assertEquals(2, r.line(), "line of the start tag");
    }

    @Test
    void extract_conrefAndSameFileConref() throws IOException {
        File src = write("a.dita",
                "<concept id=\"a\"><title>T</title><conbody>"
                + "<p conref=\"warehouse.dita#wh/legal\"/>"
                + "<p conref=\"#a/local\"/>"
                + "</conbody></concept>");

        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(2, refs.size());
        assertEquals(canon("warehouse.dita"), refs.get(0).targetPath());
        assertEquals("wh/legal", refs.get(0).fragment());
        assertEquals(LinkExtractor.canonical(src), refs.get(1).targetPath(),
                "fragment-only conref targets the same file");
    }

    @Test
    void extract_keyrefAndConkeyref_recordedAsKeyReferences() throws IOException {
        File src = write("a.dita",
                "<concept id=\"a\"><title>T</title><conbody>"
                + "<p><ph keyref=\"product-name\"/></p>"
                + "<p conkeyref=\"warehouse/legal\"/>"
                + "</conbody></concept>");

        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(2, refs.size());

        Reference keyref = refs.get(0);
        assertTrue(keyref.isKeyReference());
        assertEquals("product-name", keyref.keyName());
        assertNull(keyref.targetPath());

        Reference conkeyref = refs.get(1);
        assertTrue(conkeyref.isKeyReference());
        assertEquals("warehouse", conkeyref.keyName());
        assertEquals("legal", conkeyref.fragment());
    }

    @Test
    void extract_externalSchemesSkipped() throws IOException {
        File src = write("a.dita",
                "<concept id=\"a\"><title>T</title><conbody>"
                + "<p><xref href=\"https://example.com/x\">web</xref>"
                + "<xref href=\"mailto:x@y.z\">mail</xref>"
                + "<xref href=\"other.dita\">local</xref></p>"
                + "</conbody></concept>");

        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(1, refs.size(), "only the local ref survives");
        assertEquals(canon("other.dita"), refs.get(0).targetPath());
    }

    @Test
    void extract_imageSrcAndXinclude() throws IOException {
        File src = write("a.xml",
                "<doc xmlns:xi=\"http://www.w3.org/2001/XInclude\">"
                + "<image src=\"img/pic.png\"/>"
                + "<xi:include href=\"part.xml\"/>"
                + "</doc>");

        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(2, refs.size());
        assertEquals(canon("img/pic.png"), refs.get(0).targetPath());
        assertEquals("include", refs.get(1).element());
        assertEquals(canon("part.xml"), refs.get(1).targetPath());
    }

    @Test
    void extract_keyDefinitions_withKeywordAndLinktext() throws IOException {
        File map = write("root.ditamap",
                "<map><title>Root</title>\n"
                + "<keydef keys=\"product-name\">\n"
                + "  <topicmeta><keywords><keyword>DogsBay XML</keyword></keywords></topicmeta>\n"
                + "</keydef>\n"
                + "<keydef keys=\"intro overview\" href=\"intro.dita\">\n"
                + "  <topicmeta><linktext>The Introduction</linktext></topicmeta>\n"
                + "</keydef>\n"
                + "</map>");

        List<KeyDefinition> defs = LinkExtractor.extract(map).keyDefinitions();
        assertEquals(2, defs.size());
        assertEquals("DogsBay XML", defs.get(0).keywordText());
        assertNull(defs.get(0).href());
        assertEquals("intro overview", defs.get(1).keys());
        assertEquals("intro.dita", defs.get(1).href());
        assertEquals("The Introduction", defs.get(1).linkText());
    }

    @Test
    void extract_malformedFile_returnsPartialNotThrow() throws IOException {
        File src = write("broken.dita",
                "<concept id=\"a\"><conbody><p conref=\"x.dita#x/y\"/><p>unclosed");
        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(1, refs.size(), "refs before the error are kept");
    }

    @Test
    void extract_doctypePresent_noDtdFetch() throws IOException {
        File src = write("a.dita",
                "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"missing.dtd\">\n"
                + "<concept id=\"a\"><conbody><p conref=\"b.dita#b/x\"/></conbody></concept>");
        List<Reference> refs = LinkExtractor.extract(src).references();
        assertEquals(1, refs.size(), "parses despite unresolvable DTD");
    }

    @Test
    void extract_multipleKeywords_firstWinsNoConcatenation() throws IOException {
        File map = write("m.ditamap",
                "<map><keydef keys=\"product\"><topicmeta><keywords>"
                + "<keyword>First</keyword><keyword>Second</keyword>"
                + "</keywords></topicmeta></keydef></map>");
        List<KeyDefinition> defs = LinkExtractor.extract(map).keyDefinitions();
        assertEquals("First", defs.get(0).keywordText(),
                "effective text is the first keyword, never a concatenation");
    }

    @Test
    void extract_withExclusion_skipsExcludedSubtrees() throws IOException {
        File map = write("m.ditamap",
                "<map>"
                + "<keydef keys=\"product\"><topicmeta><keywords>"
                + "<keyword product=\"rosa\">ROSA Title</keyword>"
                + "<keyword product=\"ocp\">OCP Title</keyword>"
                + "</keywords></topicmeta></keydef>"
                + "<keydef keys=\"ocp-only\" href=\"o.dita\" product=\"ocp\"/>"
                + "<topicref href=\"ocp-topic.dita\" product=\"ocp\"/>"
                + "<topicref href=\"shared.dita\"/>"
                + "</map>");

        // Exclude anything whose product attribute is "ocp".
        ElementExclusion excludeOcp = atts -> "ocp".equals(atts.getValue("product"));
        LinkExtractor.ExtractionResult result = LinkExtractor.extract(map, excludeOcp);

        assertEquals("ROSA Title", result.keyDefinitions().get(0).keywordText(),
                "excluded keyword variant doesn't contribute text");
        assertEquals(1, result.keyDefinitions().size(),
                "excluded keydef dropped entirely");
        assertEquals(1, result.references().size(),
                "excluded topicref dropped; shared.dita survives");
        assertTrue(result.references().get(0).rawValue().contains("shared"));
    }

    // -------------------------------------------------------------------------
    // KeySpace
    // -------------------------------------------------------------------------

    @Test
    void keySpace_submapRecursion_firstDefinitionWins() throws IOException {
        write("sub.ditamap",
                "<map>"
                + "<keydef keys=\"product-name\"><topicmeta><keywords>"
                + "<keyword>FROM SUBMAP</keyword></keywords></topicmeta></keydef>"
                + "<keydef keys=\"sub-only\" href=\"sub.dita\"/>"
                + "</map>");
        File root = write("root.ditamap",
                "<map>"
                + "<keydef keys=\"product-name\"><topicmeta><keywords>"
                + "<keyword>FROM ROOT</keyword></keywords></topicmeta></keydef>"
                + "<mapref href=\"sub.ditamap\"/>"
                + "</map>");

        KeySpace ks = KeySpace.fromRootMap(root);
        assertEquals("FROM ROOT", ks.resolve("product-name").keywordText(),
                "root map definition wins");
        assertNotNull(ks.resolve("sub-only"), "submap keys collected");
    }

    @Test
    void keySpace_cyclicSubmaps_terminate() throws IOException {
        write("a.ditamap", "<map><mapref href=\"b.ditamap\"/>"
                + "<keydef keys=\"ka\" href=\"a.dita\"/></map>");
        File b = write("b.ditamap", "<map><mapref href=\"a.ditamap\"/>"
                + "<keydef keys=\"kb\" href=\"b.dita\"/></map>");

        KeySpace ks = KeySpace.fromRootMap(b);
        assertNotNull(ks.resolve("ka"));
        assertNotNull(ks.resolve("kb"));
    }

    @Test
    void keySpace_resolveHrefFile_relativeToDefiningMap() throws IOException {
        write("maps/keys.ditamap",
                "<map><keydef keys=\"intro\" href=\"../topics/intro.dita\"/></map>");
        File root = write("root.ditamap",
                "<map><topicref href=\"maps/keys.ditamap\" format=\"ditamap\"/></map>");

        KeySpace ks = KeySpace.fromRootMap(root);
        File resolved = ks.resolveHrefFile("intro");
        assertNotNull(resolved);
        assertEquals(canon("topics/intro.dita"), LinkExtractor.canonical(resolved));
    }

    @Test
    void keySpace_missingMap_emptyNotThrow() {
        KeySpace ks = KeySpace.fromRootMap(new File(tempDir.toFile(), "nope.ditamap"));
        assertTrue(ks.isEmpty());
        assertNull(ks.resolve("anything"));
    }

    // -------------------------------------------------------------------------
    // ReverseLinkIndex
    // -------------------------------------------------------------------------

    @Test
    void index_findsUsagesAcrossTree() throws IOException {
        write("maps/guide.ditamap",
                "<map><topicref href=\"../topics/intro.dita\"/></map>");
        write("topics/other.dita",
                "<concept id=\"o\"><conbody>"
                + "<p><xref href=\"intro.dita\">see</xref></p>"
                + "<p conref=\"intro.dita#intro/shared\"/>"
                + "</conbody></concept>");
        File intro = write("topics/intro.dita", "<concept id=\"intro\"/>");

        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        List<Reference> usages = index.usagesOf(intro);

        assertEquals(3, usages.size(), "map topicref + xref + conref");
        assertEquals(3, index.getFilesIndexed());
    }

    @Test
    void index_usagesOfKey() throws IOException {
        write("a.dita", "<concept id=\"a\"><conbody>"
                + "<p><ph keyref=\"product-name\"/></p></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><conbody>"
                + "<p conkeyref=\"product-name/x\"/></conbody></concept>");

        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        assertEquals(2, index.usagesOfKey("product-name").size());
    }

    @Test
    void index_updateFile_reflectsEdits() throws IOException {
        File a = write("a.dita", "<concept id=\"a\"><conbody>"
                + "<p><xref href=\"b.dita\">b</xref></p></conbody></concept>");
        File b = write("b.dita", "<concept id=\"b\"/>");

        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        assertEquals(1, index.usagesOf(b).size());

        // Edit a.dita to drop the reference
        Files.write(a.toPath(),
                "<concept id=\"a\"><conbody><p>no refs now</p></conbody></concept>"
                        .getBytes(StandardCharsets.UTF_8));
        index.updateFile(a);

        assertEquals(0, index.usagesOf(b).size(), "stale reference removed");
    }

    @Test
    void index_usagesIncludingKeys() throws IOException {
        File root = write("root.ditamap",
                "<map><keydef keys=\"intro\" href=\"intro.dita\"/></map>");
        write("user.dita", "<concept id=\"u\"><conbody>"
                + "<p><xref keyref=\"intro\">the intro</xref></p></conbody></concept>");
        File intro = write("intro.dita", "<concept id=\"intro\"/>");

        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        KeySpace ks = KeySpace.fromRootMap(root);

        assertEquals(1, index.usagesOf(intro).size(), "direct: keydef href");
        List<Reference> all = index.usagesOfIncludingKeys(intro, ks);
        assertEquals(2, all.size(), "direct + via key 'intro'");
    }

    @Test
    void index_skipsNoiseDirs() throws IOException {
        write(".git/config.xml", "<x href=\"a.dita\"/>");
        write("build/gen.dita", "<concept id=\"g\"><conbody>"
                + "<p conref=\"a.dita#a/x\"/></conbody></concept>");
        File a = write("a.dita", "<concept id=\"a\"/>");

        ReverseLinkIndex index = ReverseLinkIndex.build(tempDir.toFile(), null);
        assertEquals(0, index.usagesOf(a).size(), ".git and build excluded");
    }

    // -------------------------------------------------------------------------

    private File write(String relPath, String content) throws IOException {
        Path p = tempDir.resolve(relPath);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    private String canon(String relPath) throws IOException {
        return tempDir.resolve(relPath).toFile().getCanonicalPath();
    }
}
