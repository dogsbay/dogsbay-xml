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

import com.dogsbay.dogsbayaieditor.commands.DeleteFileCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

@Command(name = "delete-file",
        description = "Safe delete: report every inbound reference; optionally remove "
                + "map references. Dry-run by default — pass --apply to execute.")
class DeleteFileCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The file to delete")
    private String file;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root scanned for inbound references")
    private String root;

    @Option(names = {"--remove-refs"},
            description = "Also remove referencing map elements (topicref/keydef/...)")
    private boolean removeRefs;

    @Option(names = {"--apply"},
            description = "Execute the delete (default: dry-run, print the plan)")
    private boolean apply;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        RefactorResult result = executor.execute(
                new DeleteFileCommand(file, root, removeRefs, apply));

        for (String warning : result.warnings()) {
            System.out.println("WARNING: " + warning);
        }
        System.out.println((result.applied() ? "Deleted " : "Would delete ")
                + result.moveFrom());
        for (RefactorResult.AttributeEditInfo edit : result.edits()) {
            System.out.printf("  %s:%d  remove element with @%s=\"%s\"%n",
                    edit.file(), edit.line(), edit.attribute(), edit.oldValue());
        }
        if (result.applied()) {
            System.out.println(result.editsApplied() + " reference(s) removed in "
                    + result.filesChanged() + " file(s).");
            for (String failure : result.failures()) {
                System.out.println("FAILED: " + failure);
            }
            return result.failures().isEmpty() ? 0 : 1;
        }
        System.out.println(result.edits().size() + " map reference(s) "
                + (removeRefs ? "will be removed" : "would be removable with --remove-refs")
                + ". Re-run with --apply to execute.");
        return 0;
    }
}
