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
import com.dogsbay.dogsbayaieditor.commands.ValidateConditionsCommand;
import com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation;

@Command(name = "validate-conditions",
        description = "Scan for profiling-attribute values (platform, audience, …) that "
                + "violate a subjectScheme's controlled values. Exits 1 when any violation is found.")
class ValidateConditionsCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-S", "--subject-scheme"},
            description = "Subject scheme (.ditamap); if omitted, discovered from --map's closure")
    private String subjectScheme;

    @Option(names = {"-m", "--map"},
            description = "Root map — scan its publication set (and discover its scheme)")
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
        var result = executor.execute(new ValidateConditionsCommand(root, resolved, subjectScheme));

        for (ConditionViolation v : result.findings()) {
            String where = v.line() > 0 ? v.file() + ":" + v.line() : v.file();
            System.out.printf("%s — @%s=\"%s\" — %s%n", where, v.attribute(), v.value(), v.message());
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more violation(s).");
        }
        System.out.printf("%d file(s): %d pass, %d with violations.%n",
                result.total(), result.passed(), result.failed());
        return result.isClean() ? 0 : 1;
    }
}
