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
import com.dogsbay.dogsbayaieditor.commands.ValidateDeliverablesCommand;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport;

@Command(name = "validate-deliverables",
        description = "Validate every deliverable of a project (each deliverable's map "
                + "publication set). Exits 1 when any deliverable has an invalid file.")
class ValidateDeliverablesCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        DeliverablesReport report = executor.execute(new ValidateDeliverablesCommand(root));

        if (report.deliverables().isEmpty()) {
            System.out.println("No deliverables found (no project.{xml,json,yaml} and no "
                    + "default root map).");
            return 0;
        }

        for (DeliverablesReport.Deliverable d : report.deliverables()) {
            System.out.printf("%s (%s): %d file(s), %d valid, %d invalid%n",
                    d.name(), d.map(), d.total(), d.passed(), d.failed());
        }

        // Each invalid file once, with the deliverables it breaks.
        String current = null;
        for (DeliverablesReport.Finding f : report.findings()) {
            if (!f.file().equals(current)) {
                current = f.file();
                System.out.println("  " + f.file() + "  [" + String.join(", ", f.deliverables()) + "]:");
            }
            System.out.printf("    %d:%d  %s: %s%n", f.line(), f.column(), f.severity(), f.message());
        }
        if (report.truncated() > 0) {
            System.out.println("  … and " + report.truncated() + " more finding(s).");
        }
        return report.isClean() ? 0 : 1;
    }
}
