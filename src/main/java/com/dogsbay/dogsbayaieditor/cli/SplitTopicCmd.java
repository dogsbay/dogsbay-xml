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
import com.dogsbay.dogsbayaieditor.commands.SplitTopicCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "split-topic",
        description = "Split a topic: every top-level <section> becomes a standalone "
                + "topic, the map gains nested topicrefs, and references into the "
                + "split-out content are rewritten. Dry-run by default — pass --apply "
                + "to execute.")
class SplitTopicCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The topic to split")
    private String file;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root whose references are rewritten")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Map that receives the new topicrefs (nested under the "
                    + "source topic's topicref when present)")
    private String map;

    @Option(names = {"--apply"},
            description = "Execute (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new SplitTopicCommand(file, root, map, apply));
        return RefactorCliOutput.print(result,
                (result.applied() ? "Split " : "Would split ")
                        + result.moveFrom() + "  →  " + result.moveTo());
    }
}
