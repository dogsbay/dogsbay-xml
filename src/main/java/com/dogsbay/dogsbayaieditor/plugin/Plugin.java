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

/**
 * Interface for new-style plugins. Implement this and register via
 * META-INF/services/com.dogsbay.dogsbayaieditor.plugin.Plugin
 * or place a JAR containing the implementation in the plugins/ directory.
 *
 * <p>This is separate from the legacy plugin system
 * ({@code com.dogsbay.dogsbayaieditor.plugins.PluginView}) which remains as-is.
 */
public interface Plugin {

    /**
     * Unique plugin identifier (e.g. "com.example.my-plugin").
     */
    String getId();

    /**
     * Human-readable name shown in plugin listings.
     */
    String getName();

    /**
     * Plugin version string (e.g. "1.0.0").
     */
    default String getVersion() { return "1.0.0"; }

    /**
     * Plugin author or organization name.
     */
    default String getAuthor() { return ""; }

    /**
     * Short description of what the plugin does.
     */
    default String getDescription() { return ""; }

    /**
     * Whether this plugin is built-in (shipped with the application).
     * Built-in plugins can be disabled but not removed.
     */
    default boolean isBuiltIn() { return false; }

    /**
     * Optional settings panel shown in Preferences when this plugin is selected.
     * Return null if the plugin has no configurable settings.
     */
    default javax.swing.JComponent getSettingsPanel() { return null; }

    /**
     * Called once at startup with access to all application services.
     * Plugins should use the provided context to register UI contributions,
     * subscribe to events, etc.
     *
     * @param context provides access to application services
     */
    void activate(PluginContext context);

    /**
     * Called at shutdown. Release any resources acquired during activation.
     * The default implementation does nothing.
     */
    default void deactivate() {}
}
