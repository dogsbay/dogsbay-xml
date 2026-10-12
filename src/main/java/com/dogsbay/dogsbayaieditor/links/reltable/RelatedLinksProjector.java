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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the related-links a {@link ReltableModel} would generate - the same
 * matrix DITA-OT applies, the single source of truth for both the {@code
 * reltable_audit} preview and the editor's live preview. Per row: topics in one cell
 * link to topics in the other cells (honoring each cell's effective {@code @linking}:
 * {@code normal} both ways, {@code sourceonly} contributes, {@code targetonly}
 * receives, {@code none} inert); {@code collection-type="family"} also meshes topics
 * within a cell.
 */
public final class RelatedLinksProjector {

    private RelatedLinksProjector() {}

    public static List<GeneratedLink> project(ReltableModel model) {
        List<GeneratedLink> out = new ArrayList<>();
        for (Reltable table : model.reltables()) {
            // dedupe source->target per table; a typed pair avoids any string-separator collision
            Set<List<File>> seen = new LinkedHashSet<>();
            for (int r = 0; r < table.rows().size(); r++) {
                projectRow(table, table.rows().get(r), r, out, seen);
            }
        }
        return out;
    }

    private static void projectRow(Reltable table, Reltable.Row row, int rowIndex,
            List<GeneratedLink> out, Set<List<File>> seen) {
        List<Reltable.Cell> cells = row.cells();
        // Cross-cell links: for each ordered pair of distinct cells, contributor -> receiver.
        for (int i = 0; i < cells.size(); i++) {
            Reltable.Cell from = cells.get(i);
            // collection-type=family meshes topics within a cell regardless of linking.
            if ("family".equals(table.effectiveCollectionType(i, from))) {
                link(from, from, rowIndex, out, seen);
            }
            if (!contributes(table.effectiveLinking(i, from))) {
                continue;
            }
            for (int j = 0; j < cells.size(); j++) {
                if (i == j) {
                    continue;
                }
                Reltable.Cell to = cells.get(j);
                if (!receives(table.effectiveLinking(j, to))) {
                    continue;
                }
                link(from, to, rowIndex, out, seen);
            }
        }
    }

    private static void link(Reltable.Cell from, Reltable.Cell to, int rowIndex,
            List<GeneratedLink> out, Set<List<File>> seen) {
        for (Reltable.Target a : from.targets()) {
            File src = a.resolvedFile();
            if (src == null) {
                continue;
            }
            for (Reltable.Target b : to.targets()) {
                File tgt = b.resolvedFile();
                if (tgt == null || src.equals(tgt)) {
                    continue;
                }
                if (seen.add(List.of(src, tgt))) {
                    out.add(new GeneratedLink(src, tgt, b.label(), rowIndex));
                }
            }
        }
    }

    private static boolean contributes(String linking) {
        return "normal".equals(linking) || "sourceonly".equals(linking);
    }

    private static boolean receives(String linking) {
        return "normal".equals(linking) || "targetonly".equals(linking);
    }
}
