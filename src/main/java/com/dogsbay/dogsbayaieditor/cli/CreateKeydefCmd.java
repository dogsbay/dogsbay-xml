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

import com.dogsbay.dogsbayaieditor.commands.CreateKeydefCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "create-keydef",
        description = "Define a text key: add a keydef with keyword text to a map. "
                + "Dry-run by default — pass --apply to execute.")
class CreateKeydefCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The key name to define")
    private String key;

    @Parameters(index = "1", description = "The keyword text the key resolves to")
    private String text;

    @Option(names = {"-m", "--map"}, required = true,
            description = "The map that receives the keydef (often a keys submap)")
    private String map;

    @Option(names = {"--root-map"},
            description = "Root map for context: warns when --map isn't included "
                    + "from it, and collision checks use its effective key space")
    private String rootMap;

    @Option(names = {"--replace-in"},
            description = "Also replace whole-word occurrences of the text in topics "
                    + "under this root with <ph keyref=.../> (text content only — "
                    + "never attributes, comments, or code elements)")
    private String replaceRoot;

    @Option(names = {"--apply"},
            description = "Execute (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new CreateKeydefCommand(key, text, map, rootMap, replaceRoot, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Defined " : "Would define ")
                        + "key '" + result.moveFrom() + "' in " + result.moveTo());
    }
}
