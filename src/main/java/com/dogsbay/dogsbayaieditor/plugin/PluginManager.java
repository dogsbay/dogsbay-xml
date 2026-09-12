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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Discovers, loads, and manages the lifecycle of new-style plugins.
 *
 * <p>Plugins are discovered using the standard Java {@link ServiceLoader}
 * mechanism. They can be on the classpath (META-INF/services) or loaded
 * from JAR files in a plugin directory.
 *
 * <p>Supports enable/disable with state persisted to configuration.
 */
public class PluginManager {

    private static final Logger LOG = LoggerFactory.getLogger(PluginManager.class);

    /** Config key for disabled plugin IDs. */
    static final String CONFIG_DISABLED_PLUGIN = "disabled-plugin";

    private final DogsBayAIEditor editor;
    private final PluginContext context;

    /** All discovered plugins in discovery order. */
    private final List<Plugin> plugins = new ArrayList<>();

    /** Quick lookup by ID. */
    private final Map<String, Plugin> pluginMap = new LinkedHashMap<>();

    /** IDs of disabled plugins (persisted to config). */
    private final Set<String> disabledPluginIds = new LinkedHashSet<>();

    /** IDs of currently activated plugins. */
    private final Set<String> activePluginIds = new LinkedHashSet<>();

    public PluginManager(DogsBayAIEditor editor) {
        this.editor = editor;
        this.context = new DefaultPluginContext(editor);
    }

    /**
     * Load disabled plugin IDs from configuration.
     */
    public void loadState(ConfigurationProperties config) {
        disabledPluginIds.clear();
        if (config != null) {
            Vector<?> ids = config.getStringList(CONFIG_DISABLED_PLUGIN);
            if (ids != null) {
                for (Object id : ids) {
                    if (id instanceof String) {
                        disabledPluginIds.add((String) id);
                    }
                }
            }
        }
        LOG.debug("Loaded disabled plugins: {}", disabledPluginIds);
    }

    /**
     * Save disabled plugin IDs to configuration.
     */
    public void saveState(ConfigurationProperties config) {
        if (config == null) return;

        // Remove all existing disabled-plugin entries
        Vector<?> existing = config.getStringList(CONFIG_DISABLED_PLUGIN);
        if (existing != null) {
            for (Object id : new ArrayList<>(existing)) {
                if (id instanceof String) {
                    config.remove(CONFIG_DISABLED_PLUGIN, (String) id);
                }
            }
        }

        // Write current disabled set
        for (String id : disabledPluginIds) {
            config.add(CONFIG_DISABLED_PLUGIN, id);
        }
        LOG.debug("Saved disabled plugins: {}", disabledPluginIds);
    }

