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

package com.dogsbay.dogsbayaieditor.framework;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Dialog for importing a Framework from a local directory or GitHub URL.
 *
 * <p>Layout:
 * <ul>
 *   <li>Source: text field + Browse button (local) / GitHub URL</li>
 *   <li>Install location: Default radio / Custom radio + path field</li>
 *   <li>Progress label</li>
 *   <li>Import / Cancel buttons</li>
 * </ul>
 */
public class FrameworkImportDialog extends JDialog {

    private final ConfigurationProperties config;

    private JTextField sourceField;
    private JTextField customInstallField;
    private JRadioButton defaultInstallRadio;
    private JRadioButton customInstallRadio;
    private JLabel progressLabel;
    private JProgressBar progressBar;
    private JButton importButton;

    private FrameworkProperties importedFramework;

    public FrameworkImportDialog(Frame owner, ConfigurationProperties config) {
        super(owner, "Import Framework", true);
        this.config = config;
        buildUI();
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        // --- Source panel ---
        JPanel sourcePanel = new JPanel(new GridBagLayout());
        sourcePanel.setBorder(BorderFactory.createTitledBorder("Framework source"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        sourcePanel.add(new JLabel("Path or GitHub URL:"), gbc);

        sourceField = new JTextField(36);
        gbc.gridx = 1; gbc.weightx = 1;
        sourcePanel.add(sourceField, gbc);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("Select framework directory");
                if (chooser.showOpenDialog(FrameworkImportDialog.this) == JFileChooser.APPROVE_OPTION) {
                    sourceField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            }
        });
        gbc.gridx = 2; gbc.weightx = 0;
        sourcePanel.add(browseButton, gbc);

        content.add(sourcePanel, BorderLayout.NORTH);

        // --- Install location panel ---
        JPanel installPanel = new JPanel(new GridBagLayout());
        installPanel.setBorder(BorderFactory.createTitledBorder("Install location"));

        defaultInstallRadio = new JRadioButton("Default (~/.dogsbay/frameworks/)", true);
        customInstallRadio  = new JRadioButton("Custom:");
        ButtonGroup bg = new ButtonGroup();
        bg.add(defaultInstallRadio);
        bg.add(customInstallRadio);

        customInstallField = new JTextField(28);
        customInstallField.setEnabled(false);

        JButton installBrowse = new JButton("Browse...");
        installBrowse.setEnabled(false);
        installBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(FrameworkImportDialog.this) == JFileChooser.APPROVE_OPTION) {
                    customInstallField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        customInstallRadio.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean custom = customInstallRadio.isSelected();
                customInstallField.setEnabled(custom);
                installBrowse.setEnabled(custom);
            }
        });
        defaultInstallRadio.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                customInstallField.setEnabled(false);
                installBrowse.setEnabled(false);
            }
        });

        GridBagConstraints igbc = new GridBagConstraints();
        igbc.insets = new Insets(2, 4, 2, 4);
        igbc.anchor = GridBagConstraints.WEST;

        igbc.gridx = 0; igbc.gridy = 0; igbc.gridwidth = 3;
        installPanel.add(defaultInstallRadio, igbc);

        igbc.gridy = 1; igbc.gridwidth = 1;
        installPanel.add(customInstallRadio, igbc);

        igbc.gridx = 1; igbc.fill = GridBagConstraints.HORIZONTAL; igbc.weightx = 1;
        installPanel.add(customInstallField, igbc);

        igbc.gridx = 2; igbc.weightx = 0;
        installPanel.add(installBrowse, igbc);

        content.add(installPanel, BorderLayout.CENTER);

        // --- Bottom: progress + buttons ---
        JPanel bottom = new JPanel(new BorderLayout(4, 4));

        JPanel progressPanel = new JPanel(new BorderLayout(0, 2));
        progressLabel = new JLabel(" ");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.SOUTH);
        bottom.add(progressPanel, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        importButton = new JButton("Import");
        importButton.setMnemonic('I');
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startImport();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setMnemonic('C');
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        buttons.add(importButton);
        buttons.add(cancelButton);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        getContentPane().add(content);
        getRootPane().setDefaultButton(importButton);
    }

    private void startImport() {
        final String source = sourceField.getText().trim();
        if (source.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a source path or GitHub URL.", "Import Framework",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        final File installDir;
        if (customInstallRadio.isSelected()) {
            String custom = customInstallField.getText().trim();
            if (custom.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a custom install directory.", "Import Framework",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            installDir = new File(custom);
        } else {
            installDir = null; // use default
        }

        importButton.setEnabled(false);
        progressLabel.setText("Importing...");

        FrameworkImporter importer = new FrameworkImporter(config);
        importer.setProgressListener(new FrameworkImporter.ProgressListener() {
            public void onProgress(String message) {
                SwingUtilities.invokeLater(() -> progressLabel.setText(message));
            }

            public void onBytes(long downloaded, long total) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(true);
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setValue((int) Math.min(100, (downloaded * 100) / total));
                        progressBar.setString(formatBytes(downloaded) + " / " + formatBytes(total));
                    } else {
                        progressBar.setIndeterminate(true);
                        progressBar.setString(formatBytes(downloaded));
                    }
                });
            }
        });

        SwingWorker<FrameworkProperties, Void> worker = new SwingWorker<FrameworkProperties, Void>() {
            protected FrameworkProperties doInBackground() throws Exception {
                if (source.startsWith("https://github.com/") || source.startsWith("http://github.com/")) {
                    return importer.importFromGitHub(source, installDir);
                } else {
                    return importer.importFromDirectory(new File(source), installDir);
                }
            }

            protected void done() {
                progressBar.setVisible(false);
                try {
                    importedFramework = get();
                    progressLabel.setText("Import complete: " + importedFramework.getName());
                    JOptionPane.showMessageDialog(FrameworkImportDialog.this,
                            "Framework '" + importedFramework.getName() + "' imported successfully.",
                            "Import Framework", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    progressLabel.setText("Import failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(FrameworkImportDialog.this,
                            "Import failed:\n" + cause.getMessage(),
                            "Import Framework", JOptionPane.ERROR_MESSAGE);
                    importButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /** @return the imported FrameworkProperties, or null if cancelled. */
    public FrameworkProperties getImportedFramework() {
        return importedFramework;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
