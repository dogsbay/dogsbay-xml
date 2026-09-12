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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.FormatCommand;

@Command(name = "format",
        description = "Pretty-print XML with the project house style (.dogsbay/config.xml).")
class FormatCmd implements Callable<Integer> {

    @Parameters(index = "0..*", arity = "1..*", description = "XML file(s) to format")
    private List<Path> files;

    @Option(names = "--indent", description = "Indentation spaces (overrides the project/house style)")
    private Integer indent;

    @Option(names = {"-o", "--output"}, description = "Output file (single input; prints to stdout if omitted)")
    private Path output;

    @Option(names = {"-i", "--write"}, description = "Format in place, writing each file back")
    private boolean write;

    @Option(names = "--check",
            description = "Don't write; exit non-zero and list files that aren't already canonical")
    private boolean check;

    @Override
    public Integer call() {
        if (output != null && files.size() > 1) {
            System.err.println("-o/--output takes a single input file; use --write to "
                    + "format multiple files in place.");
            return 2;
        }
        var executor = new HeadlessExecutor();
        int notCanonical = 0;
        int failed = 0;

        for (Path file : files) {
            try {
                if (check) {
                    // no write; just report whether the file is already canonical
                    if (executor.execute(new FormatCommand(file, indent, null)).modified()) {
                        System.out.println("would reformat: " + file);
                        notCanonical++;
                    }
                } else if (write) {
                    // format in place only when it changes; the executor writes with the
                    // file's own charset (not a forced UTF-8) and preserves line endings
                    if (executor.execute(new FormatCommand(file, indent, null)).modified()) {
                        executor.execute(new FormatCommand(file, indent, file));
                        System.out.println("formatted: " + file);
                    }
                } else if (output != null) {
                    executor.execute(new FormatCommand(file, indent, output));
                    System.out.println("Formatted output written to: " + output);
                } else {
                    System.out.println(executor.execute(new FormatCommand(file, indent, null)).content());
                }
            } catch (Exception e) {
                // one malformed file must not abort the whole batch
                System.err.println("skipped " + file + ": " + e.getMessage());
                failed++;
            }
        }

        if (check && notCanonical > 0) {
            System.out.println(notCanonical + " file(s) need formatting (run with --write to fix).");
            return 1;
        }
        return failed > 0 ? 1 : 0;
    }
}
