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
import com.dogsbay.dogsbayaieditor.commands.MergeKeydefsCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "merge-keydefs",
        description = "Remove shadowed duplicate key definitions from a root map's "
                + "closure (first definition wins; resolution is unchanged). Dry-run "
                + "by default — pass --apply to execute.")
class MergeKeydefsCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The root map whose closure is scanned")
    private String rootMap;

    @Option(names = {"-k", "--key"},
            description = "Merge just this key (default: all duplicated keys)")
    private String key;

    @Option(names = {"--apply"},
            description = "Execute (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new MergeKeydefsCommand(rootMap, key, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Merged duplicates under "
                        : "Would merge duplicates under ") + result.moveFrom());
    }
}
