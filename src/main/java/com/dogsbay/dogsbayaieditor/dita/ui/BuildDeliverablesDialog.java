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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/**
 * Checkbox picker for "Build Deliverables": one row per deliverable with the active
 * one pre-ticked. "Build All" builds everything; "Build Selected" builds the ticked
 * rows. Output folders come from each deliverable's {@code <output>} (project.json),
 * so this never prompts for a destination.
 */
public final class BuildDeliverablesDialog extends JDialog {

    private List<String> result = null; // null = cancelled

    /** Show the picker; returns the deliverable names to build, or null if cancelled. */
    public static List<String> choose(Frame owner, List<Deliverable> deliverables, Deliverable active) {
        BuildDeliverablesDialog d = new BuildDeliverablesDialog(owner, deliverables, active);
        d.setVisible(true);
        return d.result;
    }

    /** Names ticked when the dialog opens — just the active deliverable, others off. */
    static Set<String> initialSelection(Deliverable active) {
        return (active != null && active.name() != null) ? Set.of(active.name()) : Set.of();
    }

    private BuildDeliverablesDialog(Frame owner, List<Deliverable> deliverables, Deliverable active) {
        super(owner, "Build Deliverables", true);

        Set<String> initial = initialSelection(active);
        List<JCheckBox> boxes = new ArrayList<>();

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(8, 12, 8, 12));
        for (Deliverable d : deliverables) {
            StringBuilder label = new StringBuilder(d.name());
            if (d.transtype() != null && !d.transtype().isBlank()) {
                label.append("  —  ").append(d.transtype());
            }
            if (d.output() != null) {
                label.append("   →  ").append(d.output());
            }
            JCheckBox cb = new JCheckBox(label.toString(), initial.contains(d.name()));
            cb.putClientProperty("deliverable", d.name());
            boxes.add(cb);
            list.add(cb);
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(480, Math.min(320, 40 + deliverables.size() * 26)));

        JCheckBox selectAll = new JCheckBox("Select all");

        JButton buildAll = new JButton("Build All");
        JButton buildSelected = new JButton("Build Selected");
        JButton cancel = new JButton("Cancel");

        Runnable syncEnabled = () -> buildSelected.setEnabled(!checkedNames(boxes).isEmpty());
        for (JCheckBox b : boxes) {
            b.addActionListener(e -> syncEnabled.run());
        }
        selectAll.addActionListener(e -> {
            for (JCheckBox b : boxes) {
                b.setSelected(selectAll.isSelected());
            }
            syncEnabled.run();
        });
        syncEnabled.run();

        buildAll.addActionListener(e -> {
            result = allNames(deliverables);
            dispose();
        });
        buildSelected.addActionListener(e -> {
            result = checkedNames(boxes);
            dispose();
        });
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setBorder(new EmptyBorder(12, 12, 0, 12));
        north.add(new JLabel("Select deliverables to build "
                + "(output folders come from the project):"), BorderLayout.NORTH);
        north.add(selectAll, BorderLayout.SOUTH);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        south.add(buildAll);
        south.add(buildSelected);
        south.add(cancel);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(buildSelected);
        pack();
        setLocationRelativeTo(owner);
    }

    private static List<String> checkedNames(List<JCheckBox> boxes) {
        List<String> names = new ArrayList<>();
        for (JCheckBox b : boxes) {
            if (b.isSelected()) {
                names.add((String) b.getClientProperty("deliverable"));
            }
        }
        return names;
    }

    private static List<String> allNames(List<Deliverable> deliverables) {
        List<String> names = new ArrayList<>();
        for (Deliverable d : deliverables) {
            names.add(d.name());
        }
        return names;
    }
}
