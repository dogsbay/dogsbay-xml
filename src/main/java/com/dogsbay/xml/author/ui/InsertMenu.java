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

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.BlockOperations;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.BlockTypeRegistry;

/**
 * Builds the schema-filtered structural menus: only block types the registry
 * allows at the target position are offered.
 */
final class InsertMenu {

    private InsertMenu() {
    }

    /** Popup for the insert shortcut: insert-inside and insert-after sections. */
    static JPopupMenu forBlock(AuthorEditorPanel panel, AuthorBlock block) {
        JPopupMenu menu = newPopup();
        addInsertItems(panel, menu, block);
        return menu;
    }

    /**
     * Heavyweight popups only: lightweight ones share the panel's painting
     * surface and can interleave with the custom block stack.
     */
    private static JPopupMenu newPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.setLightWeightPopupEnabled(false);
        return menu;
    }

    /** Full context menu: insert, move, table operations, delete. */
    static JPopupMenu contextMenu(AuthorEditorPanel panel, AuthorBlock block) {
        JPopupMenu menu = newPopup();
        fillBlockActions(panel, menu, block);
        return menu;
    }

    /** The block actions (insert/move/table/delete), appendable to any popup. */
    static void fillBlockActions(AuthorEditorPanel panel, JPopupMenu menu, AuthorBlock block) {
        addInsertItems(panel, menu, block);

        menu.addSeparator();
        JMenuItem up = new JMenuItem("Move Up");
        up.addActionListener(e -> panel.apply(
                BlockOperations.move(panel.getAuthorDocument(), block, -1), 0));
        menu.add(up);
        JMenuItem down = new JMenuItem("Move Down");
        down.addActionListener(e -> panel.apply(
                BlockOperations.move(panel.getAuthorDocument(), block, 1), 0));
        menu.add(down);

        AuthorBlock list = BlockOperations.listOf(block);
        if (list != null) {
            menu.addSeparator();
            boolean bulleted = "ul".equals(list.getType().getName());
            JMenuItem convert = new JMenuItem(bulleted ? "Convert to Numbered List" : "Convert to Bulleted List");
            convert.addActionListener(e -> panel.convertList(block));
            menu.add(convert);
        }

        AuthorBlock row = enclosing(block, "strow", "sthead", "row");
        AuthorBlock cell = enclosing(block, "stentry", "entry");
        if (row != null || cell != null) {
            menu.addSeparator();
            boolean bodyRow = row != null && BlockOperations.isBodyRow(row);
            if (row != null && BlockOperations.isHeaderRow(row)) {
                JMenuItem addRow = new JMenuItem("Insert Row Below Header");
                AuthorBlock target = row;
                addRow.addActionListener(e -> panel.apply(
                        BlockOperations.addRowAfter(panel.getAuthorDocument(), target), 0));
                menu.add(addRow);
            }
            if (row != null && bodyRow) {
                JMenuItem addRow = new JMenuItem("Insert Row After");
                AuthorBlock target = row;
                addRow.addActionListener(e -> panel.apply(
                        BlockOperations.addRowAfter(panel.getAuthorDocument(), target), 0));
                menu.add(addRow);
                JMenuItem delRow = new JMenuItem("Delete Row");
                delRow.addActionListener(e -> panel.apply(
                        BlockOperations.deleteRow(panel.getAuthorDocument(), target), 0));
                menu.add(delRow);
            }
            if (cell != null) {
                JMenuItem addCol = new JMenuItem("Insert Column After");
                addCol.addActionListener(e -> panel.apply(
                        BlockOperations.addColumnAfter(panel.getAuthorDocument(), cell), 0));
                menu.add(addCol);
                JMenuItem delCol = new JMenuItem("Delete Column");
                delCol.addActionListener(e -> panel.apply(
                        BlockOperations.deleteColumn(panel.getAuthorDocument(), cell), 0));
                menu.add(delCol);
            }
        }

        if ("image".equals(block.getType().getName())) {
            menu.addSeparator();
            JMenuItem source = new JMenuItem("Set Image Source…");
            source.addActionListener(e -> panel.chooseImageSource(block));
            menu.add(source);
        }

        menu.addSeparator();
        JMenuItem copy = new JMenuItem("Copy Block");
        copy.addActionListener(e -> panel.copyBlock(block));
        menu.add(copy);
        if (block.getType().isDeletable() && block.getParent() != null) {
            JMenuItem cut = new JMenuItem("Cut Block");
            cut.addActionListener(e -> panel.cutBlock(block));
            menu.add(cut);
            JMenuItem duplicate = new JMenuItem("Duplicate Block");
            duplicate.addActionListener(e -> panel.duplicateBlock(block));
            menu.add(duplicate);
        }
        JMenuItem paste = new JMenuItem("Paste Block");
        paste.addActionListener(e -> panel.pasteBlock(block));
        menu.add(paste);

        if (block.getType().isDeletable() && block.getParent() != null) {
            menu.addSeparator();
            JMenuItem delete = new JMenuItem("Delete " + block.getType().getLabel());
            delete.addActionListener(e -> panel.deleteBlock(block));
            menu.add(delete);
        }
    }

    private static void addInsertItems(AuthorEditorPanel panel, JPopupMenu menu, AuthorBlock block) {
        BlockTypeRegistry registry = panel.getAuthorDocument().getRegistry();

        // Only what the content model still allows: no second title, no steps before the
        // prerequisites. The insert lands at the nearest valid position.
        var inside = registry.insertableChildren(block);
        if (!inside.isEmpty()) {
            JMenu insideMenu = new JMenu("Insert into " + block.getType().getLabel());
            for (BlockType type : inside) {
                JMenuItem item = new JMenuItem(type.getLabel());
                item.addActionListener(e -> panel.insertBlock(
                        type.getName(), block, block.getChildren().size()));
                insideMenu.add(item);
            }
            menu.add(insideMenu);
        }

        AuthorBlock parent = block.getParent();
        if (parent != null) {
            var after = registry.insertableChildren(parent);
            if (!after.isEmpty()) {
                JMenu afterMenu = new JMenu("Insert after " + block.getType().getLabel());
                for (BlockType type : after) {
                    JMenuItem item = new JMenuItem(type.getLabel());
                    item.addActionListener(e -> panel.insertBlock(
                            type.getName(), parent, block.indexInParent() + 1));
                    afterMenu.add(item);
                }
                menu.add(afterMenu);
            }
        }
    }

    private static AuthorBlock enclosing(AuthorBlock block, String... typeNames) {
        for (AuthorBlock b = block; b != null; b = b.getParent()) {
            for (String name : typeNames) {
                if (name.equals(b.getType().getName())) {
                    return b;
                }
            }
        }
        return null;
    }
}
