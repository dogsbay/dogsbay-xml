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

import com.fasterxml.jackson.databind.JsonNode;

/** Ordered structural steps on a document: the edit_structure tool from the command line. */
@Command(name = "edit-structure",
        description = {"Change a document's structure with ordered steps (the edit_structure tool): insert, "
                + "delete, replace, move, wrap, unwrap, rename, set-attribute, remove-attribute, set-text and "
                + "test, each selecting one element by XPath or #id. All steps apply or none.",
            "Example:",
            "  dogsbay-xml edit-structure topics/a.dita --steps "
                + "'[{\"op\": \"delete\", \"select\": \"//step[@platform=\\\"linux\\\"]\"}]' --dry-run"})
class EditStructureCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The document")
    private String file;

    @Option(names = "--steps", required = true, description = "The steps, a JSON array: inline, @file, or - for "
            + "standard input")
    private String steps;

    @Option(names = "--dry-run", description = "Say what would change, and write nothing")
    private boolean dryRun;

    @Option(names = "--editor", description = "Edit through the running editor, for a document open there")
    private boolean viaEditor;

    @Override
    public Integer call() throws Exception {
        String json = steps.equals("-") ? new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                : steps.startsWith("@") ? java.nio.file.Files.readString(java.nio.file.Path.of(steps.substring(1)))
                : steps;
        JsonNode parsed = ToolRunner.MAPPER.readTree(json);
        var args = ToolRunner.MAPPER.createObjectNode().put("file", java.nio.file.Path.of(file).toAbsolutePath()
                .toString());
        args.set("steps", parsed);
        if (dryRun) {
            args.put("dryRun", true);
        }
        Object result = ToolRunner.run(ToolRunner.find("edit_structure"), args, viaEditor);
        System.out.println(ToolRunner.message(result) != null ? ToolRunner.message(result)
                : ToolRunner.MAPPER.writeValueAsString(result));
        return ToolRunner.failed(result) ? 1 : 0;
    }
}
