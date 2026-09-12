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
import com.dogsbay.dogsbayaieditor.commands.InlineKeyCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "inline-key",
        description = "Replace keyref/conkeyref usages of a key with the direct "
                + "path it resolves to. Dry-run by default — pass --apply to execute.")
class InlineKeyCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The key to inline")
    private String key;

    @Option(names = {"-m", "--map"}, required = true,
            description = "Context map that resolves the key")
    private String map;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root whose usages are rewritten")
    private String root;

    @Option(names = {"--apply"},
            description = "Execute the refactor (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new InlineKeyCommand(key, map, root, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Inlined " : "Would inline ")
                        + "key '" + result.moveFrom() + "'");
    }
}
