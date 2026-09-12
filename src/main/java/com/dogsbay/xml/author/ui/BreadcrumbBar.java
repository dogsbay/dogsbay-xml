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

import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.dogsbay.xml.author.model.AuthorBlock;

/** Ancestor chain of the selected block; clicking an ancestor selects it. */
public class BreadcrumbBar extends JPanel {

    private final BlockStylesheet stylesheet;
    private final Consumer<AuthorBlock> onSelect;

    public BreadcrumbBar(BlockStylesheet stylesheet, Consumer<AuthorBlock> onSelect) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.stylesheet = stylesheet;
        this.onSelect = onSelect;
        setBorder(new EmptyBorder(2, 8, 2, 8));
    }

    public void showPath(AuthorBlock block) {
        removeAll();
        if (block != null) {
            boolean first = true;
            for (AuthorBlock ancestor : block.path()) {
                if (!first) {
                    JLabel sep = new JLabel("›");
                    sep.setForeground(stylesheet.mutedForeground());
                    add(sep);
                }
                add(crumb(ancestor));
                first = false;
            }
        }
        revalidate();
        repaint();
    }

    /** Replaces the path with a short message (a refused edit); the next selection change restores the path. */
    public void showMessage(String message) {
        removeAll();
        JLabel label = new JLabel(message);
        label.setFont(stylesheet.gutterFont());
        label.setForeground(stylesheet.mutedForeground());
        add(label);
        revalidate();
        repaint();
    }

    private JLabel crumb(AuthorBlock block) {
        JLabel label = new JLabel(block.getType().getLabel());
        label.setFont(stylesheet.gutterFont());
        label.setForeground(stylesheet.gutterForeground());
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onSelect.accept(block);
            }
        });
        return label;
    }
}
