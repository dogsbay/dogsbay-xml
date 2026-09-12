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
import com.dogsbay.dogsbayaieditor.commands.SchematronCommand;
import com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding;

@Command(name = "schematron",
        description = "Apply a Schematron schema (.sch) to a single document. "
                + "Use schematron-project for a whole scope.")
class SchematronCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The document to check")
    private String file;

    @Parameters(index = "1", description = "Schematron schema file (.sch)")
    private String schema;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var result = executor.execute(new SchematronCommand(file, schema));

        for (SchematronFinding f : result.findings()) {
            String where = f.line() > 0
                    ? f.file() + ":" + f.line()
                    : f.file() + (f.location() != null ? " (" + f.location() + ")" : "");
            System.out.printf("%s — %s%n", where, f.message());
        }
        if (result.truncated() > 0) {
            System.out.println("… and " + result.truncated() + " more assertion(s).");
        }
        // Count the reported total, not the capped list: printing "… and 50 more"
        // directly above "200 failed assertion(s)" contradicts itself. "rule
        // match(es)" rather than "assertions" because a document that fails to
        // parse also arrives here as a finding.
        System.out.println(result.isClean()
                ? "No rule matches."
                : (result.findings().size() + result.truncated()) + " rule match(es).");
        return result.isClean() ? 0 : 1;
    }
}
