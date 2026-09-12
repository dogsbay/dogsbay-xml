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

package com.dogsbay.dogsbayaieditor.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.EditReltableCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;

@Command(name = "edit-reltable",
        description = "Edit a map's relationship table (create-table/add-row/"
                + "remove-row/add-target/remove-target/set-attr), formatting-preserving.")
class EditReltableCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The .ditamap")
    private String map;

    @Parameters(index = "1", description = "Operation: create-table | add-row | "
            + "remove-row | add-target | remove-target | set-attr")
    private String op;

    @Option(names = "--table", description = "0-based reltable index (default 0)")
    private int table = 0;

    @Option(names = "--row", description = "Row index")
    private Integer row;

    @Option(names = "--col", description = "Column index")
    private Integer col;

    @Option(names = "--index", description = "Target index (remove-target)")
    private Integer index;

    @Option(names = "--href", description = "@href for add-target")
    private String href;

    @Option(names = "--keyref", description = "@keyref for add-target")
    private String keyref;

    @Option(names = "--navtitle", description = "@navtitle for add-target")
    private String navtitle;

    @Option(names = "--name", description = "Attribute name for set-attr")
    private String name;

    @Option(names = "--value", description = "Attribute value for set-attr (empty = remove)")
    private String value;

    @Option(names = "--columns", description = "Comma-separated column types for create-table")
    private String columns;

    @Option(names = "--dry-run", description = "Show the plan without writing")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        MapEditResult r = new HeadlessExecutor().execute(new EditReltableCommand(
                map, table, op, row, col, index, href, keyref, navtitle, name, value,
                columns, dryRun));
        for (String f : r.files()) {
            System.out.println((dryRun ? "[dry-run] would change " : "changed ") + f);
        }
        return 0;
    }
}