    /**
     * Load plugins via ServiceLoader from the application classpath.
     */
    public void loadPlugins() {
        ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class);
        for (Plugin plugin : loader) {
            registerPlugin(plugin);
        }
    }

    /**
     * Load plugins from a directory containing JAR files.
     * Only discovers plugins from the JARs themselves — plugins already
     * found on the application classpath are skipped via duplicate ID check.
     */
    public void loadPlugins(File pluginDir) {
        if (pluginDir == null || !pluginDir.isDirectory()) {
            LOG.debug("Plugin directory does not exist or is not a directory: {}", pluginDir);
            return;
        }

        File[] jarFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            LOG.debug("No plugin JARs found in {}", pluginDir);
            return;
        }

        try {
            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
                LOG.debug("Loading plugin JAR: {}", jarFiles[i].getName());
            }

            // Use null parent to avoid re-discovering classpath plugins
            URLClassLoader pluginClassLoader = new URLClassLoader(urls, null);
            ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class, pluginClassLoader);

            for (Plugin plugin : loader) {
                registerPlugin(plugin);
            }
        } catch (Exception e) {
            LOG.error("Failed to load plugins from directory: {}", pluginDir, e);
        }
    }

    private void registerPlugin(Plugin plugin) {
        if (pluginMap.containsKey(plugin.getId())) {
            LOG.warn("Duplicate plugin ID '{}', skipping", plugin.getId());
            return;
        }
        plugins.add(plugin);
        pluginMap.put(plugin.getId(), plugin);
        LOG.info("Discovered plugin: {} ({}) v{}", plugin.getName(), plugin.getId(), plugin.getVersion());
    }

    /**
     * Activate all enabled plugins. Disabled plugins are skipped.
     */
    public void activateAll() {
        for (Plugin plugin : plugins) {
            if (disabledPluginIds.contains(plugin.getId())) {
                LOG.info("Skipping disabled plugin: {} ({})", plugin.getName(), plugin.getId());
                continue;
            }
            activatePlugin(plugin);
        }
    }

    /**
     * Deactivate all active plugins (called at shutdown).
     */
    public void deactivateAll() {
        for (Plugin plugin : plugins) {
            if (activePluginIds.contains(plugin.getId())) {
                deactivatePlugin(plugin);
            }
        }
    }

    /**
     * Is this plugin currently enabled?
     */
    public boolean isEnabled(String pluginId) {
        return !disabledPluginIds.contains(pluginId);
    }

    /**
     * Enable a plugin. Activates immediately if not already active.
     */
    public void enable(String pluginId) {
        disabledPluginIds.remove(pluginId);
        Plugin plugin = pluginMap.get(pluginId);
        if (plugin != null && !activePluginIds.contains(pluginId)) {
            activatePlugin(plugin);
        }
    }

    /**
     * Disable a plugin. Deactivates immediately if currently active.
     */
    public void disable(String pluginId) {
        disabledPluginIds.add(pluginId);
        Plugin plugin = pluginMap.get(pluginId);
        if (plugin != null && activePluginIds.contains(pluginId)) {
            deactivatePlugin(plugin);
        }
    }

    /**
     * Toggle a plugin. Returns new enabled state.
     */
    public boolean toggle(String pluginId) {
        if (isEnabled(pluginId)) {
            disable(pluginId);
            return false;
        } else {
            enable(pluginId);
            return true;
        }
    }

    private void activatePlugin(Plugin plugin) {
        try {
            plugin.activate(context);
            activePluginIds.add(plugin.getId());
            LOG.info("Activated plugin: {} ({})", plugin.getName(), plugin.getId());
        } catch (Exception e) {
            LOG.error("Failed to activate plugin: {} ({})", plugin.getName(), plugin.getId(), e);
        }
    }

    private void deactivatePlugin(Plugin plugin) {
        try {
            plugin.deactivate();
            activePluginIds.remove(plugin.getId());
            LOG.info("Deactivated plugin: {} ({})", plugin.getName(), plugin.getId());
        } catch (Exception e) {
            LOG.error("Failed to deactivate plugin: {} ({})", plugin.getName(), plugin.getId(), e);
        }
    }

    /**
     * Get all discovered plugins (both enabled and disabled).
     */
    public List<Plugin> getAllPlugins() {
        return Collections.unmodifiableList(plugins);
    }

    /**
     * Get only active plugins.
     */
    public List<Plugin> getActivePlugins() {
        List<Plugin> active = new ArrayList<>();
        for (Plugin plugin : plugins) {
            if (activePluginIds.contains(plugin.getId())) {
                active.add(plugin);
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Get all loaded plugins (unmodifiable). Legacy alias for getAllPlugins().
     */
    public List<Plugin> getPlugins() {
        return getAllPlugins();
    }

    /**
     * Look up an active plugin by its class. Returns null if the plugin
     * is not active (disabled or not found).
     */
    @SuppressWarnings("unchecked")
    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
        for (Plugin plugin : plugins) {
            if (pluginClass.isInstance(plugin) && activePluginIds.contains(plugin.getId())) {
                return (T) plugin;
            }
        }
        return null;
    }

    /**
     * Get the plugin context.
     */
    public PluginContext getContext() {
        return context;
    }
}
