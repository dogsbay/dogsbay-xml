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
import com.dogsbay.dogsbayaieditor.commands.ReflowCommand;

@Command(name = "reflow",
        description = "Reflow prose to one sentence per line (semantic line breaks), "
                + "verbatim blocks untouched, then format.")
class ReflowCmd implements Callable<Integer> {

    @Parameters(index = "0..*", arity = "1..*", description = "XML file(s) to reflow")
    private List<Path> files;

    @Option(names = {"-o", "--output"}, description = "Output file (single input; prints to stdout if omitted)")
    private Path output;

    @Option(names = {"-i", "--write"}, description = "Reflow in place, writing each file back")
    private boolean write;

    @Option(names = "--check",
            description = "Don't write; exit non-zero and list files whose prose isn't one-sentence-per-line")
    private boolean check;

    @Override
    public Integer call() {
        if (output != null && files.size() > 1) {
            System.err.println("-o/--output takes a single input file; use --write for multiple files.");
            return 2;
        }
        var executor = new HeadlessExecutor();
        int notReflowed = 0;
        int failed = 0;

        for (Path file : files) {
            try {
                if (check) {
                    if (executor.execute(new ReflowCommand(file, null)).modified()) {
                        System.out.println("would reflow: " + file);
                        notReflowed++;
                    }
                } else if (write) {
                    if (executor.execute(new ReflowCommand(file, null)).modified()) {
                        executor.execute(new ReflowCommand(file, file));
                        System.out.println("reflowed: " + file);
                    }
                } else if (output != null) {
                    executor.execute(new ReflowCommand(file, output));
                    System.out.println("Reflowed output written to: " + output);
                } else {
                    System.out.println(executor.execute(new ReflowCommand(file, null)).content());
                }
            } catch (Exception e) {
                System.err.println("skipped " + file + ": " + e.getMessage());
                failed++;
            }
        }

        if (check && notReflowed > 0) {
            System.out.println(notReflowed + " file(s) not one-sentence-per-line (run with --write to fix).");
            return 1;
        }
        return failed > 0 ? 1 : 0;
    }
}
