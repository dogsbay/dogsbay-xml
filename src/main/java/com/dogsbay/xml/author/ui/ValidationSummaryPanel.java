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

package com.dogsbay.xml.author.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.dogsbay.xml.author.validation.ValidationIssue;

/**
 * Strip listing the current validation issues; clicking one selects and
 * focuses the offending block. Hidden while the document is clean.
 */
class ValidationSummaryPanel extends JPanel {

    private final DefaultListModel<ValidationIssue> model = new DefaultListModel<>();
    private final JList<ValidationIssue> list = new JList<>(model);
    private final JLabel header = new JLabel();

    ValidationSummaryPanel(AuthorEditorPanel panel) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        header.setFont(header.getFont().deriveFont(11f));
        add(header, BorderLayout.NORTH);

        list.setVisibleRowCount(3);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jlist, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(jlist, value, index, selected, focused);
                ValidationIssue issue = (ValidationIssue) value;
                setText((issue.isError() ? "✕ " : "⚠ ") + issue.message()
                        + "  — " + issue.block().getType().getLabel());
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                panel.focusBlock(list.getSelectedValue().block(), 0);
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(100, 64));
        add(scroll, BorderLayout.CENTER);
        setVisible(false);
    }

    void showIssues(List<ValidationIssue> issues) {
        model.clear();
        long errors = issues.stream().filter(ValidationIssue::isError).count();
        long warnings = issues.size() - errors;
        issues.forEach(model::addElement);
        header.setText(errors + " error" + (errors == 1 ? "" : "s") + ", "
                + warnings + " warning" + (warnings == 1 ? "" : "s"));
        setVisible(!issues.isEmpty());
        revalidate();
    }
}
