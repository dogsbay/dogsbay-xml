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

package com.dogsbay.xml.editor;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Toolbar for AsciiDoc editing with formatting buttons.
 * Replaces the XML editor toolbar when an AsciiDoc file is active.
 */
public class AsciiDocToolbar extends JToolBar {

    private JTextComponent editor;

    public AsciiDocToolbar() {
        setRollover(true);
        setFloatable(false);
        setBorderPainted(false);
        buildToolbar();
    }

    public void setEditor(JTextComponent editor) {
        this.editor = editor;
    }

    private void buildToolbar() {
        add(createAction("Bold", MarkdownToolbarIcons.getBoldIcon(),
            "Bold (Ctrl+B)", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
            () -> AsciiDocEditHelper.wrapSelection(editor, "*", "*", "bold text")));

        add(createAction("Italic", MarkdownToolbarIcons.getItalicIcon(),
            "Italic (Ctrl+I)", KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK,
            () -> AsciiDocEditHelper.wrapSelection(editor, "_", "_", "italic text")));

        add(createAction("Monospace", MarkdownToolbarIcons.getCodeIcon(),
            "Monospace (Ctrl+`)", KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK,
            () -> AsciiDocEditHelper.wrapSelection(editor, "`", "`", "monospace")));

        add(createAction("Highlight", null,
            "Highlight", 0, 0,
            () -> AsciiDocEditHelper.wrapSelection(editor, "#", "#", "highlighted")));

        addSeparator();

        add(createAction("Section", MarkdownToolbarIcons.getHeadingIcon(),
            "Cycle Section Level (Ctrl+H)", KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK,
            () -> AsciiDocEditHelper.cycleSection(editor)));

        addSeparator();

        add(createAction("Link", MarkdownToolbarIcons.getLinkIcon(),
            "Insert Link (Ctrl+K)", KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK,
            () -> {
                String selected = editor.getSelectedText();
                if (selected != null && !selected.isEmpty()) {
                    editor.replaceSelection("link:" + selected + "[link text]");
                } else {
                    int pos = editor.getCaretPosition();
                    editor.replaceSelection("link:url[link text]");
                    editor.setSelectionStart(pos + 5);
                    editor.setSelectionEnd(pos + 8);
                }
                editor.requestFocus();
            }));

        add(createAction("Image", MarkdownToolbarIcons.getImageIcon(),
            "Insert Image", 0, 0,
            () -> {
                int pos = editor.getCaretPosition();
                editor.replaceSelection("image::path[alt text]");
                editor.setSelectionStart(pos + 7);
                editor.setSelectionEnd(pos + 11);
                editor.requestFocus();
            }));

        add(createAction("Xref", null,
            "Cross Reference", 0, 0,
            () -> {
                int pos = editor.getCaretPosition();
                editor.replaceSelection("<<target>>");
                editor.setSelectionStart(pos + 2);
                editor.setSelectionEnd(pos + 8);
                editor.requestFocus();
            }));

        addSeparator();

        add(createAction("Code Block", MarkdownToolbarIcons.getCodeBlockIcon(),
            "Listing Block", 0, 0,
            () -> AsciiDocEditHelper.insertBlock(editor, "----", "code")));

        add(createAction("NOTE", null,
            "Admonition (NOTE)", 0, 0,
            () -> AsciiDocEditHelper.insertAdmonition(editor, "NOTE")));

        addSeparator();

        add(createAction("Bullet List", MarkdownToolbarIcons.getBulletListIcon(),
            "Unordered List", 0, 0,
            () -> AsciiDocEditHelper.toggleLinePrefix(editor, "* ")));

        add(createAction("Number List", MarkdownToolbarIcons.getNumberListIcon(),
            "Ordered List", 0, 0,
            () -> AsciiDocEditHelper.toggleLinePrefix(editor, ". ")));

        addSeparator();

        add(createAction("Table", MarkdownToolbarIcons.getTableIcon(),
            "Insert Table", 0, 0,
            () -> AsciiDocEditHelper.insertTable(editor)));
    }

    private AbstractAction createAction(String name, Icon icon, String tooltip,
                                         int keyCode, int modifiers, Runnable action) {
        AbstractAction a = new AbstractAction(name, icon) {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor != null) {
                    action.run();
                }
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, tooltip);
        if (keyCode != 0) {
            a.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(keyCode, modifiers));
        }
        return a;
    }

    public void registerKeyBindings(JTextComponent textComponent) {
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof JButton) {
                JButton button = (JButton) getComponent(i);
                Action action = button.getAction();
                if (action != null) {
                    KeyStroke ks = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                    if (ks != null) {
                        String key = "ad-" + action.getValue(Action.NAME);
                        textComponent.getInputMap(JComponent.WHEN_FOCUSED).put(ks, key);
                        textComponent.getActionMap().put(key, action);
                    }
                }
            }
        }
    }
}
