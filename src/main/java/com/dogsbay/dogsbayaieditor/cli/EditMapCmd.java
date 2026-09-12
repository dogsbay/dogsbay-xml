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

import com.dogsbay.dogsbayaieditor.commands.EditMapCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.MapEditResult;

@Command(name = "edit-map",
        description = "Structurally edit a DITA map (set-attr/insert/remove/move "
                + "topicrefs), reference-safe and formatting-preserving.")
class EditMapCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The .ditamap file")
    private String map;

    @Parameters(index = "1", description = "Operation: set-attr | insert | remove | move")
    private String op;

    @Option(names = "--ref", description = "Target selector: @id or child-path (/1/3)")
    private String ref;

    @Option(names = "--parent", description = "Parent selector for insert/move (/ = root)")
    private String parent;

    @Option(names = "--index", description = "Child index for insert/move (default: append)")
    private Integer index;

    @Option(names = "--type", description = "Element type for insert (default topicref)")
    private String type;

    @Option(names = "--name", description = "Attribute name for set-attr")
    private String name;

    @Option(names = "--value", description = "Attribute value for set-attr (empty = remove)")
    private String value;

    @Option(names = "--href", description = "@href for insert")
    private String href;

    @Option(names = "--navtitle", description = "@navtitle for insert")
    private String navtitle;

    @Option(names = "--to-map", description = "Destination map for a cross-map move")
    private String toMap;

    @Option(names = "--dry-run", description = "Show the plan without writing")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        MapEditResult result = new HeadlessExecutor().execute(new EditMapCommand(
                map, op, ref, parent, index, type, name, value, href, navtitle, toMap, dryRun));
        for (String w : result.warnings()) {
            System.out.println("warning: " + w);
        }
        for (String f : result.files()) {
            System.out.println((dryRun ? "[dry-run] would change " : "changed ") + f);
        }
        if (result.files().isEmpty()) {
            System.out.println("No changes.");
        }
        return 0;
    }
}
