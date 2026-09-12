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

package com.dogsbay.dogsbayaieditor.dita.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.dogsbay.xml.dita.DitaOtTransformer;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Dialog for publishing DITA maps via DITA-OT.
 *
 * @version $Revision: 1.0 $, $Date: 2026/02/25 $
 * @author DogsBay Ltd
 */
public class DitaPublishDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private static final String[] TRANSTYPES = { "html5", "pdf" };

    private final DogsBayAIEditor editor;
    private final File inputMapFile;
    private final ConfigurationProperties properties;
    /** The deliverable defining this map, when known — supplies the DITAVAL,
     *  transtype, output, and publication params so publish matches the project. */
    private final com.dogsbay.dogsbayaieditor.ditaproject.Deliverable deliverable;

    private JTextField inputMapField;
    private JComboBox<String> transtypeCombo;
    private JTextField outputDirField;

    public DitaPublishDialog(DogsBayAIEditor editor, File inputMapFile, ConfigurationProperties properties) {
        super(editor, "Publish DITA Map", true);
        this.editor = editor;
        this.inputMapFile = inputMapFile;
        this.properties = properties;
        this.deliverable = resolveDeliverable();

        initUI();
        pack();
        setLocationRelativeTo(editor);
    }

    /** Find the deliverable whose map is the one being published — the active one
     *  if it matches, else any deliverable in the project with that map; null if
     *  the map isn't a declared deliverable (publish then behaves as before). */
    private com.dogsbay.dogsbayaieditor.ditaproject.Deliverable resolveDeliverable() {
        var svc = editor.getDeliverableService();
        if (svc == null || svc.getContext() == null) {
            return null;
        }
        java.nio.file.Path mapPath = inputMapFile.toPath().toAbsolutePath().normalize();
        var active = svc.getActiveDeliverable();
        if (active != null && active.map() != null
                && active.map().toAbsolutePath().normalize().equals(mapPath)) {
            return active;
        }
        for (var d : svc.getContext().deliverables()) {
            if (d.map() != null && d.map().toAbsolutePath().normalize().equals(mapPath)) {
                return d;
            }
        }
        return null;
    }

    /** Human summary of the deliverable's DITAVAL filter(s) for the read-only field. */
    private String ditavalSummary() {
        if (deliverable == null || deliverable.ditavals().isEmpty()) {
            return "(none — unfiltered)";
        }
        StringBuilder sb = new StringBuilder();
        for (java.nio.file.Path dv : deliverable.ditavals()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(dv.getFileName());
        }
        return sb.toString();
    }

    /** The deliverable's declared output dir, resolved to an absolute path against
     *  the project file, or null when none. */
    private String defaultOutputDir() {
        if (deliverable == null || deliverable.output() == null) {
            return null;
        }
        java.nio.file.Path out = deliverable.output();
        if (!out.isAbsolute() && deliverable.sourceFile() != null) {
            out = deliverable.sourceFile().getParent().resolve(out).normalize();
        }
        return out.toString();
    }

    /** DITA-OT properties from the deliverable: its publication params (resolved)
     *  plus {@code args.filter} for its DITAVAL(s). Empty when no deliverable. */
    private java.util.Map<String, String> buildPublishParams() {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        if (deliverable == null) {
            return params;
        }
        java.nio.file.Path base = deliverable.sourceFile() != null
                ? deliverable.sourceFile().getParent() : null;
        for (com.dogsbay.dogsbayaieditor.ditaproject.Param p : deliverable.params()) {
            params.put(p.name(), base != null ? p.resolve(base) : p.value());
        }
        // Open review proposals: struck text stays out of the output. The generated
        // filter goes first; the deliverable's own args.filter param, if any, joins after.
        java.nio.file.Path work;
        try {
            work = java.nio.file.Files.createTempDirectory("dogsbay-publish");
        } catch (java.io.IOException e) {
            work = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        }
        StringBuilder sb = new StringBuilder();
        for (java.io.File dv : com.dogsbay.xml.review.ReviewDitaval.prepend(deliverable.ditavals(), work)) {
            if (sb.length() > 0) {
                sb.append(java.io.File.pathSeparator);
            }
            sb.append(dv.getAbsolutePath());
        }
        String own = params.get("args.filter");
        if (own != null && !own.isBlank()) {
            sb.append(java.io.File.pathSeparator).append(own.trim());
        }
        params.put("args.filter", sb.toString());
        return params;
    }

    private void initUI() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Input map (read-only)
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Input Map:"), gbc);

        inputMapField = new JTextField(inputMapFile.getAbsolutePath(), 35);
        inputMapField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(inputMapField, gbc);

        // Transtype
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Transtype:"), gbc);

        transtypeCombo = new JComboBox<>(TRANSTYPES);
        // Prefer the deliverable's transtype (adding it if not a built-in option),
        // else the last-used one.
        String preferred = (deliverable != null && deliverable.transtype() != null)
                ? deliverable.transtype() : properties.getDitaPublishTranstype();
        if (preferred != null && !preferred.isBlank()) {
            boolean found = false;
            for (int i = 0; i < transtypeCombo.getItemCount(); i++) {
                if (preferred.equals(transtypeCombo.getItemAt(i))) {
                    transtypeCombo.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                transtypeCombo.addItem(preferred);
                transtypeCombo.setSelectedItem(preferred);
            }
        }
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(transtypeCombo, gbc);

        int row = 2;
        // DITAVAL filter (read-only) — shown when this map is a known deliverable,
        // so the user sees publish will apply the deliverable's conditions.
        if (deliverable != null) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;
            formPanel.add(new JLabel("Filter (DITAVAL):"), gbc);

            JTextField ditavalField = new JTextField(ditavalSummary(), 35);
            ditavalField.setEditable(false);
            gbc.gridx = 1;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            formPanel.add(ditavalField, gbc);
            row++;
        }

        // Output folder
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Output Folder:"), gbc);

        // Prefer the deliverable's declared <output>; fall back to the last-used dir.
        String initialOutput = defaultOutputDir();
        if (initialOutput == null || initialOutput.isBlank()) {
            initialOutput = properties.getDitaPublishOutputDir();
        }
        outputDirField = new JTextField(initialOutput != null ? initialOutput : "", 30);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(outputDirField, gbc);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseOutputFolder());
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(browseButton, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton publishButton = new JButton("Publish");
        publishButton.addActionListener(e -> doPublish());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(publishButton);
        buttonPanel.add(cancelButton);
        getRootPane().setDefaultButton(publishButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void browseOutputFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = outputDirField.getText().trim();
        if (!current.isEmpty()) {
            File dir = new File(current);
            if (dir.exists()) {
                chooser.setCurrentDirectory(dir);
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void doPublish() {
        String outputDir = outputDirField.getText().trim();
        if (outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output folder.", "Missing Output Folder",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String ditaOtPath = editor.getDitaOtPath();
        if (ditaOtPath == null) {
            JOptionPane.showMessageDialog(this,
                    "No DITA-OT installation found.\nImport a framework with DITA-OT support via File \u2192 Import Framework...",
                    "No DITA-OT", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String transtype = (String) transtypeCombo.getSelectedItem();

        // Save preferences
        properties.setDitaPublishTranstype(transtype);
        properties.setDitaPublishOutputDir(outputDir);

        // Hide this dialog
        dispose();

        // Show progress dialog
        JDialog progressDialog = new JDialog(editor, "Publishing...", false);
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        progressPanel.add(new JLabel("Publishing " + inputMapFile.getName() + " as " + transtype + "..."),
                BorderLayout.NORTH);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressDialog.setContentPane(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(editor);
        progressDialog.setVisible(true);

        String inputMap = inputMapFile.getAbsolutePath();
        String finalOutputDir = outputDir;
        final java.util.Map<String, String> params = buildPublishParams();

        new SwingWorker<Boolean, Void>() {
            private Exception error;

            @Override
            protected Boolean doInBackground() {
                try {
                    DitaOtTransformer transformer = new DitaOtTransformer(ditaOtPath);
                    return transformer.transform(inputMap, finalOutputDir, transtype, params);
                } catch (Exception e) {
                    error = e;
                    return false;
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    boolean success = get();
                    if (success) {
                        int choice = JOptionPane.showOptionDialog(editor,
                                "DITA-OT publish completed successfully.",
                                "Publish Complete", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                                null, new String[] { "Open Output Folder", "OK" }, "OK");
                        if (choice == 0) {
                            openOutputFolder(new File(finalOutputDir));
                        }
                    } else {
                        String msg = (error != null) ? error.getMessage() : "Transformation returned failure.";
                        JOptionPane.showMessageDialog(editor, "Publish failed:\n" + msg, "Publish Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(editor, "Publish failed:\n" + e.getMessage(), "Publish Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openOutputFolder(File folder) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            }
        } catch (Exception e) {
            // Ignore — user can navigate manually
        }
    }
}
