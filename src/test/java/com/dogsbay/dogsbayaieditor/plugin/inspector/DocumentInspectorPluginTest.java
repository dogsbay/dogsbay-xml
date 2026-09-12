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

package com.dogsbay.dogsbayaieditor.plugin.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.plugin.UIService;
import com.dogsbay.dogsbayaieditor.services.ActionRegistry;
import com.dogsbay.dogsbayaieditor.services.DocumentManager;
import com.dogsbay.dogsbayaieditor.services.EventBus;
import com.dogsbay.dogsbayaieditor.services.SchemaManager;
import com.dogsbay.dogsbayaieditor.services.ToolbarManager;
import com.dogsbay.dogsbayaieditor.services.ViewManager;

/**
 * Tests for the Document Inspector plugin.
 */
class DocumentInspectorPluginTest {

    private DocumentInspectorPlugin plugin;
    private EventBus eventBus;
    private RecordingUIService uiService;

    @BeforeEach
    void setUp() {
        plugin = new DocumentInspectorPlugin();
        eventBus = new EventBus();
        uiService = new RecordingUIService();
    }

    @Test
    void testPluginIdAndName() {
        assertEquals("com.dogsbay.document-inspector", plugin.getId());
        assertEquals("Document Inspector", plugin.getName());
    }

    @Test
    void testPluginActivatesWithoutError() throws Exception {
        PluginContext ctx = createContext();
        SwingUtilities.invokeAndWait(() -> {
            assertDoesNotThrow(() -> plugin.activate(ctx));
        });
    }

    @Test
    void testPluginRegistersBottomPanel() throws Exception {
        PluginContext ctx = createContext();
        SwingUtilities.invokeAndWait(() -> plugin.activate(ctx));
        assertTrue(uiService.bottomPanelTitles.contains("Inspector"),
                "Should register 'Inspector' bottom panel");
    }

    @Test
    void testPluginRegistersMenuItem() throws Exception {
        PluginContext ctx = createContext();
        SwingUtilities.invokeAndWait(() -> plugin.activate(ctx));
        assertEquals(1, uiService.menuItems.size(), "Should register one menu item");
        assertEquals("Tools", uiService.menuItems.get(0).menuName);
    }

    @Test
    void testPluginDeactivatesWithoutError() throws Exception {
        PluginContext ctx = createContext();
        SwingUtilities.invokeAndWait(() -> {
            plugin.activate(ctx);
            assertDoesNotThrow(() -> plugin.deactivate());
        });
    }

    @Test
    void testDeactivateWithoutActivateDoesNotThrow() {
        assertDoesNotThrow(() -> plugin.deactivate());
    }

    // --- Tabbed pane tests ---

