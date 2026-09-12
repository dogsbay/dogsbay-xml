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

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;
import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;
import com.dogsbay.dogsbayaieditor.commands.results.PreviewRenderResult;
import com.dogsbay.dogsbayaieditor.commands.results.ReferenceInfo;

/**
 * Headless tests for the reuse-analysis commands (where-used, keys,
 * check-links, render-preview) against a temp fixture project.
 */
class ReuseCommandsTest {

    private final HeadlessExecutor executor = new HeadlessExecutor();
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("reuse-commands-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void writeFixtureProject() throws IOException {
        write("root.ditamap",
                "<map><title>Guide</title>"
                + "<keydef keys=\"intro\" href=\"topics/intro.dita\"/>"
                + "<keydef keys=\"product\"><topicmeta><keywords>"
                + "<keyword>Acme Widget</keyword></keywords></topicmeta></keydef>"
                + "<topicref href=\"topics/intro.dita\"/>"
                + "<topicref href=\"topics/other.dita\"/>"
                + "</map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>Intro</title>"
                + "<conbody><p>About <ph keyref=\"product\"/>.</p></conbody></concept>");
        write("topics/other.dita", "<concept id=\"o\"><title>Other</title><conbody>"
                + "<p><xref href=\"intro.dita\">see</xref></p>"
                + "<p><xref keyref=\"intro\">via key</xref></p>"
                + "</conbody></concept>");
    }

    // -------------------------------------------------------------------------

    @Test
    void whereUsed_findsDirectAndViaKeyReferences() throws Exception {
        writeFixtureProject();
        List<ReferenceInfo> refs = executor.execute(new WhereUsedCommand(
                path("topics/intro.dita"), tempDir.toString(), path("root.ditamap")));

        // map topicref + keydef href + xref href + xref keyref (via key)
        assertEquals(4, refs.size(), "refs: " + refs);
        assertTrue(refs.stream().anyMatch(r -> "In maps".equals(r.category())));
        assertTrue(refs.stream().anyMatch(r -> "intro".equals(r.viaKey())),
                "via-key reference labeled");
        assertTrue(refs.stream().allMatch(r -> r.line() > 0), "line numbers present");
    }

