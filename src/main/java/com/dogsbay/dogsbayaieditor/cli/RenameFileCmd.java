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
import com.dogsbay.dogsbayaieditor.commands.RenameFileCommand;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "rename-file",
        description = "Rename/move a file and rewrite every reference to it. "
                + "Dry-run by default — pass --apply to execute.")
class RenameFileCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The file to rename/move")
    private String file;

    @Parameters(index = "1", description = "The destination path")
    private String newPath;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root whose references are rewritten")
    private String root;

    @Option(names = {"--apply"},
            description = "Execute the refactor (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new RenameFileCommand(file, newPath, root, apply));

        for (String warning : result.warnings()) {
            System.out.println("WARNING: " + warning);
        }
        System.out.println((result.applied() ? "Renamed " : "Would rename ")
                + result.moveFrom() + "  →  " + result.moveTo());
        for (RefactorResult.AttributeEditInfo edit : result.edits()) {
            System.out.printf("  %s:%d  @%s: \"%s\" → \"%s\"%n",
                    edit.file(), edit.line(), edit.attribute(),
                    edit.oldValue(), edit.newValue());
        }
        if (result.applied()) {
            System.out.println(result.editsApplied() + " edit(s) in "
                    + result.filesChanged() + " file(s).");
            for (String failure : result.failures()) {
                System.out.println("FAILED: " + failure);
            }
            return result.failures().isEmpty() ? 0 : 1;
        }
        System.out.println(result.edits().size()
                + " edit(s) planned. Re-run with --apply to execute.");
        return 0;
    }
}
