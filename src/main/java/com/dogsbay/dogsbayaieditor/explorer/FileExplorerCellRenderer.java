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

package com.dogsbay.dogsbayaieditor.explorer;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.io.File;

import com.dogsbay.dogsbayaieditor.IconFactory;

/**
 * Custom tree cell renderer for the file explorer.
 * Shows system icons for files only (no icons for directories).
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class FileExplorerCellRenderer extends DefaultTreeCellRenderer {

    public FileExplorerCellRenderer() {
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof FileSystemNode) {
            FileSystemNode node = (FileSystemNode) value;
            File file = node.getFile();

            setText(node.toString());
            setToolTipText(file.getAbsolutePath());

            if (file.isDirectory()) {
                setIcon(IconFactory.getDirectoryIcon());
            } else {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                String ext = (dot >= 0) ? name.substring(dot + 1) : null;
                setIcon(IconFactory.getIconForExtension(ext));
            }

            // Gray out cut items
            if (!selected && node.isCut()) {
                Color grayColor = UIManager.getColor("Label.disabledForeground");
                setForeground(grayColor != null ? grayColor : Color.GRAY);
            }
        } else {
            setText(value.toString());
            setIcon(IconFactory.getDocumentIcon());
            setToolTipText(null);
            if (!selected) {
                setForeground(Color.GRAY);
            }
        }

        return this;
    }
}
