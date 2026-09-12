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

package com.dogsbay.dogsbayaieditor.refactor;

import java.io.File;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * Table model for a refactoring plan's attribute edits.
 * Files are shown relative to the scan root when they live under it.
 */
public class RefactorPlanTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"In File", "Line", "Attribute", "Old Value", "New Value"};

    private final List<RefactorResult.AttributeEditInfo> edits;
    private final File root;

    public RefactorPlanTableModel(List<RefactorResult.AttributeEditInfo> edits, File root) {
        this.edits = edits;
        this.root = root;
    }

    @Override
    public int getRowCount() {
        return edits.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        RefactorResult.AttributeEditInfo edit = edits.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> displayPath(edit.file());
            case 1 -> edit.line() > 0 ? String.valueOf(edit.line()) : "";
            case 2 -> edit.attribute();
            case 3 -> edit.oldValue();
            case 4 -> edit.newValue();
            default -> "";
        };
    }

    /** The absolute path of the row's containing file (for tooltips). */
    public String absolutePath(int rowIndex) {
        return edits.get(rowIndex).file();
    }

    /**
     * Renders a path relative to the scan root when possible,
     * absolute otherwise.
     */
    public String displayPath(String absolutePath) {
        if (root == null) {
            return absolutePath;
        }
        try {
            java.nio.file.Path rootPath = root.toPath().toAbsolutePath().normalize();
            java.nio.file.Path filePath = new File(absolutePath).toPath().toAbsolutePath().normalize();
            if (filePath.startsWith(rootPath)) {
                return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {
            // fall through to absolute
        }
        return absolutePath;
    }
}