    @Test
    void whereUsed_missingInputs_clearErrors() {
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new WhereUsedCommand(
                        tempDir.resolve("nope.dita").toString(), tempDir.toString(), null)));
        assertTrue(e.getMessage().contains("not found"));
    }

    @Test
    void listKeys_andResolveKey() throws Exception {
        writeFixtureProject();
        List<KeyInfo> keys = executor.execute(
                new ListKeysCommand(path("root.ditamap"), null));
        assertEquals(2, keys.size());

        KeyInfo product = executor.execute(
                new ResolveKeyCommand("product", path("root.ditamap"), null));
        assertEquals("Acme Widget", product.text());

        KeyInfo intro = executor.execute(
                new ResolveKeyCommand("intro", path("root.ditamap"), null));
        assertNotNull(intro.resolvedPath(), "href key resolves to the file");
        assertTrue(intro.resolvedPath().endsWith("intro.dita"));
    }

    @Test
    void resolveKey_scopedReuseAcrossSubmaps() throws Exception {
        // Two guides reused under their own @keyscope (the demo's collection shape):
        // the same bare key "start-here" binds to a different topic per scope.
        write("a-guide.ditamap",
                "<map><title>A</title>"
                + "<keydef keys=\"start-here\" href=\"topics/intro.dita\"/></map>");
        write("b-guide.ditamap",
                "<map><title>B</title>"
                + "<keydef keys=\"start-here\" href=\"topics/other.dita\"/></map>");
        write("collection.ditamap",
                "<map><title>C</title>"
                + "<mapref href=\"a-guide.ditamap\" keyscope=\"alpha\"/>"
                + "<mapref href=\"b-guide.ditamap\" keyscope=\"beta\"/></map>");
        write("topics/intro.dita", "<concept id=\"intro\"><title>I</title><conbody/></concept>");
        write("topics/other.dita", "<concept id=\"o\"><title>O</title><conbody/></concept>");

        KeyInfo fromAlpha = executor.execute(
                new ResolveKeyCommand("start-here", path("collection.ditamap"), null, "alpha"));
        assertTrue(fromAlpha.resolvedPath().endsWith("intro.dita"), fromAlpha.resolvedPath());

        KeyInfo fromBeta = executor.execute(
                new ResolveKeyCommand("start-here", path("collection.ditamap"), null, "beta"));
        assertTrue(fromBeta.resolvedPath().endsWith("other.dita"), fromBeta.resolvedPath());

        // List surfaces both under their fully-qualified names.
        List<KeyInfo> keys = executor.execute(new ListKeysCommand(path("collection.ditamap"), null));
        assertTrue(keys.stream().anyMatch(k -> "alpha.start-here".equals(k.name())));
        assertTrue(keys.stream().anyMatch(k -> "beta.start-here".equals(k.name())));
    }

    @Test
    void resolveKey_reportsScopeWhereFoundNotScopeRequested() throws Exception {
        // "product" is a ROOT key; resolving it from scope "alpha" walks outward and
        // lands at root, so KeyInfo.scope must be "" (root), not the requested "alpha".
        write("scoped.ditamap",
                "<map><title>S</title>"
                + "<keydef keys=\"product\"><topicmeta><keywords>"
                + "<keyword>Acme</keyword></keywords></topicmeta></keydef>"
                + "<topicgroup keyscope=\"alpha\"><title>A</title></topicgroup></map>");
        KeyInfo found = executor.execute(
                new ResolveKeyCommand("product", path("scoped.ditamap"), null, "alpha"));
        assertEquals("Acme", found.text());
        assertEquals("", found.scope(), "root key reached from a scope reports root scope");
    }

    @Test
    void resolveKey_unknown_throws() throws Exception {
        writeFixtureProject();
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new ResolveKeyCommand(
                        "no-such-key", path("root.ditamap"), null)));
        assertTrue(e.getMessage().contains("no-such-key"));
    }

    @Test
    void listKeys_ditavalConditionsTheSpace() throws Exception {
        write("attrs.ditamap",
                "<map><keydef keys=\"title\"><topicmeta><keywords>"
                + "<keyword product=\"a\">A Title</keyword>"
                + "<keyword product=\"b\">B Title</keyword>"
                + "</keywords></topicmeta></keydef></map>");
        write("no-a.ditaval",
                "<val><prop action=\"exclude\" att=\"product\" val=\"a\"/></val>");

        KeyInfo unfiltered = executor.execute(
                new ResolveKeyCommand("title", path("attrs.ditamap"), null));
        assertEquals("A Title", unfiltered.text(), "first variant without filter");

        KeyInfo filtered = executor.execute(
                new ResolveKeyCommand("title", path("attrs.ditamap"), path("no-a.ditaval")));
        assertEquals("B Title", filtered.text(), "filter picks the survivor");
    }

    @Test
    void checkLinks_reportsMissingTargetsAndUndefinedKeys() throws Exception {
        writeFixtureProject();
        write("topics/broken.dita", "<concept id=\"b\"><title>B</title><conbody>"
                + "<p><xref href=\"gone.dita\">missing</xref></p>"
                + "<p><ph keyref=\"undefined-key\"/></p>"
                + "</conbody></concept>");

        List<BrokenRef> broken = executor.execute(new CheckLinksCommand(
                tempDir.toString(), path("root.ditamap")));

        assertEquals(2, broken.size(), "broken: " + broken);
        assertTrue(broken.stream().anyMatch(b -> b.reason().contains("target not found")));
        assertTrue(broken.stream().anyMatch(b -> b.reason().contains("undefined-key")));
    }

    @Test
    void checkLinks_cleanProject_empty() throws Exception {
        writeFixtureProject();
        List<BrokenRef> broken = executor.execute(new CheckLinksCommand(
                tempDir.toString(), path("root.ditamap")));
        assertTrue(broken.isEmpty(), "fixture is clean: " + broken);
    }

    @Test
    void renderPreview_resolvesKeysAndWritesOutput() throws Exception {
        writeFixtureProject();
        File out = tempDir.resolve("out.html").toFile();

        PreviewRenderResult result = executor.execute(new RenderPreviewCommand(
                path("topics/intro.dita"), out.getAbsolutePath(),
                path("root.ditamap"), null));

        assertEquals(out.getAbsolutePath(), result.outputPath());
        String html = Files.readString(out.toPath());
        assertTrue(html.contains("Acme Widget"), "key resolved in rendered HTML");
    }

    @Test
    void renderPreview_inlineHtml_whenNoOutput() throws Exception {
        writeFixtureProject();
        PreviewRenderResult result = executor.execute(new RenderPreviewCommand(
                path("topics/intro.dita"), null, null, null));
        assertNull(result.outputPath());
        assertTrue(result.html().contains("<html"));
    }

    @Test
    void renderPreview_nonDita_throws() throws Exception {
        write("plain.xml", "<project><x/></project>");
        CommandException e = assertThrows(CommandException.class, () ->
                executor.execute(new RenderPreviewCommand(
                        path("plain.xml"), null, null, null)));
        assertTrue(e.getMessage().contains("not a DITA"));
    }

    @Test
    void registry_listsNewCommands() {
        var registry = new CommandRegistry();
        for (String name : new String[] {"where-used", "list-keys", "resolve-key",
                "check-links", "render-preview"}) {
            assertNotNull(registry.getCommand(name), "registry entry: " + name);
        }
    }

    // -------------------------------------------------------------------------

    private String path(String rel) {
        return tempDir.resolve(rel).toString();
    }

    private File write(String relPath, String content) throws IOException {
        Path p = tempDir.resolve(relPath);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }
}
