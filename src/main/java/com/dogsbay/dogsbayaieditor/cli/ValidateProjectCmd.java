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
import com.dogsbay.dogsbayaieditor.commands.ValidateProjectCommand;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;

@Command(name = "validate-project",
        description = "Validate every file in a scope (map publication set / glob / root) "
                + "on disk. Exits 1 when any file is invalid — CI-friendly.")
class ValidateProjectCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — validate its publication set")
    private String map;

    @Option(names = {"-s", "--scope"},
            description = "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides --map; default 'root')")
    private String scope;

    @Override
    public Integer call() throws Exception {
        String resolved = scope;
        if (resolved == null && map != null) {
            resolved = "map:" + map;
        }

        var executor = new HeadlessExecutor();
        var result = executor.execute(new ValidateProjectCommand(root, resolved));

        for (FileValidation fv : result.findings()) {
            System.out.println(fv.file() + ":");
            for (ValidationError e : fv.errors()) {
                System.out.printf("  %d:%d  %s: %s%n",
                        e.line(), e.column(), e.severity(), e.message());
            }
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more failing file(s).");
        }
        System.out.printf("%d file(s): %d valid, %d invalid.%n",
                result.total(), result.passed(), result.failed());
        return result.isClean() ? 0 : 1;
    }
}
