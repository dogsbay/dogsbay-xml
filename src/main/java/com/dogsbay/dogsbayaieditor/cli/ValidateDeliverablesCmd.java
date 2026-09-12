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
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableValidation;
import com.dogsbay.dogsbayaieditor.commands.results.FileValidation;
import com.dogsbay.dogsbayaieditor.commands.results.ValidationError;

@Command(name = "validate-deliverables",
        description = "Validate every deliverable of a project (each deliverable's map "
                + "publication set). Exits 1 when any deliverable has an invalid file.")
class ValidateDeliverablesCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var deliverables = executor.execute(new ValidateDeliverablesCommand(root));

        if (deliverables.isEmpty()) {
            System.out.println("No deliverables found (no project.{xml,json,yaml} and no "
                    + "default root map).");
            return 0;
        }

        boolean clean = true;
        for (DeliverableValidation d : deliverables) {
            var v = d.validation();
            System.out.printf("%s (%s): %d file(s), %d valid, %d invalid%n",
                    d.name(), d.map(), v.total(), v.passed(), v.failed());
            for (FileValidation fv : v.findings()) {
                System.out.println("  " + fv.file() + ":");
                for (ValidationError e : fv.errors()) {
                    System.out.printf("    %d:%d  %s: %s%n",
                            e.line(), e.column(), e.severity(), e.message());
                }
            }
            if (!v.isClean()) {
                clean = false;
            }
        }
        return clean ? 0 : 1;
    }
}
