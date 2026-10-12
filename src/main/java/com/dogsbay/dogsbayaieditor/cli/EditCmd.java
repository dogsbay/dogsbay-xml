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

/** Replace one exact piece of a document's text: the edit_document tool from the command line. */
@Command(name = "edit",
        description = "Replace one exact piece of a document's text, leaving the rest of the file byte for byte "
                + "(the edit_document tool). The text must occur once, or pass --all.")
class EditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The document")
    private String file;

    @Option(names = "--find", required = true, description = "The exact text to replace")
    private String find;

    @Option(names = "--replace", required = true, description = "The new text (empty deletes)")
    private String replace;

    @Option(names = "--all", description = "Replace every occurrence")
    private boolean all;

    @Option(names = "--editor", description = "Edit through the running editor, for a document open there")
    private boolean viaEditor;

    @Override
    public Integer call() throws Exception {
        var args = ToolRunner.MAPPER.createObjectNode().put("file", java.nio.file.Path.of(file).toAbsolutePath()
                .toString()).put("find", find).put("replace", replace);
        if (all) {
            args.put("all", true);
        }
        Object result = ToolRunner.run(ToolRunner.find("edit_document"), args, viaEditor);
        System.out.println(ToolRunner.message(result) != null ? ToolRunner.message(result)
                : ToolRunner.MAPPER.writeValueAsString(result));
        return ToolRunner.failed(result) ? 1 : 0;
    }
}
