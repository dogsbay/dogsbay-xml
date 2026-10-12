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

import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.dogsbay.xml.author.model.InlineStyle;

/**
 * Inline formatting actions for the focused text block's selection.
 * Keyboard equivalents (Ctrl+B/I/U/E) live on each text component.
 */
public class FormatToolbar extends JPanel {

    public FormatToolbar(AuthorEditorPanel panel) {
        super(new FlowLayout(FlowLayout.RIGHT, 2, 1));
        setOpaque(false);
        add(button("B", "Bold (Ctrl+B)", Font.BOLD,
                () -> panel.toggleInlineStyle(InlineStyle.BOLD, InlineStyle.TRUE)));
        add(button("I", "Italic (Ctrl+I)", Font.ITALIC,
                () -> panel.toggleInlineStyle(InlineStyle.ITALIC, InlineStyle.TRUE)));
        add(button("U", "Underline (Ctrl+U)", Font.PLAIN,
                () -> panel.toggleInlineStyle(InlineStyle.UNDERLINE, InlineStyle.TRUE)));
        add(button("</>", "Code phrase (Ctrl+E)", Font.PLAIN,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "codeph")));
        add(button("UI", "UI control <uicontrol>", Font.PLAIN,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "uicontrol")));
        add(button("path", "File path <filepath>", Font.PLAIN,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "filepath")));
        add(button("var", "Variable name <varname>", Font.ITALIC,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "varname")));
        add(button("term", "Term <term>", Font.ITALIC,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "term")));
        add(button("kw", "Keyword <keyword>", Font.PLAIN,
                () -> panel.toggleInlineStyle(InlineStyle.DITA_INLINE, "keyword")));
        add(button("link", "Insert link <xref> on the selection", Font.PLAIN, panel::insertLink));
    }

    private JButton button(String label, String tooltip, int style, Runnable action) {
        JButton button = new JButton(label);
        button.setToolTipText(tooltip);
        button.setFont(button.getFont().deriveFont(style, 11f));
        button.setFocusable(false);
        button.setMargin(new java.awt.Insets(1, 6, 1, 6));
        button.addActionListener(e -> action.run());
        return button;
    }
}
