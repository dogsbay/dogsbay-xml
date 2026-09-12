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
import com.dogsbay.dogsbayaieditor.commands.ListBranchesCommand;
import com.dogsbay.dogsbayaieditor.commands.results.BranchInfo;

@Command(name = "list-branches",
        description = "Enumerate a map's DITA 1.3 branch-filter variants (ditavalref).")
class ListBranchesCmd implements Callable<Integer> {

    @Parameters(index = "0", description = "The .ditamap")
    private String map;

    @Override
    public Integer call() throws Exception {
        var branches = new HeadlessExecutor().execute(new ListBranchesCommand(map));
        if (branches.isEmpty()) {
            System.out.println("No branch-filter variants (ditavalref) in " + map);
            return 0;
        }
        for (BranchInfo b : branches) {
            System.out.printf("%-20s filters %-24s ditaval=%s%s%n", b.label(),
                    b.appliesTo(), b.ditaval(),
                    b.generatedKeyscope().isEmpty() ? ""
                            : "  keyscope=" + b.generatedKeyscope());
        }
        System.out.println(branches.size() + " branch variant(s).");
        return 0;
    }
}
