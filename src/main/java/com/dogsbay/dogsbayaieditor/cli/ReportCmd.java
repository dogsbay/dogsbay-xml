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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.ReportResult;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The CLI face of {@code render_report}, through the same dispatch the MCP tool
 * uses, so a page built in CI matches one an agent builds.
 */
@Command(name = "report",
        description = "Write a standalone HTML page from a read-only command's output, e.g. "
                + "'report project-graph . -o docs/relationship-map.html' or "
                + "'report project-health . -o health.html --arg map=guide.ditamap'.")
class ReportCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Source command, e.g. project-graph or project-health")
    private String source;

    @Parameters(index = "1", description = "Project root")
    private String root;

    @Option(names = {"-o", "--output"}, required = true, description = "Where to write the page")
    private Path output;

    @Option(names = {"-t", "--template"},
            description = "Built-in template (relationship-map, health) or a template path; defaults from the source")
    private String template;

    @Option(names = {"-a", "--arg"},
            description = "A source argument as name=value, repeatable, e.g. --arg map=guide.ditamap")
    private Map<String, String> args;

    @Option(names = "--data", description = "A JSON file to render instead of running the source")
    private Path data;

    @Override
    public Integer call() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("root", Path.of(root).toAbsolutePath().normalize().toString());
        // A CLI path is relative to where the command runs, not to the project.
        params.put("output", output.toAbsolutePath().normalize().toString());
        if (template != null) {
            params.put("template", template);
        }
        if (data != null) {
            params.put("data", Files.readString(data));
        } else {
            params.put("source", source);
            ObjectNode sourceArgs = params.putObject("args");
            if (args != null) {
                args.forEach(sourceArgs::put);
            }
        }
        ReportResult result = (ReportResult) new JsonRpcHandler(new HeadlessExecutor())
                .dispatch("render-report", params);
        System.out.println("Wrote " + result.output() + " (" + result.bytes() + " bytes, template "
                + result.template() + ")");
        return 0;
    }
}
