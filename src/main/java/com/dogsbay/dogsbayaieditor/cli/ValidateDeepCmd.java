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
import com.dogsbay.dogsbayaieditor.commands.ValidateDeepCommand;
import com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation;
import com.dogsbay.xml.dita.DitaOtMessage;

@Command(name = "validate-ot",
        description = "Deep-validate deliverables by running DITA-OT preprocessing "
                + "(applies each deliverable's DITAVAL). Catches keyref/conref "
                + "resolution and other preprocessing errors the static validate "
                + "cannot. Exits 1 when any deliverable has errors. Requires DITA-OT.")
class ValidateDeepCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Parameters(index = "1", arity = "0..1",
            description = "Deliverable name (all deliverables if omitted)")
    private String deliverable;

    @Option(names = "--dita-ot", description = "DITA-OT home directory (overrides the "
            + "project-configured engine path)")
    private String ditaOtHome;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var results = executor.execute(
                new ValidateDeepCommand(root, deliverable, null, ditaOtHome));

        if (results.isEmpty()) {
            System.out.println("No deliverables found (no project.{xml,json,yaml} and no "
                    + "default root map).");
            return 0;
        }

        boolean clean = true;
        for (DitaOtValidation d : results) {
            long errors = d.messages().stream().filter(DitaOtMessage::isError).count();
            long warnings = d.messages().size() - errors;
            String filter = d.ditaval() != null ? " [" + d.ditaval() + "]" : "";
            System.out.printf("%s (%s)%s: %s — %d error(s), %d warning(s)%n",
                    d.name(), d.map(), filter, d.success() ? "OK" : "FAILED", errors, warnings);
            for (DitaOtMessage m : d.messages()) {
                String loc = m.file() != null
                        ? m.file() + (m.line() > 0 ? ":" + m.line() : "")
                        : "";
                String code = m.code() != null ? "[" + m.code() + "] " : "";
                System.out.printf("  %-5s %s %s%s%n", m.severity(),
                        loc.isEmpty() ? "" : loc, code, m.message());
            }
            if (!d.success()) {
                clean = false;
            }
        }
        return clean ? 0 : 1;
    }
}
