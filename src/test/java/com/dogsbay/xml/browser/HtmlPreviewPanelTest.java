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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the HTML preview feature.
 * Note: JavaFX WebView requires a display, so UI tests are skipped in headless mode.
 * These tests verify the non-UI logic (class loading, HTML detection, etc.).
 */
public class HtmlPreviewPanelTest {

    @Test
    @DisplayName("HtmlPreviewPanel class can be loaded")
    public void testClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.browser.HtmlPreviewPanel");
        }, "HtmlPreviewPanel class should be loadable");
    }

    @Test
    @DisplayName("HtmlPreviewManager class can be loaded")
    public void testManagerClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.browser.HtmlPreviewManager");
        }, "HtmlPreviewManager class should be loadable");
    }

    @Test
    @DisplayName("OpenPreviewAction class can be loaded")
    public void testActionClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.dogsbayaieditor.actions.OpenPreviewAction");
        }, "OpenPreviewAction class should be loadable");
    }

    @Test
    @DisplayName("HtmlDocumentFormat detects HTML by file extension")
    public void testHtmlDocumentFormatDetection() {
        com.dogsbay.xml.editor.DocumentFormatRegistry.clear();
        com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.HtmlDocumentFormat());
        com.dogsbay.xml.editor.DocumentFormat format = com.dogsbay.xml.editor.DocumentFormatRegistry.detect("test.html");
        assertNotNull(format, "HTML format should be detected for .html files");
        assertEquals("HTML", format.getName());
    }

    @Test
    @DisplayName("HtmlPreviewManager.resolveHtml method exists")
    public void testResolveHtmlExists() throws Exception {
        var method = HtmlPreviewManager.class.getDeclaredMethod(
            "resolveHtml", com.dogsbay.xml.DogsBayDocument.class);
        assertNotNull(method, "resolveHtml method should exist");
    }

    @Test
    @DisplayName("JavaFX dependency JARs are resolved by Maven")
    public void testJavaFXDependenciesResolved() {
        // JavaFX uses the module system, so Class.forName may not work on classpath.
        // Instead, verify that HtmlPreviewPanel (which imports JavaFX) compiled successfully,
        // which proves the JavaFX dependencies are available.
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.browser.HtmlPreviewPanel");
        }, "HtmlPreviewPanel (which imports JavaFX) should be loadable, proving JavaFX deps are available");
    }

    @Test
    @DisplayName("PreviewState class can be loaded")
    public void testPreviewStateClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.browser.PreviewState");
        }, "PreviewState class should be loadable");
    }

    @Test
    @DisplayName("MarkdownDocumentFormat detects Markdown by file extension")
    public void testMarkdownDocumentFormatDetection() {
        com.dogsbay.xml.editor.DocumentFormatRegistry.clear();
        com.dogsbay.xml.editor.DocumentFormatRegistry.register(new com.dogsbay.xml.editor.MarkdownDocumentFormat());
        com.dogsbay.xml.editor.DocumentFormat format = com.dogsbay.xml.editor.DocumentFormatRegistry.detect("readme.md");
        assertNotNull(format, "Markdown format should be detected for .md files");
        assertEquals("Markdown", format.getName());
    }

    @Test
    @DisplayName("HtmlPreviewManager has disposePreview method for cleanup")
    public void testDisposePreviewExists() throws Exception {
        var method = HtmlPreviewManager.class.getDeclaredMethod(
            "disposePreview", String.class, com.dogsbay.dogsbayaieditor.DogsBayAIEditor.class);
        assertNotNull(method, "disposePreview method should exist for cleanup");
    }

    @TempDir Path dir;

    // -------------------------------------------------------------------------
    // What a DITA preview renders against
    //
    // The decision only. Whether an open preview is re-seeded when the reader switches deliverable lives in
    // HtmlPreviewManager's event subscription and its tab-reuse path, and neither can be driven from here:
    // HtmlPreviewPanel wraps a JFXPanel and cannot be built without JavaFX. Those are checked in the editor.
    // -------------------------------------------------------------------------

    private static com.dogsbay.dogsbayaieditor.ditaproject.Deliverable deliverable(
            String name, Path map, Path ditaval) {
        return new com.dogsbay.dogsbayaieditor.ditaproject.Deliverable(
                name, map, ditaval == null ? List.of() : List.of(ditaval),
                "html5", null, List.of(), Path.of("project.json"), null);
    }

    @Test
    @DisplayName("a preview renders against the active deliverable's map and filter")
    public void testDitaContextFollowsTheActiveDeliverable() {
        // What "following the deliverable" has to arrive at, wherever it is asked from.
        Path beginner = dir.resolve("beginner-guide.ditamap");
        Path macFilter = dir.resolve("filters/mac-beginner.ditaval");

        var context = HtmlPreviewManager.ditaContextFor(
                deliverable("beginner-mac", beginner, macFilter),
                dir.resolve("audacity-guide.ditamap").toFile());

        assertEquals(beginner.toFile(), context.map(), "the deliverable's map, not the project default");
        assertEquals(macFilter.toFile(), context.ditaval());
    }

    @Test
    @DisplayName("no deliverable falls back to the project's Default Root Map, unfiltered")
    public void testDitaContextWithoutADeliverable() {
        File defaultMap = dir.resolve("audacity-guide.ditamap").toFile();

        var context = HtmlPreviewManager.ditaContextFor(null, defaultMap);

        assertEquals(defaultMap, context.map());
        assertNull(context.ditaval());
    }

    @Test
    @DisplayName("a deliverable with no DITAVAL filters nothing")
    public void testDitaContextDeliverableWithoutAFilter() {
        // Not the previous deliverable's filter, and not the default root map's either — "full" shows everything
        Path full = dir.resolve("audacity-guide.ditamap");

        var context = HtmlPreviewManager.ditaContextFor(deliverable("full", full, null), full.toFile());

        assertEquals(full.toFile(), context.map());
        assertNull(context.ditaval());
    }

    @Test
    @DisplayName("branch selector lists 'Whole map' + each ditavalref branch (Phase 4)")
    public void testBuildBranchChoices() throws Exception {
        Files.writeString(dir.resolve("base.ditaval"), "<val/>");
        Files.writeString(dir.resolve("win.ditaval"), "<val/>");
        Files.writeString(dir.resolve("mac.ditaval"), "<val/>");
        Path map = dir.resolve("guide.ditamap");
        Files.writeString(map, """
            <?xml version="1.0" encoding="UTF-8"?>
            <map><title>G</title>
              <topicref href="install.dita">
                <ditavalref href="win.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>win</dvrResourcePrefix></ditavalmeta></ditavalref>
                <ditavalref href="mac.ditaval"><ditavalmeta>
                  <dvrResourcePrefix>mac</dvrResourcePrefix></ditavalmeta></ditavalref>
              </topicref>
            </map>
            """);
        File base = dir.resolve("base.ditaval").toFile();

        List<HtmlPreviewPanel.BranchChoice> choices =
                HtmlPreviewPanel.buildBranchChoices(map.toFile(), base);

        assertEquals(3, choices.size(), "whole map + 2 branches");
        assertEquals("Whole map", choices.get(0).label());
        assertEquals(base, choices.get(0).ditaval(), "whole map restores the default filter");
        assertEquals("win", choices.get(1).label());
        assertEquals(dir.resolve("win.ditaval").toFile(), choices.get(1).ditaval());

        // No context map → just the whole-map entry.
        assertEquals(1, HtmlPreviewPanel.buildBranchChoices(null, null).size());
    }
}
