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

import com.dogsbay.dogsbayaieditor.mcp.McpServer.McpTool;

/** Any of the editor's MCP tools, from the command line. */
@Command(name = "tool",
        description = {"Run any of the editor's tools by its MCP name, with JSON arguments, and print the JSON "
                + "result. Runs without the editor, on files (as you: no review marks), or through the running "
                + "editor with --editor.",
            "Examples:",
            "  dogsbay-xml tool --list",
            "  dogsbay-xml tool validate_document '{\"file\": \"topics/a.dita\"}'",
            "  dogsbay-xml tool edit_structure @steps.json"})
class ToolCmd implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "The tool's name (see --list)")
    private String name;

    @Parameters(index = "1", arity = "0..1", description = "Its arguments: JSON, @file, or - for standard input")
    private String args;

    @Option(names = "--list", description = "List the tools; those that need the running editor are marked")
    private boolean list;

    @Option(names = "--schema", description = "Print the tool's description and argument schema instead of running it")
    private boolean schema;

    @Option(names = "--editor", description = "Run through the running editor, for documents open there")
    private boolean viaEditor;

    @Override
    public Integer call() throws Exception {
        if (list || name == null) {
            for (McpTool t : ToolRunner.catalog()) {
                String first = t.description().split("(?<=\\.)\\s", 2)[0];
                System.out.printf("%-28s %s%s%n", t.name(), t.worksWithoutEditor() ? "" : "[editor] ",
                        first.length() > 100 ? first.substring(0, 99) + "…" : first);
            }
            return 0;
        }
        McpTool tool = ToolRunner.find(name);
        if (schema) {
            System.out.println(tool.description());
            System.out.println(ToolRunner.MAPPER.writeValueAsString(tool.inputSchema()));
            return 0;
        }
        Object result = ToolRunner.run(tool, ToolRunner.arguments(args), viaEditor);
        System.out.println(ToolRunner.MAPPER.writeValueAsString(result));
        return ToolRunner.failed(result) ? 1 : 0;
    }
}