    @Test
    void testPanelHasTwoTabs() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            DocumentInspectorPanel panel = new DocumentInspectorPanel(ctx);
            assertEquals(2, panel.getTabbedPane().getTabCount());
            assertEquals("Links", panel.getTabbedPane().getTitleAt(0));
            assertEquals("Changes", panel.getTabbedPane().getTitleAt(1));
        });
    }

    @Test
    void testNullDocumentHandledGracefully() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            DocumentInspectorPanel panel = new DocumentInspectorPanel(ctx);
            assertDoesNotThrow(() -> panel.setDocument(null));
        });
    }

    // --- Link checker tests ---

    @Test
    void testLinkCheckerWithXmlHrefs(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("target.xml"), "<root/>", StandardCharsets.UTF_8);

        String xml = "<?xml version=\"1.0\"?>\n"
                + "<root>\n"
                + "  <link href=\"target.xml\"/>\n"
                + "  <link href=\"missing.xml\"/>\n"
                + "  <link href=\"https://example.com\"/>\n"
                + "</root>";

        DogsBayDocument doc = new DogsBayDocument(xml);
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);
            linkPanel.setDocument(doc);

            File baseDir = tempDir.toFile();
            List<LinkCheckerPanel.LinkEntry> links = linkPanel.checkXmlLinks(baseDir);

            assertEquals(3, links.size(), "Should find 3 links");
            assertEquals("target.xml", links.get(0).target);
            assertEquals("OK", links.get(0).status);
            assertEquals("missing.xml", links.get(1).target);
            assertEquals("Not Found", links.get(1).status);
            assertEquals("https://example.com", links.get(2).target);
            assertEquals("External", links.get(2).status);
        });
    }

    @Test
    void testLinkCheckerResolvesRelativePaths(@TempDir Path tempDir) throws Exception {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file.xml"), "<root/>", StandardCharsets.UTF_8);

        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);

            LinkCheckerPanel.LinkEntry entry = linkPanel.resolveLink(
                    "sub/file.xml", "href", tempDir.toFile(), 1);
            assertEquals("OK", entry.status);

            LinkCheckerPanel.LinkEntry missing = linkPanel.resolveLink(
                    "sub/nope.xml", "href", tempDir.toFile(), 1);
            assertEquals("Not Found", missing.status);
        });
    }

    @Test
    void testLinkCheckerExternalUrls() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);

            assertEquals("External",
                    linkPanel.resolveLink("http://example.com", "href", null, 1).status);
            assertEquals("External",
                    linkPanel.resolveLink("https://example.com", "href", null, 1).status);
            assertEquals("External",
                    linkPanel.resolveLink("mailto:test@example.com", "href", null, 1).status);
        });
    }

    @Test
    void testLinkCheckerFragmentOnly() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);
            LinkCheckerPanel.LinkEntry entry = linkPanel.resolveLink(
                    "#section1", "href", null, 1);
            assertEquals("Fragment", entry.status);
        });
    }

    @Test
    void testLinkCheckerNullBaseDir() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);
            LinkCheckerPanel.LinkEntry entry = linkPanel.resolveLink(
                    "file.xml", "href", null, 1);
            assertEquals("Error", entry.status);
        });
    }

    @Test
    void testLinkCheckerNoDocumentOpen() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            LinkCheckerPanel linkPanel = new LinkCheckerPanel(ctx);
            linkPanel.setDocument(null);
            assertDoesNotThrow(() -> linkPanel.checkLinks());
        });
    }

    // --- Changes panel tests ---

    @Test
    void testChangesPanelNoDocument() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            ChangesPanel changesPanel = new ChangesPanel(ctx);
            changesPanel.setDocument(null);
            assertDoesNotThrow(() -> changesPanel.refreshDiff());
        });
    }

    @Test
    void testChangesPanelNeedsRefreshAfterSetDocument() throws Exception {
        String xml = "<root/>";
        DogsBayDocument doc = new DogsBayDocument(xml);
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            ChangesPanel changesPanel = new ChangesPanel(ctx);
            changesPanel.setDocument(doc);
            assertTrue(changesPanel.needsRefresh());
        });
    }

    @Test
    void testChangesPanelMarkDirty() throws Exception {
        PluginContext ctx = createContext();

        SwingUtilities.invokeAndWait(() -> {
            ChangesPanel changesPanel = new ChangesPanel(ctx);
            assertTrue(changesPanel.needsRefresh());
            changesPanel.refreshDiff();
            assertFalse(changesPanel.needsRefresh());
            changesPanel.markDirty();
            assertTrue(changesPanel.needsRefresh());
        });
    }

    @Test
    void testDiffComputationIdenticalContent() {
        String[] lines = {"line1", "line2", "line3"};
        List<ChangesPanel.DiffLine> diff = ChangesPanel.computeDiff(lines, lines);
        boolean hasChanges = diff.stream()
                .anyMatch(d -> d.type == ChangesPanel.DiffType.ADDED
                        || d.type == ChangesPanel.DiffType.REMOVED);
        assertFalse(hasChanges, "Identical content should have no changes");
    }

    @Test
    void testDiffComputationWithAddedLines() {
        String[] oldLines = {"line1", "line3"};
        String[] newLines = {"line1", "line2", "line3"};
        List<ChangesPanel.DiffLine> diff = ChangesPanel.computeDiff(oldLines, newLines);

        long addedCount = diff.stream()
                .filter(d -> d.type == ChangesPanel.DiffType.ADDED).count();
        assertEquals(1, addedCount);

        boolean hasLine2 = diff.stream()
                .anyMatch(d -> d.type == ChangesPanel.DiffType.ADDED && "line2".equals(d.text));
        assertTrue(hasLine2);
    }

    @Test
    void testDiffComputationWithRemovedLines() {
        String[] oldLines = {"line1", "line2", "line3"};
        String[] newLines = {"line1", "line3"};
        List<ChangesPanel.DiffLine> diff = ChangesPanel.computeDiff(oldLines, newLines);

        long removedCount = diff.stream()
                .filter(d -> d.type == ChangesPanel.DiffType.REMOVED).count();
        assertEquals(1, removedCount);
    }

    @Test
    void testDiffComputationEmptyInputs() {
        List<ChangesPanel.DiffLine> diff = ChangesPanel.computeDiff(
                new String[0], new String[0]);
        assertNotNull(diff);
    }

    @Test
    void testLinkTableModelBasics() {
        LinkCheckerPanel.LinkTableModel model = new LinkCheckerPanel.LinkTableModel();
        assertEquals(0, model.getRowCount());
        assertEquals(4, model.getColumnCount());
        assertEquals("Target", model.getColumnName(0));
        assertEquals("Status", model.getColumnName(2));
    }

    // --- Test helpers ---

    private PluginContext createContext() {
        return new PluginContext() {
            @Override
            public DocumentManager getDocumentManager() { return null; }
            @Override
            public ViewManager getViewManager() { return null; }
            @Override
            public SchemaManager getSchemaManager() { return null; }
            @Override
            public ActionRegistry getActionRegistry() { return null; }
            @Override
            public EventBus getEventBus() { return eventBus; }
            @Override
            public ToolbarManager getToolbarManager() { return null; }
            @Override
            public UIService getUIService() { return uiService; }
            @Override
            public void registerFormat(com.dogsbay.xml.editor.DocumentFormat format) {}
        };
    }

    static class RecordingUIService implements UIService {
        final List<String> bottomPanelTitles = new ArrayList<>();
        final List<MenuItemRecord> menuItems = new ArrayList<>();
        String lastStatusMessage;

        @Override public void addMenu(JMenu menu) {}
        @Override public void addMenuItem(String menuName, JMenuItem item) {
            menuItems.add(new MenuItemRecord(menuName, item));
        }
        @Override public void removeMenuItem(String menuName, JMenuItem item) {}
        @Override public void addMenuItem(String menuName, String submenuName, JMenuItem item) {
            menuItems.add(new MenuItemRecord(menuName + "/" + submenuName, item));
        }
        @Override public void addMenuItemBefore(String menuName, String beforeItemText,
                JMenuItem item) {
            menuItems.add(new MenuItemRecord(menuName, item));
        }
        @Override public void removeMenuItem(String menuName, String submenuName,
                JMenuItem item) {}
        @Override public void addStatusBarItem(String id, JComponent item) {}
        @Override public void removeStatusBarItem(String id) {}
        @Override public void addToolbarButton(Action action) {}
        @Override public void addTab(String title, String tooltip, JComponent panel, Runnable onClose) {}
        @Override public void removeTab(String title) {}
        @Override public void addBottomPanel(String title, JComponent panel) {
            bottomPanelTitles.add(title);
        }
        @Override public void setStatusMessage(String message) {
            lastStatusMessage = message;
        }
        @Override public void addSidebarPanel(String id, javax.swing.Icon icon, String tooltip,
                                              JComponent panel, SidebarPosition position) {}
        @Override public void removeSidebarPanel(String id, SidebarPosition position) {}
        @Override public void removeBottomPanel(String title) {}
        @Override public void showSidebarPanel(String id, SidebarPosition position) {}
        @Override public void addEditorPopupContributor(
                com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor) {}
        @Override public void removeEditorPopupContributor(
                com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor) {}
        @Override public void addAttributeValueProvider(
                com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider) {}
        @Override public void removeAttributeValueProvider(
                com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider) {}
        @Override public javax.swing.Icon loadSidebarIcon(String resourcePath) { return null; }

        record MenuItemRecord(String menuName, JMenuItem item) {}
    }
}
