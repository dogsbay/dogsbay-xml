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

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.RenameElementIdCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "rename-element-id",
        description = "Rename an element id and every #fragment referencing it "
                + "(conref/href; conkeyref too with --root-map). Dry-run by default "
                + "— pass --apply to execute.")
class RenameElementIdCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The topic containing the element")
    private String file;

    @Parameters(index = "1", description = "The current id")
    private String oldId;

    @Parameters(index = "2", description = "The new id")
    private String newId;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root whose references are rewritten")
    private String root;

    @Option(names = {"--root-map"},
            description = "Root map: enables key-mediated (conkeyref) fragment rewriting")
    private String rootMap;

    @Option(names = {"--apply"},
            description = "Execute (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(new RenameElementIdCommand(
                file, oldId, newId, root, rootMap, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Renamed " : "Would rename ")
                        + result.moveFrom() + "  →  " + result.moveTo());
    }
}
