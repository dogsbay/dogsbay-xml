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

import com.dogsbay.dogsbayaieditor.commands.ExtractConrefCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "extract-conref",
        description = "Move an element (located by id) into a reuse topic and "
                + "replace it with a conref stub. Dry-run by default — pass --apply "
                + "to execute.")
class ExtractConrefCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The topic containing the element")
    private String file;

    @Parameters(index = "1", description = "The id of the element to extract")
    private String elementId;

    @Option(names = {"-t", "--to"}, required = true,
            description = "Reuse topic that receives the element (created if missing)")
    private String to;

    @Option(names = {"--apply"},
            description = "Execute the extraction (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new ExtractConrefCommand(file, elementId, to, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Extracted " : "Would extract ")
                        + result.moveFrom() + "  →  " + result.moveTo());
    }
}
