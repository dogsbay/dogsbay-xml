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

import com.dogsbay.dogsbayaieditor.commands.ExportMetadataSchematronCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;

@Command(name = "metadata-export-schematron",
        description = "Compile the required-metadata policy to ISO Schematron "
                + "(portable validation for CI / DITA-OT / oXygen).")
class MetadataExportSchematronCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "Project root directory")
    private String root;

    @Option(names = {"-p", "--policy"}, description = "Policy .xml file (overrides config)")
    private String policy;

    @Option(names = {"-o", "--output"}, description = "Write the .sch here (else print)")
    private String output;

    @Override
    public Integer call() throws Exception {
        var executor = new HeadlessExecutor();
        String sch = executor.execute(new ExportMetadataSchematronCommand(root, policy, output));
        if (output != null) {
            System.out.println("Wrote " + output);
        } else {
            System.out.print(sch);
        }
        return 0;
    }
}
