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
import com.dogsbay.dogsbayaieditor.commands.ProjectGraphCommand;
import com.dogsbay.dogsbayaieditor.graph.ProjectGraph;
import com.fasterxml.jackson.databind.ObjectMapper;

@Command(name = "project-graph",
        description = "How a project connects: maps, topics, keys and DITAVALs with typed edges, "
                + "per deliverable, plus structural issues. Prints JSON; for a page use 'report project-graph'.")
class ProjectGraphCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-m", "--map"}, description = "Limit to one root map")
    private String map;

    @Option(names = {"-d", "--deliverable"}, description = "Limit to one deliverable")
    private String deliverable;

    @Option(names = "--no-checks", negatable = false,
            description = "Skip DTD validation, the element-id audit and the conref push audit")
    private boolean noChecks;

    @Override
    public Integer call() throws Exception {
        ProjectGraph graph = new HeadlessExecutor().execute(
                new ProjectGraphCommand(root, map, deliverable, !noChecks));
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(graph));
        return 0;
    }
}
