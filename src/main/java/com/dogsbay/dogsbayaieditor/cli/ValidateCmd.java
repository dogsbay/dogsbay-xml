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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ValidateCommand;

@Command(name = "validate", description = "Validate XML against schema (XSD, DTD, RelaxNG) with catalog support")
class ValidateCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "XML file to validate")
    private Path file;

    @Parameters(index = "1", arity = "0..1", description = "Schema file (XSD, DTD, or RNG). Auto-detected by extension.")
    private Path schema;

    @Option(names = "--catalog", description = "XML catalog file for entity resolution (repeatable)")
    private List<Path> catalogs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var result = executor.execute(new ValidateCommand(file, schema, catalogs));
        OutputFormatter.printValidation(result, System.out);
        return result.valid() ? 0 : 1;
    }
}
