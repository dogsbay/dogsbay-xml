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

import com.dogsbay.dogsbayaieditor.commands.BuildDeliverablesCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild;
import com.dogsbay.xml.dita.DitaOtMessage;

@Command(name = "build",
        description = "Build a DITA project's deliverables with DITA-OT (each with its "
                + "transtype, DITAVAL, params, and output dir). Builds one deliverable "
                + "or all. Exits 1 when any build has errors. Requires DITA-OT.")
class BuildCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Parameters(index = "1", arity = "0..1",
            description = "Deliverable name (all deliverables if omitted)")
    private String deliverable;

    @Option(names = {"-o", "--output"}, description = "Base output directory "
            + "(each deliverable built under a subdir); overrides the project's output")
    private String output;

    @Option(names = "--dita-ot", description = "DITA-OT home directory (overrides the "
            + "project-configured engine path)")
    private String ditaOtHome;

    @Option(names = "--keep-temp", description = "Keep DITA-OT's temporary files in "
            + "<root>/.dogsbay/temp/<deliverable> (same as the clean.temp=no param)")
    private boolean keepTemp;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        var results = executor.execute(new BuildDeliverablesCommand(root, output, deliverable, ditaOtHome,
                null, keepTemp ? Boolean.TRUE : null));

        if (results.isEmpty()) {
            System.out.println("No deliverables found (no project.{xml,json,yaml} and no "
                    + "default root map).");
            return 0;
        }

        boolean clean = true;
        for (DeliverableBuild b : results) {
            long errors = b.messages().stream().filter(DitaOtMessage::isError).count();
            System.out.printf("%s (%s) → %s: %s%s%n", b.name(), b.transtype(), b.outputDir(),
                    b.success() ? "OK" : "FAILED",
                    errors > 0 ? " — " + errors + " error(s)" : "");
            if (b.tempDir() != null) {
                System.out.printf("    temporary files kept in %s%n", b.tempDir());
            }
            for (DitaOtMessage m : b.messages()) {
                if (m.isError()) {
                    String loc = m.file() != null
                            ? m.file() + (m.line() > 0 ? ":" + m.line() : "") + " " : "";
                    System.out.printf("    %s%s%n", loc, m.message());
                }
            }
            if (!b.success()) {
                clean = false;
            }
        }
        return clean ? 0 : 1;
    }
}
