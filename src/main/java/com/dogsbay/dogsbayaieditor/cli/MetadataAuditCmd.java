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
import com.dogsbay.dogsbayaieditor.commands.MetadataAuditCommand;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding;

@Command(name = "metadata-audit",
        description = "Audit topics/maps against the required-metadata policy "
                + "(.dogsbay/config.xml or --policy). Exits 1 on any error-level violation.")
class MetadataAuditCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-m", "--map"}, description = "Root map — audit its publication set")
    private String map;

    @Option(names = {"-s", "--scope"},
            description = "Explicit scope: 'map:<path>', 'glob:<pattern>', or 'root' "
                    + "(overrides --map; default 'root')")
    private String scope;

    @Option(names = {"-p", "--policy"},
            description = "Policy .xml file (overrides the project config)")
    private String policy;

    @Override
    public Integer call() throws Exception {
        String resolved = scope;
        if (resolved == null && map != null) {
            resolved = "map:" + map;
        }

        var executor = new HeadlessExecutor();
        var result = executor.execute(new MetadataAuditCommand(root, resolved, policy));

        boolean anyError = false;
        for (MetadataFinding f : result.findings()) {
            String where = f.line() > 0 ? f.file() + ":" + f.line() : f.file();
            System.out.printf("%s — [%s] %s%n", where, f.severity(), f.message());
            anyError |= "error".equals(f.severity());
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more.");
        }
        System.out.printf("%d file(s): %d pass, %d with metadata issues.%n",
                result.total(), result.passed(), result.failed());
        return anyError ? 1 : 0;
    }
}
