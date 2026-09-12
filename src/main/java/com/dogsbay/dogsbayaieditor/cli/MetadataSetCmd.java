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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.MetadataSetCommand;
import com.dogsbay.dogsbayaieditor.commands.MetadataSetSpec;
import com.dogsbay.dogsbayaieditor.commands.results.MetadataChange;

@Command(name = "metadata-set",
        description = "Bulk-apply metadata changes across a scope, field-preserving. "
                + "Repeat --set/--fill/--append/--remove for several fields.")
class MetadataSetCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-m", "--map"}, description = "Root map — apply to its publication set")
    private String map;

    @Option(names = {"-s", "--scope"}, description = "Scope: 'map:<p>', 'glob:<p>', or 'root'")
    private String scope;

    @Option(names = "--set", description = "field=value (overwrite)")
    private List<String> set = new ArrayList<>();

    @Option(names = "--fill", description = "field=value (only if absent)")
    private List<String> fill = new ArrayList<>();

    @Option(names = "--append", description = "field=value (add to a list field)")
    private List<String> append = new ArrayList<>();

    @Option(names = "--remove", description = "field[=value] (remove)")
    private List<String> remove = new ArrayList<>();

    @Option(names = "--dry-run", description = "Report changes without writing")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        List<MetadataSetSpec> specs = new ArrayList<>();
        addSpecs(specs, set, "set");
        addSpecs(specs, fill, "fill");
        addSpecs(specs, append, "append");
        addSpecs(specs, remove, "remove");
        if (specs.isEmpty()) {
            System.err.println("Specify at least one --set/--fill/--append/--remove.");
            return 2;
        }
        String resolved = scope;
        if (resolved == null && map != null) {
            resolved = "map:" + map;
        }

        var result = new HeadlessExecutor()
                .execute(new MetadataSetCommand(root, resolved, specs, dryRun));
        for (MetadataChange ch : result.findings()) {
            System.out.printf("%s — %s %s%s%n", ch.file(), ch.mode(), ch.field(),
                    ch.value() != null ? "=" + ch.value() : "");
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more change(s).");
        }
        System.out.printf("%s%d file(s): %d changed, %d unchanged.%n",
                dryRun ? "[dry-run] " : "", result.total(), result.failed(), result.passed());
        return 0;
    }

    private static void addSpecs(List<MetadataSetSpec> specs, List<String> pairs, String mode) {
        for (String p : pairs) {
            int eq = p.indexOf('=');
            String field = eq >= 0 ? p.substring(0, eq) : p;
            String value = eq >= 0 ? p.substring(eq + 1) : null;
            specs.add(new MetadataSetSpec(field, value, mode));
        }
    }
}
