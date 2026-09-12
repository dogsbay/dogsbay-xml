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
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.QueryCommand;

@Command(name = "query", description = "Run XPath query across one or more XML files")
class QueryCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "File path or glob pattern (e.g., 'docs/**/*.xml')")
    private String filePattern;

    @Parameters(index = "1", description = "XPath expression")
    private String xpath;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var results = executor.execute(new QueryCommand(filePattern, xpath));
        OutputFormatter.printQuery(results, System.out);
        return results.isEmpty() ? 1 : 0;
    }
}
