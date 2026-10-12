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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.TransformCommand;

@Command(name = "transform", description = "Apply XSLT transformation to an XML document")
class TransformCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Input XML file")
    private Path input;

    @Parameters(index = "1", description = "XSLT stylesheet")
    private Path xslt;

    @Option(names = {"-o", "--output"}, description = "Output file (prints to stdout if omitted)")
    private Path output;

    @Option(names = {"-p", "--param"}, description = "XSLT parameter (key=value, repeatable)")
    private List<String> params;

    @Override
    public Integer call() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        if (params != null) {
            for (String p : params) {
                int eq = p.indexOf('=');
                if (eq > 0) {
                    parameters.put(p.substring(0, eq), p.substring(eq + 1));
                }
            }
        }

        var executor = new HeadlessExecutor();
        var result = executor.execute(new TransformCommand(input, xslt, output, parameters));
        OutputFormatter.printTransform(result, System.out);
        return 0;
    }
}
