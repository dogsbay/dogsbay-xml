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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel for managing plugins in the Preferences dialog.
 * Shows all plugins with checkboxes to enable/disable them.
 */
public class PluginManagerPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(PluginManagerPanel.class);

    private final PluginManager pluginManager;
    private final Map<String, JCheckBox> checkBoxes = new HashMap<>();
    private JPanel settingsContainer;

    public PluginManagerPanel(PluginManager pluginManager) {
        super(new BorderLayout());
        this.pluginManager = pluginManager;
        buildUI();
    }

    private void buildUI() {
        // Plugin list
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        List<Plugin> plugins = pluginManager.getAllPlugins();

        if (plugins.isEmpty()) {
            listPanel.add(new JLabel("No plugins installed."));
        } else {
            for (Plugin plugin : plugins) {
                listPanel.add(createPluginRow(plugin));
                listPanel.add(Box.createVerticalStrut(4));
            }
        }

        listPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Settings area (shown when a plugin has settings)
        settingsContainer = new JPanel(new BorderLayout());
        settingsContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        settingsContainer.setVisible(false);
        add(settingsContainer, BorderLayout.SOUTH);

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton openFolder = new JButton("Open Plugins Folder");
        openFolder.addActionListener(e -> openPluginsFolder());
        buttonPanel.add(openFolder);

        // Place buttons below settings
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(settingsContainer, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createPluginRow(Plugin plugin) {
        JPanel row = new JPanel(new BorderLayout());
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 48));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UIManager.getColor("Separator.foreground") != null
                                ? UIManager.getColor("Separator.foreground")
                                : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        // Checkbox with plugin name
        JCheckBox checkBox = new JCheckBox(plugin.getName());
        checkBox.setSelected(pluginManager.isEnabled(plugin.getId()));
        checkBox.addActionListener(e -> onToggle(plugin, checkBox.isSelected()));
        checkBoxes.put(plugin.getId(), checkBox);

        // Info label: built-in badge + version + description
        StringBuilder info = new StringBuilder();
        if (plugin.isBuiltIn()) {
            info.append("Built-in");
        }
        if (!plugin.getVersion().isEmpty()) {
            if (info.length() > 0) info.append("  |  ");
            info.append("v").append(plugin.getVersion());
        }
        if (!plugin.getDescription().isEmpty()) {
            if (info.length() > 0) info.append("  |  ");
            info.append(plugin.getDescription());
        }

        JLabel infoLabel = new JLabel(info.toString());
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground") != null
                ? UIManager.getColor("Label.disabledForeground")
                : Color.GRAY);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(checkBox, BorderLayout.NORTH);
        leftPanel.add(infoLabel, BorderLayout.SOUTH);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        row.add(leftPanel, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        return row;
    }

    private void onToggle(Plugin plugin, boolean enabled) {
        if (enabled) {
            pluginManager.enable(plugin.getId());
        } else {
            pluginManager.disable(plugin.getId());
        }
        showSettingsFor(plugin);
    }

    private void showSettingsFor(Plugin plugin) {
        settingsContainer.removeAll();
        JComponent settings = plugin.getSettingsPanel();
        if (settings != null) {
            settingsContainer.add(settings, BorderLayout.CENTER);
            settingsContainer.setVisible(true);
        } else {
            settingsContainer.setVisible(false);
        }
        settingsContainer.revalidate();
        settingsContainer.repaint();
    }

    private void openPluginsFolder() {
        try {
            File pluginsDir = new File("plugins");
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs();
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pluginsDir);
            }
        } catch (Exception e) {
            LOG.error("Failed to open plugins folder", e);
        }
    }

    /**
     * Save the current enable/disable state to the provided config.
     */
    public void saveState(com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties config) {
        pluginManager.saveState(config);
    }
}
