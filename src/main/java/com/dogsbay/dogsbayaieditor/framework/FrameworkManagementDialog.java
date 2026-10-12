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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Dialog that lists installed frameworks and allows importing, removing, or
 * updating them.
 */
public class FrameworkManagementDialog extends JDialog {

    private final ConfigurationProperties config;
    private final Frame owner;

    private DefaultListModel<String> listModel;
    private JList<String> frameworkList;
    private JButton removeButton;
    private JButton updateButton;
    private JLabel detailLabel;

    public FrameworkManagementDialog(Frame owner, ConfigurationProperties config) {
        super(owner, "Manage Frameworks", true);
        this.config = config;
        this.owner = owner;
        buildUI();
        refreshList();
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        // Framework list
        listModel = new DefaultListModel<>();
        frameworkList = new JList<>(listModel);
        frameworkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        frameworkList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                updateButtons();
                updateDetail();
            }
        });

        JScrollPane scroll = new JScrollPane(frameworkList);
        scroll.setBorder(BorderFactory.createTitledBorder("Installed frameworks"));
        content.add(scroll, BorderLayout.CENTER);

        // Detail label
        detailLabel = new JLabel(" ");
        content.add(detailLabel, BorderLayout.NORTH);

        // Action buttons (right side)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        JButton importButton = new JButton("Import New...");
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                FrameworkImportDialog dlg = new FrameworkImportDialog(owner, config);
                dlg.setVisible(true);
                refreshList();
            }
        });

        removeButton = new JButton("Remove");
        removeButton.setEnabled(false);
        removeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeSelected();
            }
        });

        updateButton = new JButton("Update");
        updateButton.setEnabled(false);
        updateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateSelected();
            }
        });

        actionPanel.add(importButton);
        actionPanel.add(removeButton);
        actionPanel.add(updateButton);
        content.add(actionPanel, BorderLayout.SOUTH);

        getContentPane().add(content);
    }

    private void refreshList() {
        listModel.clear();
        Vector frameworks = config.getFrameworkProperties();
        for (int i = 0; i < frameworks.size(); i++) {
            FrameworkProperties p = (FrameworkProperties) frameworks.elementAt(i);
            String entry = p.getName();
            if (p.getVersion() != null && !p.getVersion().isEmpty()) {
                entry += "  v" + p.getVersion();
            }
            listModel.addElement(entry);
        }
        updateButtons();
        updateDetail();
    }

    private void updateButtons() {
        boolean selected = frameworkList.getSelectedIndex() >= 0;
        removeButton.setEnabled(selected);
        // Update only available if framework has a source URL
        FrameworkProperties p = getSelectedFramework();
        updateButton.setEnabled(p != null && p.getSourceUrl() != null && !p.getSourceUrl().isEmpty());
    }

    private void updateDetail() {
        FrameworkProperties p = getSelectedFramework();
        if (p == null) {
            detailLabel.setText(" ");
        } else {
            String path = p.getFolderPath() != null ? p.getFolderPath() : "(unknown)";
            detailLabel.setText("<html><b>" + p.getName() + "</b> &mdash; " + path + "</html>");
        }
    }

    private FrameworkProperties getSelectedFramework() {
        int idx = frameworkList.getSelectedIndex();
        if (idx < 0) return null;
        Vector frameworks = config.getFrameworkProperties();
        if (idx >= frameworks.size()) return null;
        return (FrameworkProperties) frameworks.elementAt(idx);
    }

    private void removeSelected() {
        FrameworkProperties p = getSelectedFramework();
        if (p == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove framework '" + p.getName() + "'?\n"
                + "This will deregister the framework but will NOT delete installed files.",
                "Remove Framework", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        FrameworkImporter importer = new FrameworkImporter(config);
        importer.removeFramework(p);
        refreshList();
    }

    private void updateSelected() {
        FrameworkProperties p = getSelectedFramework();
        if (p == null) return;

        String sourceUrl = p.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No source URL recorded for this framework.",
                    "Update Framework", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String folderPath = p.getFolderPath();
        final java.io.File installDir = folderPath != null ? new java.io.File(folderPath) : null;

        // Remove old registration then re-import
        FrameworkImporter importer = new FrameworkImporter(config);
        importer.removeFramework(p);

        final String url = sourceUrl;
        SwingWorker<FrameworkProperties, Void> worker = new SwingWorker<FrameworkProperties, Void>() {
            protected FrameworkProperties doInBackground() throws Exception {
                return new FrameworkImporter(config).importFromGitHub(url, installDir);
            }

            protected void done() {
                try {
                    FrameworkProperties updated = get();
                    JOptionPane.showMessageDialog(FrameworkManagementDialog.this,
                            "Framework '" + updated.getName() + "' updated.",
                            "Update Framework", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(FrameworkManagementDialog.this,
                            "Update failed:\n" + cause.getMessage(),
                            "Update Framework", JOptionPane.ERROR_MESSAGE);
                }
                refreshList();
            }
        };
        worker.execute();
    }
}
