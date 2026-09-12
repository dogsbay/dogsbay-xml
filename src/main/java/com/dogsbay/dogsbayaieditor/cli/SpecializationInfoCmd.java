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
import com.dogsbay.dogsbayaieditor.commands.SpecializationInfoCommand;
import com.dogsbay.dogsbayaieditor.commands.results.SpecializationInfoResult;

@Command(name = "specialization-info",
        description = "Report a DITA file's specialization: DOCTYPE, root element, "
                + "@class generalization chain, and @domains.")
class SpecializationInfoCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The DITA file to inspect")
    private String file;

    @Override
    public Integer call() throws Exception {
        SpecializationInfoResult r = new HeadlessExecutor()
                .execute(new SpecializationInfoCommand(file));
        System.out.println("Root element : " + r.root());
        System.out.println("Specialized  : " + (r.specialized() ? "yes" : "no (base type)"));
        if (r.publicId() != null) {
            System.out.println("Public id    : " + r.publicId());
        }
        if (r.systemId() != null) {
            System.out.println("System id    : " + r.systemId());
        }
        if (!r.classChain().isEmpty()) {
            System.out.println("Class chain  : " + String.join(" → ", r.classChain()));
        }
        if (!r.domains().isEmpty()) {
            System.out.println("Domains      : " + String.join(", ", r.domains()));
        }
        return 0;
    }
}
