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
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataExtractor;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataEvaluator;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataField;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy;
import com.dogsbay.dogsbayaieditor.links.metadata.MetadataSnapshot;
import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/**
 * Right-sidebar panel showing the active DITA document's prolog/topicmeta metadata
 * and its compliance with the project's required-metadata policy. Read + policy
 * status here; bulk editing is via Project → Normalize Metadata… (the button).
 */
public class MetadataPanel extends JPanel {

    private final DogsBayAIEditor editor;
    private final JTextArea area = new JTextArea();

    public MetadataPanel(DogsBayAIEditor editor) {
        super(new BorderLayout());
        this.editor = editor;

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JButton normalize = new JButton("Normalize…");
        normalize.addActionListener(e -> editor.normalizeMetadata());
        bar.add(refresh);
        bar.add(normalize);

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
        refresh();
    }

    /** Re-read the active document's metadata + policy status and render it. */
    public void refresh() {
        File file = activeDitaFile();
        if (file == null) {
            area.setText("Open a DITA topic or map to see its metadata.");
            return;
        }
        MetadataSnapshot snap = MetadataExtractor.extract(file);
        StringBuilder sb = new StringBuilder();
        sb.append(snap.rootType().isEmpty() ? file.getName() : snap.rootType())
          .append(" — ").append(file.getName()).append("\n\n");

        sb.append("Metadata\n");
        boolean any = false;
        for (MetadataField f : MetadataField.values()) {
            List<String> values = snap.get(f);
            if (!values.isEmpty()) {
                sb.append("  ").append(f.key()).append(": ")
                  .append(String.join(", ", values)).append('\n');
                any = true;
            }
        }
        if (!any) {
            sb.append("  (none)\n");
        }

        MetadataPolicy policy = policy();
        if (!policy.isEmpty()) {
            List<MetadataFinding> findings =
                    MetadataEvaluator.evaluate(file.toString(), snap, policy);
            sb.append("\nPolicy\n");
            if (findings.isEmpty()) {
                sb.append("  ✓ compliant\n");
            } else {
                for (MetadataFinding f : findings) {
                    sb.append("  ").append("error".equals(f.severity()) ? "✗ " : "⚠ ")
                      .append(f.message()).append('\n');
                }
            }
        }
        area.setText(sb.toString());
        area.setCaretPosition(0);
    }

    private File activeDitaFile() {
        try {
            var doc = editor.getDocument();
            java.net.URL url = doc != null ? doc.getURL() : null;
            if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            File f = new File(url.toURI());
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            return (n.endsWith(".dita") || n.endsWith(".ditamap") || n.endsWith(".bookmap"))
                    ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private MetadataPolicy policy() {
        try {
            File root = editor.getFileExplorer() != null
                    ? editor.getFileExplorer().getRootDirectory() : null;
            if (root != null) {
                return DogsbayProjectConfig.load(root.toPath()).getMetadataPolicy();
            }
        } catch (Exception ignore) {
            // no project / no policy
        }
        return MetadataPolicy.empty();
    }
}
