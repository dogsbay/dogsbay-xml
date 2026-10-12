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
import com.dogsbay.dogsbayaieditor.commands.KeyifyCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "keyify",
        description = "Convert direct references to a file into key references and "
                + "add a keydef to the chosen map. Dry-run by default — pass --apply "
                + "to execute.")
class KeyifyCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The referenced file to key")
    private String file;

    @Parameters(index = "1", description = "The key name to introduce")
    private String key;

    @Option(names = {"-m", "--map"}, required = true,
            description = "The map that receives the keydef")
    private String map;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root whose references are converted")
    private String root;

    @Option(names = {"--root-map"},
            description = "Root map for context: warns when --map isn't included "
                    + "from it, and collision checks use its effective key space")
    private String rootMap;

    @Option(names = {"--apply"},
            description = "Execute the refactor (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new KeyifyCommand(file, key, map, root, rootMap, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Keyed " : "Would key ")
                        + result.moveFrom() + " as '" + result.moveTo() + "'");
    }
}
