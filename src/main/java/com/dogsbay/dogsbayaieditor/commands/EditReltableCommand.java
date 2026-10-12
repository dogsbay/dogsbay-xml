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

package com.dogsbay.dogsbayaieditor.commands;

import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;

/**
 * Structurally edit a relationship table in a DITA map, formatting-preservingly.
 * Tables/rows/cells are addressed by 0-based index.
 *
 * <p>Ops:
 * <ul>
 *   <li>{@code create-table} — append a {@code <reltable>}; {@code columns} = a
 *       comma-separated list of column types (e.g. {@code concept,task,reference}).</li>
 *   <li>{@code add-row} — append a row (one empty cell per column) to {@code table}.</li>
 *   <li>{@code remove-row} — delete {@code row} of {@code table}.</li>
 *   <li>{@code add-target} — add a {@code <topicref>} (by {@code href} or
 *       {@code keyref}, optional {@code navtitle}) to cell ({@code row},{@code col}).</li>
 *   <li>{@code remove-target} — remove target {@code index} from cell
 *       ({@code row},{@code col}).</li>
 *   <li>{@code set-attr} — set/remove attribute {@code name}={@code value} on the
 *       table, or a {@code row}, or a cell ({@code row},{@code col}).</li>
 * </ul>
 *
 * @param map     the {@code .ditamap}
 * @param table   0-based reltable index in the map
 * @param op      the operation (above)
 * @param row     row index (add-target/remove-target/remove-row/set-attr)
 * @param col     column index (add-target/remove-target/set-attr cell scope)
 * @param index   target index (remove-target)
 * @param href    {@code @href} for add-target
 * @param keyref  {@code @keyref} for add-target
 * @param navtitle {@code @navtitle} for add-target
 * @param name    attribute name for set-attr
 * @param value   attribute value for set-attr (empty ⇒ remove)
 * @param columns comma-separated column types for create-table
 * @param dryRun  compute without writing
 */
public record EditReltableCommand(
    String map,
    int table,
    String op,
    Integer row,
    Integer col,
    Integer index,
    String href,
    String keyref,
    String navtitle,
    String name,
    String value,
    String columns,
    boolean dryRun
) implements Command<MapEditResult> {}
