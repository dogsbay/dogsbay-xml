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
 * Toolbar for Markdown editing with formatting buttons.
 * Replaces the XML editor toolbar when a Markdown file is active.
 */
public class MarkdownToolbar extends JToolBar {

    private JTextComponent editor;

    public MarkdownToolbar() {
        setRollover(true);
        setFloatable(false);
        setBorderPainted(false);
        buildToolbar();
    }

    /**
     * Sets the editor component that this toolbar operates on.
     */
    public void setEditor(JTextComponent editor) {
        this.editor = editor;
    }

    private void buildToolbar() {
        add(createAction("Bold", MarkdownToolbarIcons.getBoldIcon(),
            "Bold (Ctrl+B)", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
            () -> MarkdownEditHelper.wrapSelection(editor, "**", "**", "bold text")));

        add(createAction("Italic", MarkdownToolbarIcons.getItalicIcon(),
            "Italic (Ctrl+I)", KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK,
            () -> MarkdownEditHelper.wrapSelection(editor, "*", "*", "italic text")));

        add(createAction("Strikethrough", MarkdownToolbarIcons.getStrikethroughIcon(),
            "Strikethrough", 0, 0,
            () -> MarkdownEditHelper.wrapSelection(editor, "~~", "~~", "strikethrough")));

        addSeparator();

        add(createAction("Heading", MarkdownToolbarIcons.getHeadingIcon(),
            "Cycle Heading (Ctrl+H)", KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK,
            () -> MarkdownEditHelper.cycleHeading(editor)));

        addSeparator();

        add(createAction("Link", MarkdownToolbarIcons.getLinkIcon(),
            "Insert Link (Ctrl+K)", KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK,
            () -> {
                String selected = editor.getSelectedText();
                if (selected != null && !selected.isEmpty()) {
                    editor.replaceSelection("[" + selected + "](url)");
                    // Select "url"
                    int pos = editor.getCaretPosition();
                    editor.setSelectionStart(pos - 4);
                    editor.setSelectionEnd(pos - 1);
                } else {
                    int pos = editor.getCaretPosition();
                    editor.replaceSelection("[link text](url)");
                    editor.setSelectionStart(pos + 1);
                    editor.setSelectionEnd(pos + 10);
                }
                editor.requestFocus();
            }));

        add(createAction("Image", MarkdownToolbarIcons.getImageIcon(),
            "Insert Image", 0, 0,
            () -> {
                int pos = editor.getCaretPosition();
                editor.replaceSelection("![alt text](image-url)");
                editor.setSelectionStart(pos + 2);
                editor.setSelectionEnd(pos + 10);
                editor.requestFocus();
            }));

        addSeparator();

        add(createAction("Code", MarkdownToolbarIcons.getCodeIcon(),
            "Inline Code (Ctrl+`)", KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK,
            () -> MarkdownEditHelper.wrapSelection(editor, "`", "`", "code")));

        add(createAction("Code Block", MarkdownToolbarIcons.getCodeBlockIcon(),
            "Code Block (Ctrl+Shift+`)", KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
            () -> {
                String selected = editor.getSelectedText();
                if (selected != null && !selected.isEmpty()) {
                    editor.replaceSelection("```\n" + selected + "\n```");
                } else {
                    int pos = editor.getCaretPosition();
                    editor.replaceSelection("```\ncode\n```");
                    editor.setSelectionStart(pos + 4);
                    editor.setSelectionEnd(pos + 8);
                }
                editor.requestFocus();
            }));

        addSeparator();

        add(createAction("Bullet List", MarkdownToolbarIcons.getBulletListIcon(),
            "Bullet List", 0, 0,
            () -> MarkdownEditHelper.toggleLinePrefix(editor, "- ")));

        add(createAction("Number List", MarkdownToolbarIcons.getNumberListIcon(),
            "Numbered List", 0, 0,
            () -> MarkdownEditHelper.toggleLinePrefix(editor, "1. ")));

        add(createAction("Task List", MarkdownToolbarIcons.getTaskListIcon(),
            "Task List", 0, 0,
            () -> MarkdownEditHelper.toggleLinePrefix(editor, "- [ ] ")));

        addSeparator();

        add(createAction("Quote", MarkdownToolbarIcons.getQuoteIcon(),
            "Blockquote", 0, 0,
            () -> MarkdownEditHelper.toggleLinePrefix(editor, "> ")));

        add(createAction("Horizontal Rule", MarkdownToolbarIcons.getHorizontalRuleIcon(),
            "Horizontal Rule", 0, 0,
            () -> MarkdownEditHelper.insertBlock(editor, "---\n")));

        add(createAction("Table", MarkdownToolbarIcons.getTableIcon(),
            "Insert Table", 0, 0,
            () -> MarkdownEditHelper.insertTable(editor)));
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

    /**
     * Registers keyboard shortcuts on the given text component.
     * Should be called after setEditor().
     */
    public void registerKeyBindings(JTextComponent textComponent) {
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof JButton) {
                JButton button = (JButton) getComponent(i);
                Action action = button.getAction();
                if (action != null) {
                    KeyStroke ks = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                    if (ks != null) {
                        String key = "md-" + action.getValue(Action.NAME);
                        textComponent.getInputMap(JComponent.WHEN_FOCUSED).put(ks, key);
                        textComponent.getActionMap().put(key, action);
                    }
                }
            }
        }
    }

    /**
     * Unregisters keyboard shortcuts from the given text component.
     */
    public void unregisterKeyBindings(JTextComponent textComponent) {
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof JButton) {
                JButton button = (JButton) getComponent(i);
                Action action = button.getAction();
                if (action != null) {
                    KeyStroke ks = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
                    if (ks != null) {
                        String key = "md-" + action.getValue(Action.NAME);
                        textComponent.getInputMap(JComponent.WHEN_FOCUSED).remove(ks);
                        textComponent.getActionMap().remove(key);
                    }
                }
            }
        }
    }
}
