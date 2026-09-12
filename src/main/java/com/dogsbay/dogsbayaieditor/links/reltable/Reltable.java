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

package com.dogsbay.dogsbayaieditor.links.reltable;

import java.io.File;
import java.util.List;

import com.dogsbay.xml.XElement;

/**
 * One {@code <reltable>} in a DITA map: typed columns (from {@code <relheader>}),
 * rows of cells, and the topicref targets in each cell — each level bound to its
 * source {@link XElement} so a {@link ReltableWriter} can splice formatting-preserving
 * edits. Cell attributes override column, which override table (the DITA cascade).
 *
 * @param element        the {@code <reltable>} element (carries byte offsets)
 * @param title          {@code @title}, or null
 * @param linking        table-level {@code @linking}, or null
 * @param collectionType table-level {@code @collection-type}, or null
 * @param columns        from {@code <relheader><relcolspec>} (may be empty)
 * @param rows           the {@code <relrow>}s
 * @param source         the map file this reltable lives in
 */
public record Reltable(XElement element, String title, String linking,
        String collectionType, List<Column> columns, List<Row> rows, File source) {

    /** A {@code <relcolspec>} header column. */
    public record Column(String type, String linking, String collectionType, String label) {}

    /** A {@code <relrow>}. */
    public record Row(XElement element, List<Cell> cells) {}

    /** A {@code <relcell>} (one per column; short rows padded with empty cells). */
    public record Cell(XElement element, String type, String linking,
            String collectionType, List<Target> targets) {}

    /** A {@code <topicref>} inside a cell. */
    public record Target(XElement element, String href, String keyref,
            String navtitle, String type, File resolvedFile) {
        /** The label to show: navtitle, else the resolved file name, else href/keyref. */
        public String label() {
            if (navtitle != null && !navtitle.isBlank()) {
                return navtitle;
            }
            if (resolvedFile != null) {
                return resolvedFile.getName();
            }
            return href != null ? href : (keyref != null ? "key:" + keyref : "?");
        }
    }

    public int startOffset() {
        return element.getElementStartPosition();
    }

    public int endOffset() {
        return element.getElementEndPosition();
    }

    /** Effective linking for a cell: cell → column → table → {@code normal}. */
    public String effectiveLinking(int columnIndex, Cell cell) {
        if (cell != null && cell.linking() != null) {
            return cell.linking();
        }
        if (columnIndex >= 0 && columnIndex < columns.size()
                && columns.get(columnIndex).linking() != null) {
            return columns.get(columnIndex).linking();
        }
        return linking != null ? linking : "normal";
    }

    /** Effective collection-type for a cell: cell → column → table → {@code unordered}.
     *  Same parameter order as {@link #effectiveLinking} to avoid transposition. */
    public String effectiveCollectionType(int columnIndex, Cell cell) {
        if (cell != null && cell.collectionType() != null) {
            return cell.collectionType();
        }
        if (columnIndex >= 0 && columnIndex < columns.size()
                && columns.get(columnIndex).collectionType() != null) {
            return columns.get(columnIndex).collectionType();
        }
        return collectionType != null ? collectionType : "unordered";
    }
}
