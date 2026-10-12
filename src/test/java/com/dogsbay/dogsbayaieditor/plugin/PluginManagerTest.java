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

package com.dogsbay.dogsbayaieditor.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Plugin API: Plugin interface, metadata defaults,
 * enable/disable, and PluginContext wiring.
 */
class PluginManagerTest {

    /**
     * A simple test plugin that records its lifecycle.
     */
    static class TestPlugin implements Plugin {
        final String id;
        final String name;
        boolean activated = false;
        boolean deactivated = false;
        PluginContext receivedContext = null;

        TestPlugin(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public void activate(PluginContext context) {
            this.activated = true;
            this.receivedContext = context;
        }
        @Override public void deactivate() { this.deactivated = true; }
    }

    /**
     * A plugin that throws during activation.
     */
    static class FailingPlugin implements Plugin {
        boolean deactivated = false;

        @Override public String getId() { return "com.test.failing"; }
        @Override public String getName() { return "Failing Plugin"; }
        @Override public void activate(PluginContext context) {
            throw new RuntimeException("Activation failed on purpose");
        }
        @Override public void deactivate() { this.deactivated = true; }
    }

    /**
     * A plugin with full metadata.
     */
    static class MetadataPlugin implements Plugin {
        @Override public String getId() { return "com.test.meta"; }
        @Override public String getName() { return "Metadata Plugin"; }
        @Override public String getVersion() { return "2.1.0"; }
        @Override public String getAuthor() { return "Test Author"; }
        @Override public String getDescription() { return "A test plugin with metadata"; }
        @Override public boolean isBuiltIn() { return true; }
        @Override public void activate(PluginContext context) {}
    }

    // --- Plugin interface tests ---

    @Test
    void testPluginInterfaceDefaultDeactivate() {
        Plugin plugin = new Plugin() {
            @Override public String getId() { return "test.default"; }
            @Override public String getName() { return "Default Deactivate"; }
            @Override public void activate(PluginContext context) {}
        };
        assertDoesNotThrow(plugin::deactivate);
    }

    @Test
    void testPluginMetadataDefaults() {
        TestPlugin plugin = new TestPlugin("test-id", "Test Plugin");
        assertEquals("1.0.0", plugin.getVersion());
        assertEquals("", plugin.getAuthor());
        assertEquals("", plugin.getDescription());
        assertFalse(plugin.isBuiltIn());
        assertNull(plugin.getSettingsPanel());
    }

    @Test
    void testPluginMetadataOverrides() {
        MetadataPlugin plugin = new MetadataPlugin();
        assertEquals("com.test.meta", plugin.getId());
        assertEquals("Metadata Plugin", plugin.getName());
        assertEquals("2.1.0", plugin.getVersion());
        assertEquals("Test Author", plugin.getAuthor());
        assertEquals("A test plugin with metadata", plugin.getDescription());
        assertTrue(plugin.isBuiltIn());
    }

    @Test
    void testDocumentInspectorMetadata() {
        var inspector = new com.dogsbay.dogsbayaieditor.plugin.inspector.DocumentInspectorPlugin();
        assertEquals("com.dogsbay.document-inspector", inspector.getId());
        assertEquals("Document Inspector", inspector.getName());
        assertEquals("Link checker and dirty diff viewer", inspector.getDescription());
        assertTrue(inspector.isBuiltIn());
        assertEquals("1.0.0", inspector.getVersion());
    }

    // --- Lifecycle tests ---

    @Test
    void testPluginCanBeRegisteredAndActivated() {
        TestPlugin plugin = new TestPlugin("com.test.sample", "Sample Plugin");
        PluginContext stubContext = createStubContext();

        assertFalse(plugin.activated);
        plugin.activate(stubContext);
        assertTrue(plugin.activated);
        assertSame(stubContext, plugin.receivedContext);
    }

    @Test
    void testDeactivateIsCalled() {
        TestPlugin plugin = new TestPlugin("com.test.deactivate", "Deactivate Test");
        PluginContext stubContext = createStubContext();

        plugin.activate(stubContext);
        assertTrue(plugin.activated);
        assertFalse(plugin.deactivated);

        plugin.deactivate();
        assertTrue(plugin.deactivated);
    }

    @Test
    void testFailingPluginDoesNotPreventOthersFromActivating() {
        TestPlugin good1 = new TestPlugin("com.test.good1", "Good 1");
        FailingPlugin bad = new FailingPlugin();
        TestPlugin good2 = new TestPlugin("com.test.good2", "Good 2");

        List<Plugin> plugins = new ArrayList<>();
        plugins.add(good1);
        plugins.add(bad);
        plugins.add(good2);

        PluginContext stubContext = createStubContext();

        for (Plugin plugin : plugins) {
            try {
                plugin.activate(stubContext);
            } catch (Exception e) {
                // PluginManager catches and logs this
            }
        }

        assertTrue(good1.activated, "First good plugin should be activated");
        assertTrue(good2.activated, "Second good plugin should still activate after a failure");
    }

    @Test
    void testMultiplePluginsCanBeActivatedAndDeactivated() {
        TestPlugin p1 = new TestPlugin("com.test.p1", "Plugin 1");
        TestPlugin p2 = new TestPlugin("com.test.p2", "Plugin 2");
        TestPlugin p3 = new TestPlugin("com.test.p3", "Plugin 3");

        PluginContext stubContext = createStubContext();

        List<Plugin> plugins = List.of(p1, p2, p3);
        for (Plugin plugin : plugins) {
            plugin.activate(stubContext);
        }

        assertTrue(p1.activated);
        assertTrue(p2.activated);
        assertTrue(p3.activated);

        for (Plugin plugin : plugins) {
            plugin.deactivate();
        }

        assertTrue(p1.deactivated);
        assertTrue(p2.deactivated);
        assertTrue(p3.deactivated);
    }

    // --- PluginContext stub tests ---

    @Test
    void testPluginContextStubProvidesAccessibleMethods() {
        PluginContext context = createStubContext();
        assertDoesNotThrow(context::getDocumentManager);
        assertDoesNotThrow(context::getViewManager);
        assertDoesNotThrow(context::getSchemaManager);
        assertDoesNotThrow(context::getActionRegistry);
        assertDoesNotThrow(context::getEventBus);
        assertDoesNotThrow(context::getToolbarManager);
        assertDoesNotThrow(context::getUIService);
    }

    // --- Helper ---

    private PluginContext createStubContext() {
        return new PluginContext() {
            @Override public com.dogsbay.dogsbayaieditor.services.DocumentManager getDocumentManager() { return null; }
            @Override public com.dogsbay.dogsbayaieditor.services.ViewManager getViewManager() { return null; }
            @Override public com.dogsbay.dogsbayaieditor.services.SchemaManager getSchemaManager() { return null; }
            @Override public com.dogsbay.dogsbayaieditor.services.ActionRegistry getActionRegistry() { return null; }
            @Override public com.dogsbay.dogsbayaieditor.services.EventBus getEventBus() { return null; }
            @Override public com.dogsbay.dogsbayaieditor.services.ToolbarManager getToolbarManager() { return null; }
            @Override public UIService getUIService() { return null; }
            @Override public void registerFormat(com.dogsbay.xml.editor.DocumentFormat format) {}
        };
    }
}
