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
import com.dogsbay.dogsbayaieditor.commands.RenameProfileValueCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "rename-profile-value",
        description = "Rename a profiling attribute value (product/audience/platform/"
                + "otherprops token) project-wide, including matching <prop> rules in "
                + ".ditaval files. Dry-run by default — pass --apply to execute.")
class RenameProfileValueCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The profiling attribute (e.g. product)")
    private String attribute;

    @Parameters(index = "1", description = "The token to rename")
    private String oldValue;

    @Parameters(index = "2", description = "The new token")
    private String newValue;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root scanned and rewritten")
    private String root;

    @Option(names = {"--apply"},
            description = "Execute (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(new RenameProfileValueCommand(
                attribute, oldValue, newValue, root, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Renamed " : "Would rename ")
                        + result.moveFrom() + "  →  " + result.moveTo());
    }
}
