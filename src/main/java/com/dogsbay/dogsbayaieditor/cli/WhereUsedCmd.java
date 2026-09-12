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
import com.dogsbay.dogsbayaieditor.commands.WhereUsedCommand;
import com.dogsbay.dogsbayaieditor.commands.results.ReferenceInfo;

@Command(name = "where-used",
        description = "Find every reference to a file across a project")
class WhereUsedCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The target file")
    private String file;

    @Option(names = {"-r", "--root"}, required = true,
            description = "Project root directory to scan")
    private String root;

    @Option(names = {"-m", "--map"},
            description = "Root map — includes indirect references via keys")
    private String rootMap;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var results = executor.execute(new WhereUsedCommand(file, root, rootMap));
        if (results.isEmpty()) {
            System.out.println("No references found.");
            return 1;
        }
        String currentCategory = null;
        for (ReferenceInfo ref : results) {
            if (!ref.category().equals(currentCategory)) {
                currentCategory = ref.category();
                System.out.println(currentCategory + ":");
            }
            String via = ref.viaKey() != null ? "  (via key '" + ref.viaKey() + "')" : "";
            System.out.printf("  %s:%d  <%s %s=\"%s\">%s%n",
                    ref.source(), ref.line(), ref.element(),
                    ref.attribute(), ref.value(), via);
        }
        System.out.println(results.size() + " reference(s).");
        return 0;
    }
}
