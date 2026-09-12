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

package com.dogsbay.xml.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the context-aware DITA preview: key resolution against a
 * context map and DITAVAL filtering. Headless.
 */
class DitaPreviewContextTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("preview-context-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    // -------------------------------------------------------------------------
    // Key resolution
    // -------------------------------------------------------------------------

    @Test
    void phKeyref_resolvesToKeywordText() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"product-name\"><topicmeta><keywords>"
                + "<keyword>DogsBay XML</keyword></keywords></topicmeta></keydef></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p>Welcome to <ph keyref=\"product-name\"/>!</p>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, null));

        assertTrue(html.contains("DogsBay XML"), "keyword text substituted");
        assertTrue(html.contains("keyref-resolved"), "marked resolved");
    }

    @Test
    void phKeyref_unknownKey_fallsBackToPlaceholder() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"something-else\" href=\"x.dita\"/></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><ph keyref=\"no-such-key\"/></p></conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, null));

        assertTrue(html.contains("no-such-key"), "key name shown");
        assertTrue(html.contains("class=\"keyref\""), "unresolved placeholder class");
    }

    @Test
    void xrefKeyref_usesLinktext() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"intro\" href=\"intro.dita\">"
                + "<topicmeta><linktext>The Introduction</linktext></topicmeta>"
                + "</keydef></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p>See <xref keyref=\"intro\"/>.</p></conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, null));

        assertTrue(html.contains("The Introduction"), "linktext as link content");
        assertTrue(html.contains("intro.dita"), "target href in tooltip");
    }

    @Test
    void conkeyref_missingTarget_badgeShowsResolvedTarget() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"warehouse\" href=\"shared/warehouse.dita\"/></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conkeyref=\"warehouse/legal\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, null));

        assertTrue(html.contains("conref-badge"));
        assertTrue(html.contains("shared/warehouse.dita"), "resolved href shown");
    }

    // -------------------------------------------------------------------------
    // Conref transclusion
    // -------------------------------------------------------------------------

    @Test
    void conref_transcludesFromSiblingFile() throws IOException {
        write("shared.dita", "<concept id=\"shared\"><title>S</title><conbody>"
                + "<p id=\"legal\">All rights reserved.</p></conbody></concept>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"shared.dita#shared/legal\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(), null);

        assertTrue(html.contains("All rights reserved."), "content transcluded: " + html);
        assertTrue(html.contains("class=\"conref-included\""), "reuse marker present");
        assertTrue(html.contains("Reused content: shared.dita#shared/legal"),
                "marker tooltip names the source");
        assertFalse(html.contains("class=\"conref-badge\""), "no badge once resolved");
    }

    @Test
    void conkeyref_transcludesViaContextMap() throws IOException {
        write("shared/warehouse.dita",
                "<concept id=\"wh\"><title>W</title><conbody>"
                + "<note id=\"legal\">© DogsBay Ltd.</note></conbody></concept>");
        File map = write("root.ditamap",
                "<map><keydef keys=\"warehouse\" href=\"shared/warehouse.dita\"/></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<note conkeyref=\"warehouse/legal\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(),
                new PreviewOptions(map, null));

        assertTrue(html.contains("© DogsBay Ltd."), "key-mediated transclusion: " + html);
        assertTrue(html.contains("class=\"conref-included\""));
        assertFalse(html.contains("class=\"conref-badge\""));
    }

    @Test
    void conref_nestedChainResolvesAgainstEachFilesDirectory() throws IOException {
        // sub/a.dita references ../other.dita — relative to ITS directory
        write("sub/a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p id=\"outer\">Start <ph conref=\"../other.dita#o/inner\"/> end.</p>"
                + "</conbody></concept>");
        write("other.dita", "<concept id=\"o\"><title>O</title><conbody>"
                + "<p><ph id=\"inner\">DEEP</ph></p></conbody></concept>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"sub/a.dita#a/outer\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(), null);

        assertTrue(html.contains("Start"), "outer content: " + html);
        assertTrue(html.contains("DEEP"), "nested conref resolved against sub/'s parent");
        assertFalse(html.contains("class=\"conref-badge\""));
    }

    @Test
    void conref_cycle_degradesToBadgeWithoutHanging() throws IOException {
        write("a.dita", "<concept id=\"a\"><title>A</title><conbody>"
                + "<p id=\"p1\" conref=\"b.dita#b/p2\"/></conbody></concept>");
        write("b.dita", "<concept id=\"b\"><title>B</title><conbody>"
                + "<p id=\"p2\" conref=\"a.dita#a/p1\"/></conbody></concept>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"a.dita#a/p1\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(), null);

        assertTrue(html.contains("conref-badge"), "cycle ends in a badge: " + html);
    }

    @Test
    void ditaval_filtersInsideTranscludedContent() throws IOException {
        write("shared.dita", "<concept id=\"shared\"><title>S</title><conbody>"
                + "<p id=\"legal\"><ph product=\"rosa\">ROSA terms.</ph>"
                + "<ph product=\"osd\">OSD terms.</ph></p></conbody></concept>");
        File ditaval = write("filter.ditaval",
                "<val><prop att=\"product\" val=\"osd\" action=\"exclude\"/></val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"shared.dita#shared/legal\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(),
                new PreviewOptions(null, ditaval));

        assertTrue(html.contains("ROSA terms."), "included variant survives: " + html);
        assertFalse(html.contains("OSD terms."), "excluded content filtered in the target too");
    }

    @Test
    void conref_missingFragment_keepsBadge() throws IOException {
        write("shared.dita", "<concept id=\"shared\"><title>S</title><conbody>"
                + "<p id=\"other\">x</p></conbody></concept>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"shared.dita#shared/nope\"/></conbody></concept>";

        String html = DitaConverter.convert(topic, tempDir.toFile(), null);

        assertTrue(html.contains("conref-badge"), "unresolvable fragment badges");
    }

    @Test
    void noContextMap_v1BehaviorUnchanged() {
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><ph keyref=\"product-name\"/></p></conbody></concept>";

        String withNull = DitaConverter.convert(topic, null, null);
        String withEmpty = DitaConverter.convert(topic, null, PreviewOptions.EMPTY);

        assertTrue(withNull.contains("class=\"keyref\""), "placeholder without context");
        assertEquals(withNull, withEmpty, "null and EMPTY options behave identically");
    }

    @Test
    void keySpace_submapKeysVisible() throws IOException {
        write("sub.ditamap",
                "<map><keydef keys=\"from-sub\"><topicmeta><keywords>"
                + "<keyword>Submap Keyword</keyword></keywords></topicmeta></keydef></map>");
        File root = write("root.ditamap",
                "<map><mapref href=\"sub.ditamap\"/></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><ph keyref=\"from-sub\"/></p></conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(root, null));
        assertTrue(html.contains("Submap Keyword"));
    }

    @Test
    void keySpaceCache_refreshesWhenMapChanges() throws IOException, InterruptedException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"k\"><topicmeta><keywords>"
                + "<keyword>OLD</keyword></keywords></topicmeta></keydef></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><ph keyref=\"k\"/></p></conbody></concept>";
        PreviewOptions options = new PreviewOptions(map, null);

        assertTrue(DitaConverter.convert(topic, null, options).contains("OLD"));

        // Rewrite the map with a different keyword and a newer mtime.
        Files.write(map.toPath(),
                ("<map><keydef keys=\"k\"><topicmeta><keywords>"
                + "<keyword>NEW</keyword></keywords></topicmeta></keydef></map>")
                        .getBytes(StandardCharsets.UTF_8));
        assertTrue(map.setLastModified(map.lastModified() + 5000));

        assertTrue(DitaConverter.convert(topic, null, options).contains("NEW"),
                "cache must refresh when the map's lastModified changes");
    }

    @Test
    void keyrefImage_resolvesAgainstDefiningMapDir() throws IOException {
        // keydef hrefs resolve against the MAP's directory, not the topic's.
        write("images/intro.png", "fake-png-bytes");
        write("maps/attrs.ditamap",
                "<map><keydef keys=\"img_intro\" href=\"../images/intro.png\""
                + " format=\"image\"/></map>");
        File root = write("root.ditamap",
                "<map><mapref href=\"maps/attrs.ditamap\"/></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<fig><image keyref=\"img_intro\" align=\"center\"/></fig>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(root, null));

        assertTrue(html.contains("<img"), "image element rendered");
        assertTrue(html.contains("images/intro.png"), "resolved to the real file");
        assertTrue(html.contains("file:"), "absolute file URL");
        // match the markup, not the bare string — the CSS block always
        // contains ".image-unresolved" as a selector
        assertFalse(html.contains("class=\"image-unresolved\""));
    }

    @Test
    void keyrefImage_noContextMap_showsPlaceholderNotBrokenImg() {
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<fig><image keyref=\"img_intro\"/></fig>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, null);

        assertFalse(html.contains("<img"), "no broken img without resolution");
        assertTrue(html.contains("class=\"image-unresolved\""), "visible placeholder");
        assertTrue(html.contains("img_intro"), "key named in placeholder");
    }

    // -------------------------------------------------------------------------
    // DITAVAL filtering
    // -------------------------------------------------------------------------

    @Test
    void ditaval_excludesByValue() throws IOException {
        File ditaval = write("filter.ditaval",
                "<val><prop att=\"platform\" val=\"linux\" action=\"exclude\"/></val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p platform=\"linux\">linux only</p>"
                + "<p platform=\"windows\">windows only</p>"
                + "<p>everywhere</p>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(null, ditaval));

        assertFalse(html.contains("linux only"), "excluded content removed");
        assertTrue(html.contains("windows only"));
        assertTrue(html.contains("everywhere"), "unprofiled content untouched");
    }

    @Test
    void ditaval_multiTokenAttr_needsAllTokensExcluded() throws IOException {
        File ditaval = write("filter.ditaval",
                "<val><prop att=\"platform\" val=\"linux\" action=\"exclude\"/></val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p platform=\"linux windows\">both platforms</p>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(null, ditaval));

        assertTrue(html.contains("both platforms"),
                "kept: windows token is not excluded");
    }

    @Test
    void ditaval_attWideExclude_withExplicitInclude() throws IOException {
        File ditaval = write("filter.ditaval",
                "<val>"
                + "<prop att=\"audience\" action=\"exclude\"/>"
                + "<prop att=\"audience\" val=\"admin\" action=\"include\"/>"
                + "</val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p audience=\"admin\">for admins</p>"
                + "<p audience=\"novice\">for novices</p>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(null, ditaval));

        assertTrue(html.contains("for admins"), "explicit include overrides att-wide");
        assertFalse(html.contains("for novices"), "att-wide exclude applies");
    }

    @Test
    void ditaval_missingFile_noFiltering() {
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p platform=\"linux\">still here</p></conbody></concept>";
        File missing = new File(tempDir.toFile(), "nope.ditaval");

        String html = DitaConverter.convert(topic, null, new PreviewOptions(null, missing));
        assertTrue(html.contains("still here"));
    }

    @Test
    void conditionalKeydef_ditavalSelectsTheRightVariant() throws IOException {
        // The openshift-docs pattern: one keydef, two profiled keyword
        // variants. The ditaval must condition the KEY SPACE, not just the
        // previewed document.
        File map = write("common-attributes.ditamap",
                "<map><keydef keys=\"product-title\"><topicmeta><keywords>"
                + "<keyword product=\"openshift-rosa\">Red Hat OpenShift Service on AWS</keyword>"
                + "<keyword product=\"openshift-enterprise\">OpenShift Container Platform</keyword>"
                + "</keywords></topicmeta></keydef></map>");
        File rosaDitaval = write("rosa.ditaval",
                "<val>"
                + "<prop action=\"include\" att=\"product\" val=\"openshift-rosa\"/>"
                + "<prop action=\"exclude\" att=\"product\" val=\"openshift-enterprise\"/>"
                + "</val>");
        String topic = "<concept id=\"c\">"
                + "<title><keyword keyref=\"product-title\"/> Documentation</title>"
                + "<conbody><p>x</p></conbody></concept>";

        String withFilter = DitaConverter.convert(topic, null,
                new PreviewOptions(map, rosaDitaval));
        assertTrue(withFilter.contains("Red Hat OpenShift Service on AWS"),
                "included variant renders");
        assertFalse(withFilter.contains("OpenShift Container Platform"),
                "excluded variant must not render");
    }

    @Test
    void conditionalKeydef_noDitaval_firstVariantOnlyNoConcatenation() throws IOException {
        File map = write("attrs.ditamap",
                "<map><keydef keys=\"product-title\"><topicmeta><keywords>"
                + "<keyword product=\"a\">First Product</keyword>"
                + "<keyword product=\"b\">Second Product</keyword>"
                + "</keywords></topicmeta></keydef></map>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><keyword keyref=\"product-title\"/></p></conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, null));
        assertTrue(html.contains("First Product"), "first variant used");
        assertFalse(html.contains("Second Product"),
                "variants must not concatenate");
        assertFalse(html.contains("First ProductSecond"), "no run-together text");
    }

    @Test
    void keySpaceCache_distinguishesDitaval() throws IOException {
        File map = write("m.ditamap",
                "<map><keydef keys=\"k\"><topicmeta><keywords>"
                + "<keyword product=\"x\">XVAL</keyword>"
                + "<keyword product=\"y\">YVAL</keyword>"
                + "</keywords></topicmeta></keydef></map>");
        File excludeX = write("no-x.ditaval",
                "<val><prop action=\"exclude\" att=\"product\" val=\"x\"/></val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><ph keyref=\"k\"/></p></conbody></concept>";

        // Unfiltered first (primes the cache), then filtered — the filtered
        // result must not be served from the unfiltered cache entry.
        String unfiltered = DitaConverter.convert(topic, null, new PreviewOptions(map, null));
        assertTrue(unfiltered.contains("XVAL"));

        String filtered = DitaConverter.convert(topic, null, new PreviewOptions(map, excludeX));
        assertTrue(filtered.contains("YVAL"), "filtered key space picks the survivor");
        assertFalse(filtered.contains("XVAL"));
    }

    @Test
    void ditavalAndKeyspace_together() throws IOException {
        File map = write("root.ditamap",
                "<map><keydef keys=\"prod\"><topicmeta><keywords>"
                + "<keyword>Acme</keyword></keywords></topicmeta></keydef></map>");
        File ditaval = write("filter.ditaval",
                "<val><prop att=\"audience\" val=\"internal\" action=\"exclude\"/></val>");
        String topic = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p>Get <ph keyref=\"prod\"/> now.</p>"
                + "<p audience=\"internal\">internal note</p>"
                + "</conbody></concept>";

        String html = DitaConverter.convert(topic, null, new PreviewOptions(map, ditaval));

        assertTrue(html.contains("Acme"), "keys resolve");
        assertFalse(html.contains("internal note"), "ditaval filters");
    }

    // -------------------------------------------------------------------------

    private File write(String relPath, String content) throws IOException {
        Path p = tempDir.resolve(relPath);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }
}
